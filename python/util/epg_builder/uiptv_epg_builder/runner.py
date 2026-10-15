"""Main runner for UIPTV EPG Builder."""

from __future__ import annotations

import json
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from datetime import datetime, timezone

from .config import Config, load_config, create_default_config
from .inventory import InventoryExporter, InventoryExport
from .registry import SourceRegistry, SourceCollector, FetchResult
from .xmltv import parse_xmltv, XmltvGuide
from .matching import ChannelMatcher, ChannelMatch
from .merge import ScheduleMerger, MergedSchedule
from .bundle import EpgBundle, BundleBuilder


@dataclass
class RunResult:
    """Result of a builder run."""
    success: bool
    bundle: EpgBundle | None = None
    error: str = ""
    duration_seconds: float = 0.0
    sources_fetched: int = 0
    sources_failed: int = 0
    channels_matched: int = 0
    channels_unmatched: int = 0
    programmes_imported: int = 0


class EpgBuilderRunner:
    """Orchestrates the EPG building process."""

    def __init__(self, config: Config):
        self.config = config
        self.working_dir = Path(config.working_dir or Path.home() / ".uiptv" / "epg-builder").expanduser()
        self.working_dir.mkdir(parents=True, exist_ok=True)

        # Initialize components
        self.registry = SourceRegistry(config.collector, str(self.working_dir))
        self.collector = SourceCollector(self.registry, config.collector)
        self.matcher = ChannelMatcher(
            min_confidence=config.matching.min_confidence,
            margin_threshold=config.matching.margin_threshold,
            enable_fuzzy=config.matching.enable_fuzzy,
            quality_aliases=config.matching.quality_aliases,
            exact_id_first=config.matching.exact_id_first,
            canonical_alias_first=config.matching.canonical_alias_first,
        )
        from .bridge import bridge_from_config
        self.merger = ScheduleMerger(bridge=bridge_from_config(config.bridge))
        self.exporter = InventoryExporter(config)

        # Register sources from config
        for source in config.sources:
            self.registry.register_source(source)

    def run(self, dry_run: bool | None = None, force_refresh: bool | None = None) -> RunResult:
        from .locking import run_lock
        try:
            with run_lock(Path(self.config.lock.lock_file).expanduser() if self.config.lock.lock_file else self.working_dir / "builder.lock"):
                return self._run(dry_run, force_refresh)
        except OSError as error:
            return RunResult(success=False, error=f"Cannot acquire builder lock: {error}")
        finally:
            self.collector.close()

    def _run(self, dry_run: bool | None = None, force_refresh: bool | None = None) -> RunResult:
        """Run the complete EPG building process."""
        start_time = time.time()
        dry_run = dry_run if dry_run is not None else self.config.dry_run
        force_refresh = force_refresh if force_refresh is not None else self.config.force_refresh

        try:
            # Step 1: Export inventory from Java bridge
            print("Exporting channel inventory from UIPTV database...")
            inventory = self.exporter.export_inventory()
            print(f"  Found {len(inventory.channels)} channels across {len(inventory.accounts)} accounts")

            # Step 2: Collect sources
            print("Fetching EPG sources...")
            guides, fetch_results = self._collect_sources(force_refresh)
            print(f"  Fetched {len(guides)} sources successfully")

            # Step 3: Match channels
            print("Matching channels to guide data...")
            matches = self._match_channels(inventory, guides)
            matched_count = sum(1 for m in matches if m.guide_channel_id)
            print(f"  Matched {matched_count}/{len(matches)} channels")

            # Step 4: Merge schedules
            print("Merging schedules...")
            merged = self._merge_schedules(matches, guides)
            total_programmes = sum(len(m.programmes) for m in merged)
            print(f"  Merged {total_programmes} programmes")

            # Step 5: Build bundle
            print("Building import bundle...")
            bundle = self._build_bundle(inventory, matches, merged, guides, fetch_results)

            # Step 6: Validate bundle
            validation = self._validate_bundle(bundle, inventory)
            from .bridge import bridge_from_config
            bridge_from_config(self.config.bridge).validate_bundle(bundle)
            print(f"  Validation: {validation}")
            bundle.save(self.working_dir / "last-bundle.json")
            (self.working_dir / "last-matches.json").write_text(json.dumps([m.to_dict() for m in matches], indent=2))

            # Step 7: Import (if not dry run)
            if not dry_run:
                print("Importing bundle to UIPTV database...")
                self._import_bundle(bundle)
                print("  Import complete")
            else:
                print("  Dry run - skipping import")

            duration = time.time() - start_time
            return RunResult(
                success=True,
                bundle=bundle,
                duration_seconds=duration,
                sources_fetched=sum(1 for r in fetch_results if r.success and not r.from_cache),
                sources_failed=sum(1 for r in fetch_results if not r.success),
                channels_matched=matched_count,
                channels_unmatched=len(matches) - matched_count,
                programmes_imported=0 if dry_run else total_programmes,
            )

        except Exception as e:
            duration = time.time() - start_time
            return RunResult(
                success=False,
                error=str(e),
                duration_seconds=duration,
            )

    def _collect_sources(self, force: bool) -> tuple[dict[str, XmltvGuide], list[FetchResult]]:
        """Collect all enabled sources."""
        sources = self.registry.get_enabled_sources(
            include=self.config.include_sources,
            exclude=self.config.exclude_sources,
            region_filter=self.config.region_filter,
            language_filter=self.config.language_filter,
        )

        guides = {}
        results = []

        for source in sources:
            print(f"  Fetching {source.name} ({source.id})...")
            result = self.collector.fetch_source(source, force)
            results.append(result)

            if result.success and result.content:
                try:
                    from .bridge import bridge_from_config
                    guide = parse_xmltv(result.content, bridge=bridge_from_config(self.config.bridge))
                    guides[source.id] = guide
                    # Update health with counts
                    health = self.registry.get_health(source.id)
                    health.programme_count = sum(len(p) for p in guide.programmes.values())
                    health.channel_count = len(guide.programmes)
                    self.registry.update_health(health)
                    print(f"    OK: {health.programme_count} programmes, {health.channel_count} channels")
                except Exception as e:
                    print(f"    Parse error: {e}")
                    result.success = False
                    result.error = f"Parse error: {e}"
            elif result.from_cache:
                print(f"    Skipped (fresh)")
            else:
                print(f"    Failed: {result.error}")

        return guides, results

    def _match_channels(
        self,
        inventory: InventoryExport,
        guides: dict[str, XmltvGuide],
    ) -> list[ChannelMatch]:
        """Match channels to guide data."""
        # Build source priorities
        source_priorities = {}
        for source in self.config.sources:
            source_priorities[source.id] = source.priority

        return self.matcher.match_channels(inventory.channels, guides, source_priorities,
            {s.id: {"countries": s.countries, "languages": s.languages} for s in self.config.sources})

    def _merge_schedules(
        self,
        matches: list[ChannelMatch],
        guides: dict[str, XmltvGuide],
    ) -> list[MergedSchedule]:
        """Merge schedules from multiple sources."""
        source_priorities = {}
        for source in self.config.sources:
            source_priorities[source.id] = source.priority

        return self.merger.merge_schedules(matches, guides, source_priorities)

    def _build_bundle(
        self,
        inventory: InventoryExport,
        matches: list[ChannelMatch],
        merged: list[MergedSchedule],
        guides: dict[str, XmltvGuide],
        fetch_results: list[FetchResult],
    ) -> EpgBundle:
        """Build the import bundle."""
        # Build source configs for bundle
        source_configs = {}
        for source in self.config.sources:
            source_configs[source.id] = {
                "name": source.name,
                "type": source.type,
                "url": source.url,
                "priority": source.priority,
            }

        # Update with fetch results
        for result in fetch_results:
            if result.success and result.source_id in source_configs:
                health = self.registry.get_health(result.source_id)
                source_configs[result.source_id].update({
                    "fetched_at": datetime.now(timezone.utc).isoformat(),
                    "size_bytes": health.size_bytes,
                    "content_hash": health.content_hash,
                })

        builder = BundleBuilder(inventory)
        return builder.build_bundle(matches, merged, guides, source_configs)

    def _validate_bundle(self, bundle: EpgBundle, inventory: InventoryExport) -> dict[str, Any]:
        """Validate the bundle before import."""
        validation = {
            "schema_version": bundle.schema_version,
            "database_identity_match": bundle.database_identity == inventory.database_identity,
            "config_signature_match": bundle.config_signature == inventory.config_signature,
            "inventory_revision_match": bundle.inventory_revision == inventory.revision,
            "total_mappings": len(bundle.mappings),
            "total_programmes": len(bundle.programmes),
            "unique_sources": len(set(m.source_id for m in bundle.mappings)),
            "warnings": [],
        }

        # Check for signature mismatches
        by_identity = {(c.account_id, c.provider_channel_id): c for c in inventory.channels}
        sig_mismatches = 0
        for mapping in bundle.mappings:
            channel = by_identity.get((mapping.account_id, mapping.provider_channel_id))
            if channel and channel.signature != mapping.signature:
                sig_mismatches += 1

        if sig_mismatches > 0:
            validation["warnings"].append(f"{sig_mismatches} signature mismatches detected")

        # Check for duplicate programmes
        prog_keys = set()
        duplicates = 0
        for p in bundle.programmes:
            key = (p.source_id, p.guide_channel_id, p.start)
            if key in prog_keys:
                duplicates += 1
            prog_keys.add(key)

        if duplicates > 0:
            validation["warnings"].append(f"{duplicates} duplicate programme entries")

        return validation

    def _import_bundle(self, bundle: EpgBundle) -> None:
        from .bridge import bridge_from_config
        bridge_from_config(self.config.bridge).import_bundle(bundle)


def run_builder(config_path: str | None = None, **overrides) -> RunResult:
    """Convenience function to run the builder."""
    if config_path:
        config = load_config(config_path)
    else:
        # Create default config in working directory
        working_dir = Path.home() / ".uiptv" / "epg-builder"
        config_path = working_dir / "config.toml"
        if not config_path.exists():
            config = create_default_config(config_path)
        else:
            config = load_config(config_path)

    # Apply overrides
    for key, value in overrides.items():
        if value is not None and hasattr(config, key):
            setattr(config, key, value)

    runner = EpgBuilderRunner(config)
    return runner.run()


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="UIPTV EPG Builder")
    parser.add_argument("--config", help="Path to config file")
    parser.add_argument("--dry-run", action="store_true", help="Run without importing")
    parser.add_argument("--apply", action="store_true", help="Apply changes (opposite of dry-run)")
    parser.add_argument("--force", action="store_true", help="Force refresh all sources")
    parser.add_argument("--json", action="store_true", help="Output result as JSON")

    args = parser.parse_args()

    dry_run = not args.apply if args.apply else True
    if args.dry_run:
        dry_run = True

    result = run_builder(
        config_path=args.config,
        dry_run=dry_run,
        force_refresh=args.force,
    )

    if args.json:
        print(json.dumps({
            "success": result.success,
            "error": result.error,
            "duration_seconds": result.duration_seconds,
            "sources_fetched": result.sources_fetched,
            "sources_failed": result.sources_failed,
            "channels_matched": result.channels_matched,
            "channels_unmatched": result.channels_unmatched,
            "programmes_imported": result.programmes_imported,
        }))
    else:
        if result.success:
            print(f"\nSuccess! Duration: {result.duration_seconds:.1f}s")
            print(f"  Sources fetched: {result.sources_fetched}")
            print(f"  Sources failed: {result.sources_failed}")
            print(f"  Channels matched: {result.channels_matched}")
            print(f"  Channels unmatched: {result.channels_unmatched}")
            print(f"  Programmes imported: {result.programmes_imported}")
        else:
            print(f"\nFailed: {result.error}")
            sys.exit(1)
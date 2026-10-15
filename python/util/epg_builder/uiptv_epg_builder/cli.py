"""Command-line interface for UIPTV EPG Builder."""

from __future__ import annotations

import json as json_module
import sys
from pathlib import Path

import click

from .config import Config, load_config, create_default_config
from .runner import run_builder, RunResult
from .inventory import InventoryExporter
from .registry import SourceRegistry, SourceCollector, load_source_registry, save_source_registry, SourceConfig
from .bundle import EpgBundle


@click.group()
@click.version_option(version="0.1.0", prog_name="uiptv-epg")
def cli():
    """UIPTV EPG Builder - External headless utility for discovering and retrieving programme guides."""
    pass


@cli.command()
@click.option("--config", "-c", type=click.Path(), help="Path to configuration file")
@click.option("--output", "-o", type=click.Path(), help="Output path for default config")
def init(config: str | None, output: str | None):
    """Create a default configuration file."""
    if output:
        path = Path(output)
    elif config:
        path = Path(config)
    else:
        path = Path.home() / ".uiptv" / "epg-builder" / "config.toml"

    if path.exists():
        raise click.ClickException("Configuration already exists; choose a new --output path")
    config_obj = create_default_config(path)
    click.echo(f"Created default configuration at {path}")
    click.echo(f"Working directory: {config_obj.working_dir}")
    click.echo(f"Registered {len(config_obj.sources)} example sources")


@cli.command()
@click.option("--config", "-c", type=click.Path(exists=True), help="Path to configuration file")
@click.option("--dry-run/--apply", default=True, help="Dry run (default) or apply changes")
@click.option("--force", "-f", is_flag=True, help="Force refresh all sources")
@click.option("--json", is_flag=True, help="Output result as JSON")
@click.option("--include", multiple=True, help="Include only these source IDs")
@click.option("--exclude", multiple=True, help="Exclude these source IDs")
@click.option("--region", multiple=True, help="Filter by region/country")
@click.option("--language", multiple=True, help="Filter by language")
def build(config: str | None, dry_run: bool, force: bool, json: bool,
          include: tuple, exclude: tuple, region: tuple, language: tuple):
    """Build EPG data from configured sources."""
    import contextlib
    with contextlib.redirect_stdout(sys.stderr):
        result = run_builder(
            config_path=config, dry_run=dry_run, force_refresh=force,
            include_sources=list(include) if include else None,
            exclude_sources=list(exclude) if exclude else None,
            region_filter=list(region) if region else None,
            language_filter=list(language) if language else None,
        )

    if json:
        click.echo(json_module.dumps({
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
            click.echo(f"\nSuccess! Duration: {result.duration_seconds:.1f}s")
            click.echo(f"  Sources fetched: {result.sources_fetched}")
            click.echo(f"  Sources failed: {result.sources_failed}")
            click.echo(f"  Channels matched: {result.channels_matched}")
            click.echo(f"  Channels unmatched: {result.channels_unmatched}")
            click.echo(f"  Programmes imported: {result.programmes_imported}")
            if dry_run:
                click.echo("  (Dry run - no changes applied)")
        else:
            click.echo(f"\nFailed: {result.error}", err=True)
            sys.exit(1)

    if not result.success:
        raise click.exceptions.Exit(1)


@cli.command()
@click.option("--config", "-c", type=click.Path(exists=True), help="Path to configuration file")
@click.option("--json", is_flag=True, help="Output as JSON")
def scan(config: str | None, json: bool):
    """Scan and list channels that would be processed."""
    if config:
        cfg = load_config(config)
    else:
        cfg = load_config(Path.home() / ".uiptv" / "epg-builder" / "config.toml")

    exporter = InventoryExporter(cfg)
    try:
        inventory = exporter.export_inventory()
    except Exception as e:
        click.echo(f"Error: {e}", err=True)
        sys.exit(1)

    if json:
        click.echo(json_module.dumps({
            "database_path": inventory.database_path,
            "database_identity": inventory.database_identity,
            "generation": inventory.generation,
            "revision": inventory.revision,
            "accounts": len(inventory.accounts),
            "channels": len(inventory.channels),
        }))
    else:
        click.echo(f"Database: {inventory.database_path}")
        click.echo(f"Identity: {inventory.database_identity}")
        click.echo(f"Generation: {inventory.generation}")
        click.echo(f"Revision: {inventory.revision}")
        click.echo(f"Accounts: {len(inventory.accounts)}")
        click.echo(f"Channels: {len(inventory.channels)}")

        # Group by account
        from collections import defaultdict
        by_account = defaultdict(list)
        for ch in inventory.channels:
            by_account[ch.account_id].append(ch)

        for acc_id, channels in by_account.items():
            acc_name = next((a.name for a in inventory.accounts if a.db_id == acc_id), acc_id)
            click.echo(f"\n  {acc_name} ({acc_id}): {len(channels)} channels")
            for ch in channels[:10]:  # Show first 10
                click.echo(f"    {ch.name} [{ch.provider_channel_id}]")
            if len(channels) > 10:
                click.echo(f"    ... and {len(channels) - 10} more")


@cli.group()
def sources():
    """Manage EPG sources."""
    pass


@sources.command("list")
@click.option("--config", "-c", type=click.Path(exists=True), help="Path to configuration file")
@click.option("--json", is_flag=True, help="Output as JSON")
def sources_list(config: str | None, json: bool):
    """List configured sources."""
    if config:
        cfg = load_config(config)
    else:
        cfg = load_config(Path.home() / ".uiptv" / "epg-builder" / "config.toml")

    if json:
        click.echo(json_module.dumps([{
            "id": s.id,
            "name": s.name,
            "type": s.type,
            "url": s.url,
            "enabled": s.enabled,
            "priority": s.priority,
            "refresh_hours": s.refresh_hours,
        } for s in cfg.sources]))
    else:
        for s in cfg.sources:
            status = "enabled" if s.enabled else "disabled"
            click.echo(f"  {s.id} ({status}): {s.name} [{s.type}] priority={s.priority} refresh={s.refresh_hours}h")


@sources.command("check")
@click.option("--config", "-c", type=click.Path(exists=True), help="Path to configuration file")
@click.option("--json", is_flag=True, help="Output as JSON")
def sources_check(config: str | None, json: bool):
    """Check source health and freshness."""
    if config:
        cfg = load_config(config)
    else:
        cfg = load_config(Path.home() / ".uiptv" / "epg-builder" / "config.toml")

    registry = SourceRegistry(cfg.collector, cfg.working_dir)
    for s in cfg.sources:
        registry.register_source(s)

    collector = SourceCollector(registry, cfg.collector)

    results = []
    for source in cfg.sources:
        if not source.enabled:
            continue
        result = collector.fetch_source(source, force=False)
        health = registry.get_health(source.id)
        results.append({
            "source_id": source.id,
            "name": source.name,
            "status": health.status.value,
            "last_success": health.last_success,
            "last_failure": health.last_failure,
            "consecutive_failures": health.consecutive_failures,
            "error": health.error_message,
            "programme_count": health.programme_count,
            "channel_count": health.channel_count,
            "due": registry.is_source_due(source),
        })

    collector.close()
    if json:
        click.echo(json_module.dumps(results))
    else:
        for r in results:
            status = r["status"]
            due = " (DUE)" if r["due"] else ""
            click.echo(f"  {r['source_id']}: {status}{due}")
            if r["last_success"]:
                from datetime import datetime
                dt = datetime.fromtimestamp(r["last_success"])
                click.echo(f"    Last success: {dt.strftime('%Y-%m-%d %H:%M:%S')}")
            if r["error"]:
                click.echo(f"    Error: {r['error']}")


@cli.command()
@click.option("--config", "-c", type=click.Path(exists=True), help="Path to configuration file")
@click.option("--bundle", "-b", type=click.Path(exists=True), help="Path to bundle file")
@click.option("--json", is_flag=True, help="Output as JSON")
def status(config: str | None, bundle: str | None, json: bool):
    """Show current EPG cache status."""
    # This would use the Java bridge to get status
    # For now, show bundle info if provided
    if bundle:
        b = EpgBundle.load(bundle)
        if json:
            click.echo(json_module.dumps({
                "schema_version": b.schema_version,
                "created_at": b.created_at,
                "generation_id": b.generation_id,
                "database_identity": b.database_identity,
                "inventory_revision": b.inventory_revision,
                "sources": len(b.sources),
                "mappings": len(b.mappings),
                "programmes": len(b.programmes),
            }))
        else:
            click.echo(f"Bundle: {bundle}")
            click.echo(f"  Schema version: {b.schema_version}")
            click.echo(f"  Created: {b.created_at}")
            click.echo(f"  Generation: {b.generation_id}")
            click.echo(f"  Database: {b.database_identity}")
            click.echo(f"  Inventory: {b.inventory_revision}")
            click.echo(f"  Sources: {len(b.sources)}")
            click.echo(f"  Mappings: {len(b.mappings)}")
            click.echo(f"  Programmes: {len(b.programmes)}")
    else:
        from .bridge import bridge_from_config
        cfg = load_config(config or Path.home() / ".uiptv" / "epg-builder" / "config.toml")
        result = bridge_from_config(cfg.bridge).get_status()
        click.echo(json_module.dumps(result, indent=2))


@cli.command()
@click.option("--config", "-c", type=click.Path(exists=True), help="Path to configuration file")
@click.option("--json", is_flag=True, help="Output as JSON")
def report(config: str | None, json: bool):
    """Generate a matching report."""
    if config:
        cfg = load_config(config)
    else:
        cfg = load_config(Path.home() / ".uiptv" / "epg-builder" / "config.toml")

    path = Path(cfg.working_dir).expanduser() / "last-matches.json"
    if not path.is_file():
        raise click.ClickException("No saved report; run build --dry-run first")
    matches = json_module.loads(path.read_text())
    if json:
        click.echo(json_module.dumps(matches))
    else:
        for match in matches:
            click.echo(f"{match['account_id']}/{match['provider_channel_id']}: {match['match_class']} {match['reason']}")


if __name__ == "__main__":
    cli()
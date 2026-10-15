# EPG channel-name exclusion audit

Scanned 529,505 cached Channel rows (193,387 distinct names) on 15 October 2026. Counts include repeated channels across accounts. No provider addresses or credentials are included.

The database seeds 179 literal rules from this review. Rules are case-insensitive and can be disabled or extended in epg_name_exclusion. They affect name fallback, never provider IDs, XMLTV IDs, programme titles or saved channel names.

## Matching order

Exact ID and explicit mappings take priority. Exact names are next, then unique names after annotation removal, then unique quality-insensitive names. Conflicting normalized aliases are excluded. A rule change invalidates existing mapping signatures; a background refresh remaps cached guides without downloading them again.

## Reviewed labels

| Label | Cached occurrences | Decision |
|---|---:|---|
| hd | 66,383 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 8k | 33,004 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| fhd | 27,216 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 4k | 12,653 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 1080p | 7,825 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 720p | 6,983 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| vip | 5,639 | Bracketed annotation only |
| uhd | 4,091 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| sd | 3,704 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| hevc | 3,098 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| not 24/7 | 2,754 | Bracketed annotation only |
| 576p | 2,236 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| geo-blocked | 1,110 | Bracketed annotation only |
| 480p | 682 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 360p | 414 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 3840p | 335 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 540p | 134 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 576i | 130 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| backup | 119 | Bracketed annotation only |
| h265 | 116 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| shd | 84 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 504p | 75 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| raw | 72 | Bracketed annotation only |
| 1080i | 65 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 406p | 56 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 240p | 54 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 396p | 43 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 2k | 38 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 2160p | 38 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 288p | 31 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 400p | 30 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 404p | 24 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 432p | 22 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 1280p | 18 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 614p | 17 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| h.265 | 16 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 1440p | 14 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 410p | 10 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 270p | 8 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 486p | 8 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 640p | 6 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 768p | 6 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 420p | 6 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 384p | 6 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 416p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 600p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 684p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 1088p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 1086p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 352p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 450p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 712p | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 480i | 4 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 714p | 3 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 368p | 3 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 960p | 3 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| h.264 | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 392p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 552p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 642p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 672p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 478p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 180p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 616p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 412p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 582p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 472p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 548p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 477p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 580p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 160p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 440p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 568p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 300p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 562p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 268p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 320p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 648p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 349p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 214p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 224p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 512p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 718p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 726p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 620p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 751p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 900p | 2 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 532p | 1 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 200p | 1 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |
| 536p | 1 | Quality fallback; bracketed resolutions/codecs also removed before exact-name lookup |

## Preserved identity

Country/language prefixes and suffixes, regional names, East/West feeds, +1/+2 time shifts, channel numbers, Home/Away, sports event names, years and dates are retained. RAW, VIP, Backup and 24/7 are retained as ordinary words; only reviewed full bracket annotations are removed. Broad stripping of these words would create incorrect programme matches. Circled availability markers retain the existing presentation-only fallback behavior.

The full bracket and prefix inventory was reviewed; frequency alone does not justify exclusion. Examples deliberately retained: (D), (Home), (GMT), (P+), (US), (UK), (Apple), (FLOW), [Local], (EAST), (WEST), [LIVE-EVENT] and country prefixes such as US:, UK| and HINDI|.

## Full-cache validation

Using a read-only snapshot of all 193,387 distinct cached channel names and 4,083 currently usable aliases from the configured global guides, the database rules produce 39 additional distinct name matches. Thirteen previous name matches are deliberately rejected because they are playlist section headings such as `##### MOVIES HD #####`, rather than stations. This measures name fallback coverage against this snapshot, not guaranteed EPG coverage for every channel; exact IDs remain authoritative.

## Maintaining the list

Both `term` and `scope` form the primary key. `annotation` removes a complete matching parenthesized or square-bracketed label; `quality` removes whole tokens only during the final quality-insensitive fallback. Terms are literal, case-insensitive text, not regular expressions. Values longer than 128 characters are ignored by readers.

```sql
SELECT term, scope, enabled FROM epg_name_exclusion ORDER BY scope, term;
INSERT INTO epg_name_exclusion (term, scope, enabled) VALUES ('trial', 'annotation', 1);
UPDATE epg_name_exclusion SET enabled=0 WHERE term='trial' AND scope='annotation';
```

Each matching batch reads the active rows. Rule edits change the mapping signature, so older matches cannot silently remain valid. The background refresh remaps existing imported guides; programme data and user channel titles remain unchanged. Empty or conflicting normalized names are never accepted as fallback matches. Exact provider/XMLTV identities and explicit mappings do not use exclusions.

The live-catalogue check also reproduced a bulk-remapping failure when the same provider channel occurs in multiple categories. Mapping batches now deduplicate by account and provider ID before queuing inserts, preventing a primary-key collision from rolling back all matches after rule changes.

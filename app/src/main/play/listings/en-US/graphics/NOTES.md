# Graphics assets — pending design

The following Play Store graphics are required at submission time and
are device- or designer-dependent:

| Asset | Dimensions | Status |
|-------|------------|--------|
| Feature graphic | 1024×500 PNG/JPEG | [BLOCKED: design-required] (T233 dependency) |
| App icon (high-res) | 512×512 32-bit PNG | [BLOCKED: design-required] (T233 dependency) |
| Phone screenshots (2–8) | 16:9 or 9:16, ≥320 px min edge | [PENDING: device-required] |
| Tablet screenshots (optional) | 16:10 or 10:16 | [PENDING: device-required] |
| Promo video URL (optional) | YouTube URL | omit at v1 |

The textual metadata (`title`, `short-description`, `full-description`,
`release-notes`) is drafted in this directory and tested for Play
Store length caps in T234's regression test.

Category: see `../../app-category.txt` (gradle-play-publisher reads
this to set Google Play category at publish time).

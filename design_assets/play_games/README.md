# NumbCrunch Play Games artwork and Console handoff

Original vector pictograms use the game's warm gold and dark wood palette.
Each achievement has a different silhouette; meaning does not depend on color.
SVG sources contain no localized text. PNG exports have transparent corners
and a circular safe area. The image generator was not used.

- `catalog.json`: exact English Console copy, order, thresholds and points.
- `source/*.svg`: editable original vector exports.
- `upload/*.png`: 512 × 512 RGBA upload images, including one leaderboard icon.
- `upload/AchievementsMetadata.csv` and `AchievementsIconsMappings.csv`: headerless import inputs.
- `achievements-v1.zip`: Console-ready bundle for a fresh catalog; inspect existing drafts first.
- `preview/earned-locked-88-and-48.png`: earned and approximate grayscale locked views at two sizes.

Rebuild on macOS with Python 3, Swift and AppKit (no downloaded packages):

```sh
python3 design_assets/play_games/generate_assets.py
python3 design_assets/play_games/check_catalog.py
```

The Python generator defines the original vector primitives. Swift renders those
same primitives into PNGs. Temporary JSON shape files are deleted after export.
The generator also writes the English `strings-achievements.xml` resource from
the catalog. Do not independently edit generated copy/CSVs; update the catalog
and corresponding domain definition together, then regenerate and validate.

Visual QA on November 13, 2026: all eleven designs inspected in the preview.
Book/check, star medal, tiles, abacus, puzzle, crown, lantern, gem, mountain,
eye and trophy remain distinct at 48 px. Important details remain within the
circle; no image contains text. Locked previews are a local approximation;
verify Google's actual rendering once the drafts exist. No Console IDs are
invented, and no resources have been published by these scripts.

See [achievement catalog](../../docs/play-games-catalog-v1.md) and
[leaderboard contract](../../docs/play-games-leaderboard-v1.md) for scope,
integration, ownership and publication gates.

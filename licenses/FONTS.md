# Bundled fonts

Both fonts are variable TrueType files from the `google/fonts` repository. They are licensed under the SIL Open Font License 1.1, and the license texts sit next to this file.

| App file | Upstream path in google/fonts | Axes | License text | sha256 |
| -------- | ----------------------------- | ---- | ------------ | ------ |
| `app/src/main/res/font/sora.ttf` | `ofl/sora/Sora[wght].ttf` | wght | `OFL-Sora.txt` | `84ff7096ae3ec6c8be47d906d1a0ba4de7f2ce78c615275c77301964a316e16c` |
| `app/src/main/res/font/inter.ttf` | `ofl/inter/Inter[opsz,wght].ttf` | opsz, wght | `OFL-Inter.txt` | `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031` |

Downloaded on 2026-11-10 with the authenticated `gh api` call (`Accept: application/vnd.github.raw`). Before copying them into the app, each file was checked for:

- the TrueType magic bytes (`00 01 00 00`)
- an `fvar` table name, which marks a variable font
- the `tnum` feature tag in Inter, which gives tabular figures for amounts

Sora is used for Display, Headline and Title (weights 600 and 700). Inter is used for Body, Label and Caption (weights 400, 500 and 600). Weights are selected at runtime with `FontVariation.weight`, so one file serves every weight. Variable fonts need Android 8.0 (API 26), which is the app minimum.

To verify a file after a checkout, run `shasum -a 256 app/src/main/res/font/*.ttf` and compare with the table.

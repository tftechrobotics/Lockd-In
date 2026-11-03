# Built-in study audio: asset register (R48)

Only recordings we own, or have verified permission to redistribute in the app, may ship. A composition being public domain does not make a particular recording of it free to distribute.

## Current files (all DEVELOPMENT recordings)

| File (res/raw) | Collection | Length | Source | Licence / rights | Attribution | Cleared to ship? |
|---|---|---|---|---|---|---|
| piano_1.ogg | Piano | 2:29 | Synthesised by `design/audio/gen.py` (additive piano model, original progressions) | Owned by the project (generated, no third-party material) | None needed | Yes legally; quality is development-level |
| piano_2.ogg | Piano | 2:28 | same | same | — | same |
| piano_3.ogg | Piano | 2:28 | same | same | — | same |
| strings_1.ogg | Strings | 2:17 | Synthesised by `design/audio/gen.py` (band-limited ensemble model) | Owned | — | same |
| strings_2.ogg | Strings | 2:18 | same | same | — | same |
| strings_3.ogg | Strings | 2:13 | same | same | — | same |
| rain_1.ogg | Rain | 4:56 loop | Synthesised by `design/audio/gen.py` (filtered noise and droplets, crossfaded loop) | Owned | — | Yes |
| quietpiano_1.ogg | Quiet piano (chill) | 2:05 | Synthesised by `design/audio/gen.py` (soft, sparse piano) | Owned | — | Yes legally; development quality |
| quietpiano_2.ogg | Quiet piano (chill) | 2:00 | same | same | — | same |
| lofi_1.ogg | Lo-fi (study) | 2:22 | Synthesised (soft drums, warm keys, vinyl noise) | Owned | — | same |
| lofi_2.ogg | Lo-fi (study) | 2:24 | same | same | — | same |
| ambient_1.ogg | Ambient (study) | 2:28 | Synthesised (slow pads) | Owned | — | same |
| ambient_2.ogg | Ambient (study) | 2:28 | same | same | — | same |
| ocean_1.ogg | Ocean (chill) | 3:54 loop | Synthesised (modulated noise waves) | Owned | — | Yes |
| forest_1.ogg | Forest (chill) | 3:54 loop | Synthesised (wind, leaves, birds) | Owned | — | Yes |
| brown_1.ogg | Brown noise (chill) | 2:54 loop | Synthesised (integrated noise) | Owned | — | Yes |

All files are Ogg Opus at 64 kbps (rain 56 kbps), 48 kHz stereo, with no metadata. They're about 21 MB in total. To rebuild them, run `python3 design/audio/gen.py` (it's deterministic; `--new-only` rebuilds just the 2026-11-02 additions).

In the app, collections are grouped as **Study music** (Piano, Strings, Lo-fi, Ambient) and **Quiet and chill** (Quiet piano, Rain, Ocean, Forest, Brown noise), owner request 2026-11-02.

The app labels these as "Development recordings" in the profile editor (`AudioCollection.development`).

## Content still needed for release

- **Piano and Strings:** produced instrumental recordings with no lyrics, about 6 to 10 tracks per collection and 3 to 6 minutes each. They must be either commissioned with a written buy-out, or recorded and released by us. If we use a public-domain work such as Bach or Satie, we still need rights to the *recording*.
- **Rain:** the generated loop can ship. A field recording would need a release.
- **For each new file, record:** source, performer or creator, licence text or contract reference, date, attribution wording, and whether redistribution inside an app is allowed.
- **Planned collections:** produced (not generated) recordings for every collection above before release. Adding one means adding a value to `AudioCollection` and its files to `res/raw`. Nothing else changes.

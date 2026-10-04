# Zen Sudoku complete Google Play visual asset package

The package is ready for Andrew's manual upload. Icon retained, feature graphic created, six existing production screenshots retained, eight untouched raw captures preserved. No application source, launcher resources, package/version settings, signing material or build output changed.

## Upload files

| Asset | Path | Format |
| --- | --- | --- |
| App icon | `icon/app-icon-512x512.png` | 512 × 512, RGBA PNG, 75,654 bytes |
| Feature graphic | `feature/feature-graphic-1024x500.png` | 1024 × 500, RGB PNG, no alpha |
| Phone screenshots | `phone/production/01-menu.png` through `06-settings.png` | Each 1080 × 1920, RGB PNG, no alpha |

Screenshot order: `01-menu.png`, `02-gameplay.png`, `03-hints.png`, `04-notes.png`, `05-pause-resume.png`, `06-settings.png`. All presentation and captured UI remain unchanged from the screenshot phase. Review the complete set in `review/complete-play-set.jpg`.

Suggested feature graphic alt text: “Zen Sudoku watercolour cloud and brush wordmark with the line ‘A quieter kind of puzzle.’”

## Icon decision: retain

Reviewed the existing 512px Play icon, all ten legacy standard/round launcher PNGs across mdpi–xxxhdpi, both adaptive XML definitions, foreground vector and background colour. The cloud is the same mark used in the actual home-screen wordmark; lavender and watercolour textures match the refreshed app and screenshots. Its white contours separate the coloured cloud from the lavender tile on light and dark surroundings. It reads as the established cloud at 48px; fine texture softens at 32px, as expected. A grid or text added at that size would introduce clutter and change the identity without sufficient benefit.

Both adaptive definitions reference the same foreground and background. Rendered the unchanged foreground with the released #B4A9D2 background and simulated the central 72dp viewport with circle, rounded-square and squircle masks. The cloud outline lies inside a radius of 24.83dp from the 108dp layer centre, within the 33dp safe radius; no cloud clipping was observed. These are static design simulations, not a claim of exhaustive launcher animation testing. No icon refinement was needed, so no in-app icon replacement or rebuilt release was required.

`icon/app-icon-512x512.png` is a byte-for-byte copy of `app/src/main/ic_launcher-playstore.png`, not a redesign. The Play export has an RGBA channel with fully opaque pixels, full-square artwork, and no baked store shadow. Existing legacy launcher resources include their original Android-shaped treatment and are deliberately not reused as the Play export. Application icon files remain unchanged.

## Feature graphic

Uses the exact existing `ic_logo_banner.xml` cloud/brush wordmark and `ic_waterclour_background2.xml` watercolour scene. The renderer removes only the logo vector's translucent decorative banner veil so the unmodified brand paths sit directly on the shared background. A parchment veil follows the app's light treatment. The short supporting line uses the app's Ubuntu Regular and muted ink. Critical artwork is inset approximately 84px horizontally and 146px vertically; the slogan sits away from the edges. No app UI, device imagery, badges, rankings, price claims or fictional features were generated.

Google recommends extending rather than prominently duplicating the app icon in feature graphics. We retained the cloud as part of the actual combined wordmark, with the title providing the wider brand expression. Independent review noted this as a minor marketing tradeoff, not a format or truthful-representation blocker. The existing combined identity was preferred over inventing a new logo system.

## Consistency and independent review

The icon, feature graphic and six screenshots share lavender, aqua, parchment, restrained ink and original watercolour artwork. The existing brush wordmark is intentional brand lettering; Ubuntu is the shared supporting/UI typeface. No screenshot needed a presentation adjustment. The first three explain difficulty choice, play and genuine staged hints; notes, saved progress and preferences each add distinct information.

One independent Codex reviewer inspected the complete board, icon masks/sizes, full feature graphic and screenshot sources. Result: approved, no critical or important findings. The reviewer independently confirmed screenshot pixels match their source crops after uniform resizing. Minor findings addressed: clarify historical phone handoff scope, fix cropped labels on the icon review sheet. Minor tradeoffs retained: cloud detail at 32px and feature/icon brand repetition. The lead then inspected the corrected sheet and final feature graphic.

## Validation and reproducibility

`validate_assets.py` verifies image formats/dimensions, all six captured-UI pixel matches, unchanged icon copy, ten legacy output sizes, adaptive resource references, eight raw capture hashes, and absence of external text in the retained app UI XML. Results are in `review/validation.json`.

Run `python3 release-assets/google-play/validate_assets.py` to recheck. Feature rendering requires Pillow and CairoSVG; run `python3 release-assets/google-play/render_assets.py`. Rendering was performed with Pillow 10.2.0 and CairoSVG 2.9.1 using dependencies installed only under `/tmp`; no dependency caches or environment were added to Git. `phone/compose.py` preserves the previous screenshot composition method.

Original release source: master at c93405de372222d141dafa010cfdf5340989ac60; package rew.lightgames.zensudoku, version 1.0 (1). Capture/build provenance remains in `phone/evidence/` and the historical `phone/HANDOFF.md`. The approved signed AAB remains unchanged; this asset commit does not create a replacement AAB.

Official guidance reviewed:
- https://support.google.com/googleplay/android-developer/answer/9866151?hl=en-GB
- https://developer.android.com/distribute/google-play/resources/icon-design-specifications
- https://developer.android.com/develop/ui/compose/system/icon_design_adaptive

## Git scope and remaining work

Only the `release-assets/google-play/` package is included: upload images, untouched raw captures, UI/provenance evidence, useful review sheets, reproducible scripts and handoffs. No drafts, caches, intermediate vector/raster renderings, credentials, signing files, APKs or AABs belong in this commit. Commit message: `Add complete Zen Sudoku Google Play visual asset set`. The commit hash and verified push result are supplied in the final user handoff; they are not embedded in their own commit.

Local master and remote master must be checked immediately before a normal push. No force-push, CI configuration, deployment, Play Console upload or publication is authorised by this package.

No remaining required phone-listing image gap was identified. A preview video, additional localised copy/graphics, and genuine tablet screenshots are optional separate work if Andrew chooses to use those surfaces.

Agent use: one primary Codex agent composed and validated the assets, plus one independent review subagent. No image-generation model or other delegation was used. No model override was requested or selected; exact runtime model identifiers are not exposed here.

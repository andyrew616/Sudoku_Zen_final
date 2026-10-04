# Release asset evidence

Audit date: 2026-10-04. Files were preserved; this is evidence, not an invented licence.

| Asset | Local evidence | Release decision |
| --- | --- | --- |
| `app/src/main/res/raw/purple_planet_zen.mp3` | Embedded title **A Touch of Zen**, artist **Purple Planet Music**, album **Dreamy**. SHA-256 `688531195d2fa53c009eefadfaf4e9b815312197a7bb15b95ee1c599aebc6f9a`. Played on loop by MusicService. | **Unresolved**: supply original download/source, licence terms applicable to Android redistribution/monetisation, purchase/permission if applicable, and required credit wording/placement. No licence document, receipt or credit found in repository/history. Filename and metadata do not grant rights. |
| `app/src/main/res/raw/pop.ogg` | Ogg stream metadata: `ARTIST=Arrall Austin arrall.com`, `COMMENTS=CC0`. SHA-256 `e8ef22564c704a6f6217e03885cba51ebd7761360e59fc8e247b2f4f97bc4e0e`. Used as tap sound. | Local CC0 declaration exists. Original acquisition/source record would strengthen provenance; do not report this as entirely unlicensed. |
| `app/src/main/res/font/ubuntu_regular.ttf`, `ubuntu_medium.ttf` | Font metadata identifies Canonical and Ubuntu Font Licence 1.0. Full notice/licence included in `res/raw/ubuntu_font_licence.txt`. | Adequate bundled local licence evidence; preserve it. |
| Legacy illustrations, background/launcher images and vector artwork in `res/drawable`, `res/mipmap*`, `src/main/ic_launcher-playstore.png` | Repository has files/history but no comprehensive source/ownership ledger. | **Unresolved provenance**: owner must confirm original ownership or provide source/licence for the third-party subset, especially watercolour backgrounds, cloud/menu/completion illustrations and launcher/brand art. Unknown provenance is not a finding of infringement. |
| Puzzle corpus `src/main/assets/easy.csv` | Historical bundled corpus; generator report describes original engine implementation, not provenance/licence of this CSV. | Confirm corpus authorship/source if not original. Engine implementation evidence alone does not establish corpus rights. |

Exact pre-existing artwork paths and current SHA-256 values are listed in
[legacy artwork inventory](legacy-artwork-inventory.tsv). The requested evidence
is an owner authorship declaration for original work, or source/licence/credit
requirements for any externally sourced subset. The inventory does not presume
these assets are third-party or infringing. Key release references include
`ic_waterclour_background2.xml`, `ic_menu_cloud.xml`, `ic_lvl_complete_popup.xml`,
`ic_icon_circle.xml`, `ic_icon_square.xml`, `ic_splash.xml` and
`ic_launcher-playstore.png`. `ic_logo_banner.xml` has subsequent repository
edits; retain its original artwork provenance as well.

No other bundled audio was found. Java/Kotlin dependencies are recorded in
Gradle and the AAB dependency metadata; no new runtime library was introduced.
This audit does not certify all transitive software licences. Complete the music
and artwork evidence before publication and add required in-app credits once the
actual terms are known. Assets have not been removed or replaced.

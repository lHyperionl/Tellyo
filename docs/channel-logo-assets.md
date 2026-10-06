# Bundled channel logos

The shared `ChannelLogo` component includes the broadcaster artwork for all 14
channels in `app/src/main/assets/channels.json`. These resources display offline,
including for previously cached catalogues without logo URLs. An explicit
catalogue logo URL continues to take precedence, with the bundled artwork shown
while loading or on failure. Other channels keep their existing text fallback.

Assets were retrieved on 2026-10-06 from broadcaster websites and the logo
archives linked below. The marks
belong to their respective broadcasters; inclusion does not imply endorsement.

| Channel | Broadcaster asset source | Bundled resource |
| --- | --- | --- |
| Jednotka | https://www.stvr.sk/media/images/icons/2026/jednotka.svg | `drawable/channel_logo_stvr_jednotka.xml` |
| Dvojka | https://www.stvr.sk/media/images/icons/2026/dvojka.svg | `drawable/channel_logo_stvr_dvojka.xml` |
| :24 | https://www.stvr.sk/media/images/icons/2026/24.svg | `drawable/channel_logo_stvr_24.xml` |
| Šport | https://www.stvr.sk/media/images/icons/2026/sport.svg | `drawable/channel_logo_stvr_sport.xml` |
| JOJ | https://lookbook.jojgroup.sk/wp-content/uploads/2025/10/logo_tv-joj.png | `drawable-nodpi/channel_logo_joj.png` |
| JOJ Plus | https://lookbook.jojgroup.sk/wp-content/uploads/2025/10/logo_plus.png | `drawable-nodpi/channel_logo_joj_plus.png` |
| JOJ Krimi | https://lookbook.jojgroup.sk/wp-content/uploads/2026/03/logo_joj-krimi-2026_w.png | `drawable-nodpi/channel_logo_joj_krimi.png` |
| JOJ 24 | https://lookbook.jojgroup.sk/wp-content/uploads/2025/10/logo_joj-24.png | `drawable-nodpi/channel_logo_joj_24.png` |
| Markíza | https://www.markiza.sk/html/assets/logo.svg | `drawable-nodpi/channel_logo_markiza.png` |
| Doma | https://commons.wikimedia.org/wiki/File:Mark%C3%ADza_Doma_(2022).svg | `drawable-nodpi/channel_logo_markiza_doma.png` |
| Dajto | https://seeklogo.com/vector-logo/454406/markiza-dajto | `drawable-nodpi/channel_logo_markiza_dajto.png` |
| Markíza Krimi | https://seeklogo.com/vector-logo/439428/markiza-krimi | `drawable-nodpi/channel_logo_markiza_krimi.png` |
| Markíza Klasik | https://logos-download.com/brands/markiza-klasik | `drawable-nodpi/channel_logo_markiza_klasik.png` |
| TA3 | https://www.ta3.com/ta3static/images/ta3-logo.svg | `drawable-nodpi/channel_logo_ta3.png` |

STVR SVG paths and clipping were converted directly into Android vector
drawables. TA3's SVG embeds a PNG, which was extracted. Other PNG exports have
transparent outer margins trimmed and are capped at 640 × 320 pixels without
upscaling or changing their proportions. Markíza channels use horizontal
wordmarks with white artwork on a transparent background, replacing the circular
badges. Markíza's white SVG is used directly; Doma and Klasik SVG fills are
converted to white, and Dajto/Krimi PNG exports have their white backgrounds
removed while preserving antialiased silhouettes. Compact Markíza logos use a
104 × 36 dp area so the lettering stays readable on dark channel cards.
JOJ Krimi uses the broadcaster's official white-and-gold transparent export,
unchanged at 800 × 352 pixels. Its light lettering stays readable on dark channel
cards without a backing plate.

The caller's dimensions take precedence over the component's default image
dimensions, so large artwork placeholders retain their intended logo size.

# Tellyo visual refresh

## Audit and direction

This is a brand-preserving refresh of a native Android TV product. The supplied
design-taste-frontend skill informs the audit, hierarchy and restraint; its web
marketing layouts, image-generation requirements and dual-theme defaults do not
apply to this dark TV shell. Compose Material remains the native foundation.

The existing brand is dark navy with electric blue, a play mark and sans-serif
wordmark. Navigation, Slovak copy, channel order, live programme metadata and the
D-pad routes are retained. There is substantial existing working-tree development;
this pass changes presentation on top of that work.

The visual debt is repeated saturated blue selections, persistent hero borders,
focus glows, uppercase section headings, cramped programme metadata and duplicated
programme titles over artwork. Missing metadata also creates long repetitive labels.

Design variance: 5. Motion intensity: 3. Visual density: 5. Use charcoal/navy
surfaces, one blue accent, bright outlines for actual remote focus, quiet selected
states, sentence-case headings and measured spacing. Error/success colours and
broadcaster logo colours remain semantic exceptions to the accent palette.

## Presentation rules

- Surfaces: low-contrast navy neutrals; no outer focus glow.
- Focus: a bright 3 dp outline and a small scale change, disabled by reduced motion.
- Selection: a quiet tinted fill; only the current remote target has the focus ring.
  Channel tiles use a persistent blue fill and 2 dp blue outline for the last
  browsed channel, with a 4 dp pale outline and brighter fill while remotely focused.
  D-pad focus updates the browsed channel and hero preview without starting playback.
- Corners: 16 dp hero, 12 dp channel tiles, 10 dp controls, 6 dp guide cells.
- Artwork: actual programme imagery only. A broadcaster logo provides the fallback;
  never manufacture a show image or label an unknown programme as live.
  Fit thumbnails at their original aspect ratio, aligned to the right. Do not crop
  a 16:9 thumbnail to fill the wider hero artwork area; that cuts off faces and captions.
- Typography: native sans-serif; programme title leads, channel identity follows.
- Loading/empty/error content: retain the existing data states and focus entry points.
- TV sizing: retain the existing 1672 x 941 reference-density scaling and safe edges.
- Guide: a three-hour home preview; the full guide preserves its existing time
  window. Short programmes retain their real widths, omit metadata that cannot
  fit and clip their labels instead of overlapping adjacent time slots.
- Enlarged text: the hero retains a two-line synopsis beneath the title and time.
  The extra information button is removed; the preview itself opens playback.
  Channel tiles omit their secondary time label to keep controls legible.
- Icons: official Compose Material outlined controls; preserve the existing brand
  mark independently of the control icon family.

## Validation

The working-tree unit suite passed all 43 tests. Existing Home/Guide/Settings
D-pad coverage and new hero bounds checks passed all 13 tests on the API 34 TV emulator. The
new checks cover a long programme title at normal font scale and at 1.4 font scale
with reduced motion, checking that the complete player action is visible and works.

The palette's body, muted, selected metadata, button, live badge and toggle text
combinations measure at least 5.41:1 contrast. Remote focus has one bright outline;
selected programmes and channels retain quieter fills. No automatic decorative
motion, manufactured programme artwork, navigation-label changes or brand-logo
replacement was introduced.

The channel-highlight follow-up adds a stronger focused fill (secondary text
contrast 4.73:1) and a persistent selected outline (3.39:1 against its fill).
A regression checks that browsing updates the selected channel, retains the
highlight after moving focus away and does not start playback on focus alone.
All 10 Home focus and layout tests passed on the API 36 TV emulator for this
follow-up. The API 34 emulator exited during two attempts; API 36 completed using
software rendering with Vulkan disabled. Debug assembly and lint also passed.

Visual review uses actual emulator screenshots of Home, Guide, Favorites and
Settings. Screenshots are local inspection artifacts in `artifacts/ui-redesign/`.
The emulator's native 1920 x 1080 window is the verified visual target. The attempted
1280 x 720 check could not be completed: emulator 37.1.11 exited both during a live
resolution change and after launching the app from a cold 720p boot. Only the TV
launcher could be captured at 720p, so there is no verified 720p app screenshot.
The 1080p app screenshots and all instrumentation tests completed before that
emulator failure. This pass does not claim physical-TV playback or performance
certification.

Debug assembly and lint passed (0 errors, 37 warnings). The full Material icon
dependency adds roughly 10 MB to the unshrunk debug APK; release shrinking was
not changed in this visual pass.

Build and verification commands:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.feature.home.HomeScreenFocusTest,com.tellyo.app.feature.guide.GuideScreenFocusTest,com.tellyo.app.feature.guide.GuideScreenPerformanceTest,com.tellyo.app.feature.settings.SettingsScreenFocusTest'
```

The local Java selector workaround is documented in `docs/ui-performance.md`.
Use short ASCII SDK/AVD paths if the emulator stalls while starting from an accented
Windows profile path. These are session-only environment adjustments.

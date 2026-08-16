# Tellyo — Android TV / Mi Box Implementation Plan

## 1. Project Summary

**Tellyo** is a modern Android TV application designed primarily for Xiaomi Mi Box / Google TV / Android TV devices.

The application provides a unified interface for watching legally accessible live television streams, browsing an electronic program guide (EPG), managing favorite channels, and later optionally supporting local timeshift/DVR functionality.

The product should feel like a polished consumer TV application rather than a developer-oriented IPTV client.

This document is the implementation source of truth for Codex.

---

## 2. Product Goals

### Primary goals

1. Build a fast, reliable Android TV application optimized for remote-control / D-pad navigation.
2. Provide a unified interface for multiple legally accessible live-TV providers.
3. Keep playback direct between the Mi Box and the broadcaster/CDN whenever possible.
4. Support HLS and DASH through AndroidX Media3 / ExoPlayer.
5. Provide a clean and modern EPG.
6. Provide favorites and custom channel ordering.
7. Provide strong diagnostics and error handling.
8. Maintain the selected **Tellyo visual identity** consistently throughout the application.
9. Keep the architecture modular enough to add providers without rewriting the UI or player.
10. Introduce timeshift and experimental commercial skipping only after the core TV experience is stable.

---

# 3. Product Name and Branding

## Brand name

**Tellyo**

Do not use:

- SlovakTV
- Slovak TV
- Tellyo TV

The visible product name should simply be:

**Tellyo**

---

## 3.1 Logo direction

The approved logo direction is:

- a compact **blue play-button icon**
- immediately followed by the wordmark **Tellyo**
- `Telly` in white
- final `o` in bright electric blue
- no large standalone letter `T`
- no television antenna icon
- no broadcast-wave icon in the primary logo
- no excessive 3D styling

Conceptually:

```text
▶ Tellyo
```

Where:

- the play icon uses the primary blue gradient/accent
- `Telly` uses near-white
- the final `o` uses the primary bright blue
- icon and wordmark form one compact horizontal unit

The logo must work well:

- in the top-left navigation area
- on splash screens
- as a launcher icon variant
- on dark backgrounds
- at small TV-viewing sizes

### Branding rule

The approved Tellyo logo must not be redesigned independently by Codex.

If exact production vector assets are not yet available, Codex should temporarily use a clean approximation while keeping sizing, spacing, colors, and composition compatible with the approved visual direction.

---

# 4. Approved UI Design Direction

The selected application design is a hybrid of the previously approved concepts:

- **Concept 1** — cinematic dark interface, large channel cards, premium focus glow, favorites row
- **Concept 3** — left-side TV navigation, large live preview, structured EPG, channel-switch overlay

The resulting approved visual direction is referred to in this document as:

**Tellyo Hybrid TV UI**

This is the visual source of truth.

Codex must not replace it with default Material styling or a generic Android TV template.

---

# 5. Design Principles

The Tellyo UI must follow these principles:

1. **Dark cinematic TV-first interface**
2. **Deep navy background**
3. **White primary typography**
4. **Electric blue interaction/accent color**
5. **Large readable typography**
6. **Strong D-pad focus states**
7. **Subtle gradients and glow**
8. **Rounded but not overly soft components**
9. **Low visual clutter**
10. **Content-first layouts**
11. **No mobile-style bottom navigation**
12. **No dense IPTV spreadsheet aesthetic**
13. **No Material You dynamic colors**
14. **No excessive glassmorphism**
15. **No random color changes between screens**

The interface must remain readable from approximately 2–4 meters away.

---

# 6. Core Visual System

## 6.1 Color palette

Use this palette as the initial implementation target.

### Background

```text
Background Primary:   #050B18
Background Secondary: #081426
Surface Primary:      #0B1A2D
Surface Secondary:    #10233A
Surface Elevated:     #132A45
```

### Blue

```text
Primary Blue:         #168BFF
Primary Blue Bright:  #25A7FF
Primary Blue Dark:    #0866E8
Focus Glow:           rgba(22, 139, 255, 0.40)
```

### Text

```text
Text Primary:         #F7F9FC
Text Secondary:       #B7C0CE
Text Muted:           #7D8999
```

### Semantic colors

Only use semantic colors when required:

```text
Live / destructive:   #F04444
Success:              #45C96B
Warning:              #F5B942
```

Channel logos may retain their original colors.

The rest of the application should remain primarily blue + white + navy.

---

## 6.2 Typography

Use a modern sans-serif family that renders cleanly on Android TV.

Preferred hierarchy:

```text
Display / hero title:     36–44sp
Screen title:             30–36sp
Section title:            22–26sp
Channel name:             20–24sp
Program title:            18–22sp
Body:                     16–18sp
Metadata:                 14–16sp
Small diagnostic text:    12–14sp
```

Do not use very thin font weights.

Preferred weights:

```text
Regular
Medium
SemiBold
Bold
```

---

## 6.3 Corners

Recommended radii:

```text
Large hero panel:     20dp
Channel cards:        14–16dp
EPG cells:            8–10dp
Buttons:              10–12dp
Navigation focus:     12dp
```

---

## 6.4 Focus state

TV focus must be immediately obvious.

Focused elements should use:

- bright blue outline
- 1.03–1.06 scale
- subtle blue outer glow
- slightly brighter surface
- white text retained
- animation around 150–220 ms

Example:

```text
Unfocused:
border = subtle navy/gray

Focused:
border = 2–3dp electric blue
scale = 1.05
glow = soft blue
```

Do not rely on color alone.

---

# 7. Approved Main Screen Layout

The main screen should follow the approved hybrid concept.

```text
┌──────────────┬────────────────────────────────────────────────────┐
│              │                                                    │
│ Tellyo       │           Large live preview / hero               │
│              │                                                    │
│ Home         │                                   Channel switch   │
│ Live TV      │                                   overlay          │
│ TV Guide     │                                                    │
│ Favorites    ├────────────────────────────────────────────────────┤
│ Search       │ Favorites / channel cards                         │
│ Settings     ├────────────────────────────────────────────────────┤
│              │                                                    │
│ date/time    │                EPG timeline                        │
│              │                                                    │
└──────────────┴────────────────────────────────────────────────────┘
```

---

# 8. Left Navigation Sidebar

The sidebar should contain:

```text
Home
Live TV
TV Guide
Favorites
Search
Settings
```

Slovak UI labels can later be:

```text
Domov
TV naživo
TV program
Obľúbené
Hľadať
Nastavenia
```

The sidebar should:

- remain visible on the primary dashboard
- use icons + labels
- highlight the focused/current destination in electric blue
- visually separate navigation from content without a heavy divider
- show the Tellyo logo at the top
- optionally show current date/time near the bottom

The active/focused navigation item should use a blue filled rounded rectangle, matching the approved mockup direction.

---

# 9. Hero / Live Preview Area

The top content area should contain a large live TV preview.

Content:

- channel logo
- channel name
- LIVE badge
- current program title
- start/end time
- progress bar
- short description when available
- optional "More information" action
- actual video preview or high-quality fallback artwork

Example:

```text
LIVE

STVR 1
News

19:00 — 20:30
███████████░░░░░

Short description...
```

The video/image should visually dominate the card.

Use a left-to-right dark gradient overlay behind text.

The overall panel should feel cinematic rather than like a plain rectangle.

---

# 10. Channel Switch Overlay

When the user opens quick channel switching, show a floating panel over the right side of the hero/player.

Example:

```text
Change channel

1  STVR 1
2  JOJ
3  TA3
4  :24
5  Dvojka
```

Requirements:

- dark translucent/elevated panel
- selected row in electric blue
- white text
- quick D-pad navigation
- channel number + logo/name
- optional signal/availability indicator
- dismiss on Back
- confirm on OK

Do not obscure the entire video.

---

# 11. Favorite Channel Cards

Below the hero area, display a horizontal channel row.

Each card should contain:

- channel logo
- channel name
- LIVE badge where applicable
- current program name
- time range
- progress bar

Preferred aspect:

- wide enough for readable titles
- compact enough to display 5–7 channels on 1080p

Focused card:

- bright blue border
- subtle glow
- slight scale
- elevated z-order

The visual treatment should closely follow the approved Concept 1 channel-card style.

---

# 12. EPG / TV Guide

The EPG is one of the core features and should visually follow the structured layout of Concept 3 while preserving the cleaner styling of Concept 1.

Structure:

```text
          20:00      20:30      21:00      21:30

STVR 1    Program A──────────    Program B─────────
JOJ       Program C──────        Program D─────────
TA3       Program E────────────  Program F─────────
:24       Program G────────      Program H─────────
```

Requirements:

- fixed channel column on the left
- horizontal time axis
- current-time vertical marker
- program blocks sized by duration
- current program visually distinguished
- focused program strongly highlighted in blue
- support left/right time navigation
- support up/down channel navigation
- display program detail on OK
- preserve smooth focus movement

The grid must remain usable with a remote and not behave like a mouse-oriented web grid.

---

# 13. Player UI

During normal playback:

- video should occupy the entire screen
- controls should disappear after approximately 4–6 seconds

On OK:

Display a bottom overlay containing:

- channel name/logo
- current program
- progress bar
- live indicator
- TV Guide button
- audio track
- subtitles
- options

Example:

```text
STVR 1                                ● LIVE
News

19:00 ━━━━━━━━━━━━━━━●━━━━━━━━━━━━ 20:30

[ TV Guide ] [ Audio ] [ Subtitles ] [ More ]
```

The overlay should use a dark vertical gradient instead of a large opaque box.

---

# 14. Remote-Control Behavior

Recommended controls:

```text
UP / DOWN
Switch channel while watching
Navigate vertically in menus

LEFT / RIGHT
Navigate horizontally
Seek when timeshift is available

OK
Open controls
Confirm focused action

BACK
Close overlay / return to previous screen

Long RIGHT
Optional large skip during timeshift

Long LEFT
Optional large backward seek
```

All essential functionality must be accessible without touch input.

---

# 15. Technology Stack

## Android

```text
Language:            Kotlin
UI:                  Jetpack Compose for TV
Playback:            AndroidX Media3 / ExoPlayer
Media lifecycle:     MediaSession + MediaSessionService
Networking:          OkHttp
Serialization:       Kotlin Serialization
Dependency injection:Hilt
Database:            Room
Settings:            DataStore
Background work:     WorkManager
Concurrency:         Kotlin Coroutines + Flow
Images:              Coil
Testing:             JUnit + Android / Compose TV UI tests
```

---

# 16. Architecture

Use a layered, modular architecture.

```text
UI
│
├── ViewModels / state holders
│
├── Domain repositories
│
├── Provider abstraction
│
├── Playback service
│
├── EPG repository
│
└── Local persistence
```

Suggested module structure:

```text
Tellyo/
│
├── app/
│
├── core/
│   ├── model/
│   ├── network/
│   ├── database/
│   ├── player/
│   ├── epg/
│   ├── settings/
│   └── ui/
│
├── providers/
│   ├── provider-api/
│   ├── provider-static/
│   ├── provider-stvr/
│   ├── provider-joj/
│   ├── provider-ta3/
│   └── provider-external/
│
├── features/
│   ├── home/
│   ├── live-tv/
│   ├── player/
│   ├── guide/
│   ├── favorites/
│   ├── search/
│   └── settings/
│
└── timeshift/
    ├── buffer/
    ├── playlist/
    └── detection/
```

Do not create every module immediately if it would slow down initial delivery.

---

# 17. Domain Models

## Channel

```kotlin
data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val providerId: String,
    val category: ChannelCategory,
    val enabled: Boolean
)
```

## Stream source

```kotlin
data class StreamSource(
    val channelId: String,
    val uri: Uri,
    val type: StreamType,
    val headers: Map<String, String>,
    val expiresAt: Instant?,
    val isTimeshiftable: Boolean
)

enum class StreamType {
    HLS,
    DASH
}
```

## Program

```kotlin
data class Program(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val start: Instant,
    val end: Instant,
    val category: String?
)
```

---

# 18. Provider Abstraction

The UI must never directly depend on stream URLs.

```kotlin
interface TvProvider {
    val id: String

    suspend fun getChannels(): List<Channel>

    suspend fun resolveStream(
        channel: Channel
    ): StreamResolution
}
```

Result:

```kotlin
sealed interface StreamResolution {

    data class Playable(
        val source: StreamSource
    ) : StreamResolution

    data class ExternalApp(
        val packageName: String?,
        val deepLink: Uri?
    ) : StreamResolution

    data class Unavailable(
        val reason: String
    ) : StreamResolution
}
```

This allows the app to support:

- direct legal HLS/DASH playback
- external provider applications
- temporarily unavailable sources

without leaking provider details into the UI.

---

# 19. Provider Rules and Access Restrictions

Codex must follow these rules:

> Never bypass DRM, authentication, subscription checks, geo restrictions, signed-URL protections, or other access controls.

Do not:

- reverse engineer protected DRM workflows
- fake authentication
- extract paywalled protected streams
- bypass subscription requirements
- bypass provider access controls
- bypass geo restrictions

If a provider does not offer a valid third-party playback route:

```text
ExternalApp(...)
```

or:

```text
Unavailable(...)
```

must be returned.

---

# 20. Playback Engine

Use a single central playback service.

```text
PlaybackService
│
├── ExoPlayer
├── MediaSession
├── track selection
├── subtitles
├── quality selection
└── player state
```

Do not create separate players for individual screens.

Recommended flow:

```text
Channel selected
      ↓
TvProvider.resolveStream()
      ↓
StreamResolution
      ↓
MediaItem
      ↓
PlaybackService
      ↓
ExoPlayer
```

---

# 21. Playback States

The app should model playback explicitly.

```text
IDLE
RESOLVING
BUFFERING
PLAYING
RETRYING
UNAVAILABLE
EXTERNAL
ERROR
```

The UI should render based on state rather than infer network/player behavior.

---

# 22. Stream Switching

When changing channels:

```text
User selects next channel
        ↓
resolveStream()
        ↓
validate result
        ↓
player.setMediaItem()
        ↓
prepare()
        ↓
play()
```

On failure:

```text
retry
 ↓
retry
 ↓
resolveStream() again
 ↓
retry
 ↓
show error
```

Re-resolving is important because provider URLs may expire.

---

# 23. EPG Architecture

Keep EPG completely separate from video playback.

```text
EPG source
   ↓
Parser
   ↓
EpgRepository
   ↓
Room
   ↓
ViewModels
   ↓
Guide UI
```

Possible EPG sources:

- official provider API
- XMLTV
- static source
- bundled test data

Recommended database entities:

```text
channels
programs
favorites
source_health
```

Example `programs` fields:

```text
id
channel_id
start_time
end_time
title
description
category
```

---

# 24. Background EPG Refresh

Use WorkManager.

Flow:

```text
periodic worker
   ↓
download EPG
   ↓
parse
   ↓
validate
   ↓
Room transaction
   ↓
update UI through Flow
```

The application must remain usable when the EPG server is temporarily unavailable.

---

# 25. Favorites

Support:

- favorite/unfavorite
- custom channel order
- persistence
- fast switching using custom order

Store locally.

The user-facing channel sequence should not be coupled to provider implementation order.

---

# 26. Local Configuration

Do not hardcode all channel metadata inside UI code.

Use a bundled configuration model.

Example:

```json
{
  "schemaVersion": 1,
  "channels": [
    {
      "id": "stvr1",
      "number": 1,
      "name": "STVR 1",
      "provider": "stvr"
    }
  ]
}
```

Fallback order:

```text
remote config
    ↓ fail
cached config
    ↓ fail
bundled config
```

The application should always have a safe local fallback.

---

# 27. Backend Strategy

## Phase 1

No backend.

The Mi Box should connect directly to the valid broadcaster/CDN stream.

```text
Mi Box → provider/CDN
```

Do not introduce:

- Docker
- Kubernetes
- cloud database
- user-account backend
- video proxy

for the MVP.

---

## Optional future backend

If later needed, the server should initially provide only metadata/configuration:

```text
/config/v1/channels.json
/config/v1/providers.json
/epg/v1/guide.xml.gz
```

The server should not become a video proxy by default.

---

# 28. Diagnostics

Provide a dedicated diagnostics screen.

Example:

```text
Network             OK
EPG                 Updated 11:58
STVR                OK
JOJ                 External
TA3                 OK

Current stream:
Protocol            HLS
Resolution          1920×1080
Bitrate             5.1 Mbps
Dropped frames      3
Buffer              8.2 sec
Live offset         4.1 sec
```

Also provide:

```text
Test all channels
```

Example output:

```text
✓ STVR 1     842 ms
✓ Dvojka     1021 ms
✕ Example    HTTP 403
✓ :24        711 ms
```

This should be implemented before advanced timeshift functionality.

---

# 29. Logging

Use structured logs.

Example:

```text
PLAYBACK_RESOLVE
channel=stvr1
provider=stvr

PLAYBACK_START
channel=stvr1
protocol=hls

PLAYBACK_ERROR
channel=stvr1
error=HTTP_403
```

Never log:

```text
Authorization headers
cookies
DRM tokens
signed URL query secrets
passwords
provider secrets
```

---

# 30. Security Rules

```text
HTTPS only where supported

NO secrets committed to repository

NO credentials in APK

NO DRM bypass

NO subscription bypass

NO hidden provider-token logging

NO video proxy by default
```

If legitimate OAuth is needed:

```text
Tellyo
  ↓
OAuth
  ↓
Provider
```

If a provider requires a server-side secret:

```text
Tellyo
  ↓
Tellyo backend
  ↓
Provider
```

Never ship the secret in the APK.

---

# 31. Timeshift Strategy

Timeshift is not part of MVP.

## Level 1 — provider/native DVR window

First support rewind/seek inside the live window already exposed by HLS/DASH.

Example:

```text
LIVE
-5 min
-10 min
LIVE
```

---

## Level 2 — local timeshift engine

Later:

```text
HLS stream
    ↓
PlaylistPoller
    ↓
SegmentDownloader
    ↓
Local Ring Buffer
    ↓
Local Playlist Generator
    ↓
ExoPlayer
```

Suggested components:

```text
TimeshiftEngine
SegmentStore
RingBuffer
PlaylistGenerator
TimeshiftIndex
```

---

# 32. Local Ring Buffer

Possible initial limit:

```text
2–4 GB
```

Configuration:

```text
Off
30 minutes
60 minutes
120 minutes
```

Possible index schema:

```text
segment_id
channel_id
start_time
end_time
file
size
```

Oldest segments are deleted when the configured capacity is reached.

---

# 33. Commercial Skipping

Commercial skipping must not be implemented before stable timeshift playback.

## Version 1

Manual skip:

```text
RIGHT
+30 seconds

long RIGHT
+2 minutes
```

---

## Version 2

Optional commercial markers.

Possible signals:

- channel-logo changes
- black-frame transitions
- scene-change rate
- audio loudness changes
- timing history
- metadata markers when available

Example model:

```kotlin
data class CommercialBlock(
    val start: Duration,
    val end: Duration,
    val confidence: Float
)
```

Automatic detection must remain:

```text
Experimental
OFF by default
```

False positives must never make the player unusable.

---

# 34. Development Phases

Codex must work in small, testable phases.

Do not implement the entire application in one giant task.

---

## Phase 0 — Project scaffold

Create:

```text
Android TV project
Kotlin
Compose for TV
Media3
Hilt
Room
DataStore
OkHttp
WorkManager
tests
```

Acceptance:

- project builds
- app installs
- app launches
- TV remote focus works
- base Tellyo theme is implemented
- logo placeholder uses approved Tellyo direction

---

## Phase 1 — Design system and shell

Before implementing complex business logic, build the approved visual foundation.

Implement:

```text
Tellyo colors
Tellyo typography
Tellyo focus modifier
Tellyo cards
sidebar
section titles
buttons
progress bars
badges
```

Acceptance:

- design visually matches the approved Hybrid TV UI
- no default Material theme remains visible
- blue/white/navy palette is consistently applied
- focus states are clear from TV distance

---

## Phase 2 — Player proof of concept

Implement:

```text
one legal/public test HLS stream
        ↓
Media3 / ExoPlayer
        ↓
fullscreen TV playback
```

Acceptance:

- playback starts
- pause/resume works
- buffering state works
- errors display properly
- app survives temporary network interruption

---

## Phase 3 — Provider abstraction

Implement:

```text
TvProvider
StreamResolution
ProviderRegistry
ChannelRepository
FakeProvider
```

Acceptance:

- UI contains no hardcoded stream URLs
- UI does not know provider implementation details

---

## Phase 4 — Approved Home UI

Implement the selected design:

```text
left sidebar
hero/live preview
channel-switch overlay
favorite channel row
EPG preview
```

Acceptance:

- visual hierarchy matches approved mockup direction
- 1080p layout works
- D-pad focus transitions are smooth
- focused state uses the approved electric-blue treatment
- Tellyo logo appears correctly

---

## Phase 5 — First real provider

Start with only one provider.

Example:

```text
provider-stvr
```

Only use an official or legitimately accessible playback mechanism.

If direct playback is not available:

```text
ExternalApp
```

Do not attempt to bypass restrictions.

---

## Phase 6 — Full EPG

Implement:

```text
EpgProvider
XmlTvParser
Room
EpgRepository
WorkManager
Now / Next
full TV guide
current-time marker
program detail
```

---

## Phase 7 — Reliability

Implement:

```text
retry policy
network observer
source health checks
stream refresh
diagnostics
resume handling
sleep/wake handling
```

---

## Phase 8 — Polish

Implement:

```text
channel logos
favorites
channel ordering
player overlay
audio tracks
subtitles
quality selection
animations
splash screen
launcher branding
```

Ensure all polish follows the Tellyo design system.

---

## Phase 9 — Native DVR / live window

Support provider-exposed live seeking where available.

---

## Phase 10 — Local Timeshift

Implement:

```text
TimeshiftEngine
SegmentStore
RingBuffer
PlaylistGenerator
TimeshiftPlayer
```

Acceptance:

- pause live TV
- resume
- rewind
- seek forward
- return to live edge

---

## Phase 11 — Manual Ad Skip

Implement configurable jump controls.

---

## Phase 12 — Experimental Automatic Commercial Detection

Only after the timeshift engine is stable.

Must be optional and disabled by default.

---

# 35. UI Fidelity Requirements for Codex

The selected mockups are not merely inspiration.

They define the intended product direction.

Codex must preserve:

- dark cinematic navy background
- white typography
- electric blue accents
- compact horizontal Tellyo logo
- left-side navigation
- large live-preview hero area
- horizontal favorite channel cards
- structured EPG at the bottom
- premium blue focus glow
- floating quick-channel switcher
- rounded modern surfaces
- high readability from TV distance

Codex must avoid:

- generic Material cards
- purple Material defaults
- bottom navigation
- huge mobile-style buttons
- excessive shadows
- random glass panels
- dense IPTV tables
- large unused margins
- inconsistent spacing
- tiny text
- weak focus states

Whenever there is a conflict between generic Android design defaults and the approved Tellyo visual direction, follow the Tellyo visual direction.

---

# 36. Responsive TV Layout

Primary target:

```text
1920 × 1080
```

Also validate:

```text
1280 × 720
3840 × 2160
```

Use density-independent dimensions, not hardcoded pixel coordinates.

Respect Android TV overscan-safe margins.

---

# 37. Performance Targets

Target experience:

```text
Cold start:              as fast as reasonably possible
Channel switching:       minimal overhead beyond provider/player latency
Navigation:              60 fps target
Focus animation:         smooth, 150–220 ms
EPG scrolling:           no visible jank
Memory usage:            suitable for Mi Box-class hardware
```

Avoid heavyweight background visual effects.

Do not run expensive blur shaders continuously on low-power TV hardware.

---

# 38. Testing

## Unit tests

```text
ChannelRepositoryTest
EpgParserTest
ProviderTest
FavoritesRepositoryTest
StreamResolverTest
TimeshiftIndexTest
```

## Integration tests

```text
provider → resolve → MediaItem
EPG → parser → database
database → ViewModel → UI
```

## Device tests

At minimum:

```text
Android TV emulator
real Xiaomi Mi Box
```

Test:

```text
cold start
Wi-Fi disconnect
network recovery
stream timeout
HTTP errors
broken EPG
rapid channel switching
sleep / resume
Home → app
Back handling
D-pad focus
1080p rendering
720p rendering
```

---

# 39. CI

Recommended GitHub Actions pipeline:

```text
push / pull request
       │
       ├── formatting / ktlint
       ├── compile
       ├── unit tests
       ├── Android lint
       └── debug APK
```

Release:

```text
tag
 ↓
release build
 ↓
signed APK
 ↓
GitHub Release
 ↓
Mi Box sideload
```

Google Play publication is not required for personal use.

---

# 40. Branch Strategy

Recommended:

```text
main
 │
 ├── feat/design-system
 ├── feat/player
 ├── feat/providers
 ├── feat/epg
 ├── feat/diagnostics
 ├── feat/timeshift
 └── feat/ad-detection
```

Keep changes narrow and reviewable.

---

# 41. What Codex Must NOT Build During MVP

Do not add:

```text
backend
Docker
Kubernetes
cloud user accounts
cloud database
AI commercial detection
local timeshift
DRM bypass
web scraper
IPTV video proxy
analytics SDK
advertising SDK
```

unless explicitly requested later.

---

# 42. MVP Definition

The first production-quality MVP contains:

```text
✓ Tellyo branding
✓ approved dark-blue / white visual design
✓ Android TV D-pad navigation
✓ sidebar
✓ channel list
✓ hero live-preview area
✓ Media3 playback
✓ HLS
✓ DASH
✓ provider interface
✓ 2–3 legitimate test/live channels
✓ EPG
✓ favorites
✓ channel switching
✓ diagnostics
✓ error handling
✓ stable sleep/resume behavior
```

Not required for MVP:

```text
timeshift
commercial detection
backend
accounts
cloud sync
```

---

# 43. Final Target Architecture

```text
                         ┌────────────────────────┐
                         │ optional config server │
                         │                        │
                         │ channels.json          │
                         │ EPG                    │
                         └───────────┬────────────┘
                                     │
                                     │ HTTPS
                                     ▼
┌─────────────────────────────────────────────────────┐
│                     MI BOX                          │
│                                                     │
│  ┌─────────────┐     ┌─────────────────────────┐   │
│  │ Tellyo UI   │────▶│ ChannelRepository       │   │
│  │ Compose TV  │     └────────────┬────────────┘   │
│  └─────────────┘                  │                 │
│                       ┌───────────┴───────────┐     │
│                       │ ProviderRegistry       │     │
│                       └───────────┬───────────┘     │
│                                   │                 │
│  ┌───────────────┐     ┌─────────▼────────────┐    │
│  │ EPG / Room    │     │ PlaybackService      │    │
│  └───────────────┘     │ Media3 / ExoPlayer   │    │
│                        └─────────┬─────────────┘    │
│                                  │                  │
│                   ┌──────────────┴──────────────┐   │
│                   │ optional TimeshiftEngine    │   │
│                   │ local HLS ring buffer       │   │
│                   └─────────────────────────────┘   │
└────────────────────────────┬────────────────────────┘
                             │
                             │ HLS / DASH directly
                             ▼
                 ┌────────────────────────┐
                 │ broadcasters / CDNs    │
                 └────────────────────────┘
```

---

# 44. Codex Execution Rules

Codex should follow these rules throughout implementation:

1. Do not attempt to implement all phases at once.
2. Complete one phase and verify acceptance criteria before moving forward.
3. Keep the project compiling after every meaningful change.
4. Prefer straightforward implementations over premature abstraction.
5. Do not introduce backend infrastructure unless explicitly requested.
6. Do not bypass provider access controls.
7. Treat the Tellyo visual system in this document as mandatory.
8. Use reusable design tokens instead of hardcoding visual values across screens.
9. Keep UI logic separate from provider logic.
10. Keep playback lifecycle in a central service.
11. Add tests whenever a new repository/parser/provider becomes non-trivial.
12. Prioritize real Mi Box behavior over emulator-only assumptions.
13. If a visual implementation differs materially from the approved Tellyo design, fix the visual implementation rather than redefining the design.
14. Avoid unnecessary dependency additions.
15. Prefer stable AndroidX APIs.

---

# 45. Recommended First Codex Task

The first implementation task should be:

> Create the Tellyo Android TV project scaffold using Kotlin, Compose for TV, Media3, Hilt, Room, DataStore, OkHttp, WorkManager, Coroutines/Flow, and the approved Tellyo design system. Implement the application shell with the Tellyo logo, dark navy background, blue/white theme, D-pad focus behavior, left navigation sidebar, placeholder hero panel, placeholder favorite-channel cards, and placeholder EPG grid. Do not implement real TV providers, backend infrastructure, timeshift, DRM logic, or commercial detection yet. The goal is to make the approved Tellyo UI shell compile, run, and navigate correctly on an Android TV emulator and a Mi Box.

Acceptance criteria:

```text
✓ builds successfully
✓ installs on Android TV
✓ D-pad navigation works
✓ Tellyo logo is visible
✓ visual theme matches this specification
✓ focused elements clearly glow blue
✓ sidebar works
✓ hero panel exists
✓ favorite channel cards exist
✓ placeholder EPG exists
✓ no real provider logic yet
```

---

# 46. Source-of-Truth Priority

When decisions conflict, use this priority:

1. **Security / legal access rules**
2. **This implementation plan**
3. **Approved Tellyo visual direction**
4. **Existing architecture decisions**
5. **Android TV best practices**
6. **Generic framework defaults**

The final application should look and behave like **Tellyo**, not like a default Compose sample.

<div align="center">

# <img src="image/Logo.png" width="50" height="50" alt="VRCM logo"/> VRCM

**Languages / 语言 / 言語:**<br>
[English](README.md) · [中文](README_ZH.md) · [日本語](README_JP.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat&labelColor=6e6e73)](https://opensource.org/licenses/MIT)
[![GitHub release](https://img.shields.io/github/release/vrcm-team/VRCM.svg?style=flat&labelColor=6e6e73)](https://github.com/vrcm-team/VRCM/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/vrcm-team/VRCM/total?style=flat&labelColor=6e6e73&color=6451f1)](https://github.com/vrcm-team/VRCM/releases/latest)
[![Android](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/vrcm-team/VRCM/main/badge-data/android-installer-size.json&style=flat)](https://github.com/vrcm-team/VRCM/releases/latest)
[![iOS](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/vrcm-team/VRCM/main/badge-data/ios-installer-size.json&style=flat)](https://github.com/vrcm-team/VRCM/releases/latest)
[![MacOS](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/vrcm-team/VRCM/main/badge-data/desktop-installer-size.json&style=flat)](https://github.com/vrcm-team/VRCM/releases/latest)
[![Windows](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/vrcm-team/VRCM/main/badge-data/windows-installer-size.json&style=flat)](https://github.com/vrcm-team/VRCM/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-blue.svg?style=flat&labelColor=6e6e73&logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.10.3-blue?style=flat&labelColor=6e6e73)](https://www.jetbrains.com/lp/compose-multiplatform/)

## Bring your VRChat social life to your phone

<div align="center">
  <img src="image/MultiPlatformPreview.png" width="720" alt="VRCM cross-platform VRChat social companion preview"/>
</div>

VRCM is a cross-platform VRChat companion focused on social connection and convenience. It goes beyond showing who is online: explore your social circles, remember time spent together, and move smoothly from shared links to worlds, interactions, and real-world meetups.

Compared with tools centered on desktop information management and logs, VRCM emphasizes social data, timely alerts, and actions designed for mobile use. This describes a product focus, not a compatibility or replacement claim for another project.

[Download the latest release](https://github.com/vrcm-team/VRCM/releases/latest) · [What's new in 1.1.1](docs/releases/1.1.1.md)

</div>

## What makes VRCM different

### Social graph and shared history

- **Friend network**: build community and ego-centered views from mutual-friend relationships, inspect connections, zoom, refresh, and reuse locally cached graph data.
- **Friend activity history**: revisit online/offline events, world changes, status updates, and bio changes from a profile.
- **Time together**: see when you last met, how often you met, and how long you played together.
- **Mutual context**: discover mutual friends and mutual groups without leaving the profile flow.

<div align="center">
  <img src="image/Feature-Friend-Network.png" width="300" alt="Privacy-redacted friend network on a phone"/>
</div>

> Activity and time-together data cover only what VRCM observed while running. Android can continue observing in the background when background monitoring is enabled. This is not a complete VRChat account history.

### Mobile shortcuts and in-game interaction

- **Clipboard recognition**: copy a VRChat user, world, group, or avatar URL/ID, return to VRCM, confirm, and jump directly to it.
- **Open web links in VRCM**: Android can hand supported `vrchat.com` links directly to the app.
- **Native sharing**: share public profile links through the Android/iOS share sheet; Desktop falls back to copying the URL.
- **Act immediately**: inspect a friend's instance, invite yourself, send several kinds of Boop, and handle friend requests or invitations.

### Two-way phone gallery and VRChat+ Gallery workflow

- **From phone to VRChat**: select an image from your phone and upload it to VRChat+ Gallery; crop and preview Prints before uploading.
- **From VRChat to phone**: save photos taken in-game and synced to Gallery into the system photo library, ready to share through your gallery or messaging apps.
- **Direct image sharing**: open a Gallery or Print image and send the original image through the native Android/iOS share sheet; sharing does not write another copy to the photo library.
- **Mobile photo management**: browse, zoom, download, and batch-delete Gallery content. Non-VRC+ users can still view Prints.

<div align="center">
  <img src="image/Feature-Gallery-Mobile.png" width="360" alt="VRChat+ Gallery on an Android phone, showing photo categories and the upload action"/>
</div>

### Android real-time alerts

- Filter online/offline alerts by favorite group with allow/deny modes and per-friend overrides.
- Receive Boop, friend-request, group event, and VRChat service-status alerts.
- Keep optional background monitoring active and jump to notification or battery-management settings when needed.
- Review and act on events from the dedicated in-app notification center.

<div align="center">
  <img src="image/Feature-Android-Notifications.png" width="300" alt="Android notification and background-monitoring settings"/>
</div>

### Meetup name card

- Long-press your avatar on Home to open a full-screen card designed to be held up at an in-person meetup.
- Choose Info Bar, Spotlight, or Side Tag templates with independent portrait and landscape layouts.
- Use your profile background, a local photo, or VRChat Gallery, and include status, languages, group identity, and profile effects.
- Add up to four QR codes for your VRChat profile and profile social links, then save the card to the system gallery.

<div align="center">
  <img src="image/Feature-Meetup-Card.png" width="300" alt="Full-screen meetup name card on a phone"/>
</div>

## More capabilities

- **Profile and content**: edit status, bio, languages, pronouns, and social links; browse created worlds, avatars, and favorite worlds.
- **Worlds and groups**: search worlds and groups; inspect instances, recently visited worlds, group posts, members, galleries, and group instances.
- **Avatar management**: view avatar details, switch or copy available avatars, and edit the name, description, and cover of avatars you uploaded.
- **Accounts and UI**: multiple accounts, email/2FA authentication, four UI languages, themes, shared transitions, and adaptive widescreen layouts.

## Platform support

| Platform | Status | Notes |
| --- | --- | --- |
| Android | Full | Includes native alerts, background friend monitoring, and VRChat web-link handling |
| iOS | Supported | Requires [self-signing](self-signing.md); Android background system alerts are not available |
| Desktop | Supported | Native Windows, macOS, and Linux packages; sharing falls back to copying links |

## Technology

- Kotlin Multiplatform 2.2.20 and Compose Multiplatform 1.10.3
- Ktor, kotlinx.serialization, Room, and Coil
- Koin, Lifecycle ViewModel, Navigation 3, and Material 3 Adaptive
- Android minSdk 24, targetSdk 35, compileSdk 36; Java 21

## Privacy and disclaimer

- Friend activity, caches, and meetup-card configuration remain on the local device. See the [privacy policy](privacy-policy.md).
- VRCM is not affiliated with VRChat Inc. and does not represent its views or opinions.
- VRCM does not modify the game client. Use it responsibly and follow the [VRChat Terms of Service](https://hello.vrchat.com/legal) and applicable laws.
- The authors are not responsible for damage caused by using this application.

## License and contributing

VRCM is open source under the [MIT License](LICENSE). Code contributions, bug reports, and feature proposals are welcome.

<div align="center">

[Report an issue](https://github.com/vrcm-team/VRCM/issues) · [Suggest a feature](https://github.com/vrcm-team/VRCM/discussions)

</div>

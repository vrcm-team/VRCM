<div align="center">

# <img src="image/Logo.png" width="50" height="50"  alt="logo"/> VRCM

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![GitHub release](https://img.shields.io/github/release/vrcm-team/VRCM.svg)](https://github.com/vrcm-team/VRCM/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/vrcm-team/VRCM/total?color=6451f1)](https://github.com/vrcm-team/VRCM/releases/latest)


## A Multi-Platform friend "monitor" for VRChat.

</div>
<div align="center">

## Features

</div>

- Multi-platform support (Android, iOS, Desktop?)
- Change Account
- Friend Location
- Friend List
- User Profile
- User Search
- Notification actions( friend request, mark as read, hide...)
- Settings(i8n, theme...)
- Gallery(VRChat +)
  
  <img src="image/Gallery-1.png" width="201" height="437"  alt="Gallery-1"/>
  <img src="image/Gallery-2.png" width="201" height="437"  alt="Gallery-2"/>
<div align="center">

## Preview

</div>

### Multi-platform Preview:

<div align="center">

![MultiPlatformPreview.png](image/MultiPlatformPreview.png)

</div>

### UI Preview:

<div align="center">

![UIPreview.png](image/UIPreview.png)

</div>

<div align="center">

## TODO

</div>

- Friend Profile actions(add friend, block...)
- World Profile UI(world name, description, tags...) and actions(favorite...)
- Group Profile UI(group name, description, tags...) and actions(favorite...)
- Notifications UI and actions(mark as read, delete...)
- Support for System Notifications

<div align="center">

## Disclaimer

</div>

VRCM is not affiliated with VRChat Inc and does not represent the views or opinions of VRChat Inc.
VRCM does not store or collect any data outside the confines of your device. 
The author of this application is not responsible for any damages caused by this application.
VRCM does not modify or tamper with the game and does not violate [VRChat's Terms of Service.](https://hello.vrchat.com/legal).

<div align="center">

## Dependencies

</div>

The app uses the following multiplatform dependencies in its implementation:
- [Ktor](https://ktor.io/) for networking
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for JSON handling
- [Koin](https://github.com/InsertKoinIO/koin) for dependency injection
- [Voyager](https://github.com/adrielcafe/voyager) for shared Model implementations in common code and Navigation
- [Sultiplatform-Settings](https://github.com/russhwolf/multiplatform-settings) for Key-Value preferences
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Coil](https://github.com/coil-kt/coil) for image loading
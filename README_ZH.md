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

## 把 VRChat 社交带到手机上

<div align="center">
  <img src="image/MultiPlatformPreview.png" width="720" alt="VRCM 跨平台 VRChat 社交伴侣界面预览"/>
</div>

VRCM 是以社交性和便捷性为核心的跨平台 VRChat 伴侣应用。它不只展示谁在线，还帮助你理解社交圈、回顾和好友一起玩过的经历，并把分享链接、加入房间、戳一戳和线下聚会等操作接到手机上的真实使用场景里。

与更偏桌面端信息管理和日志查看的工具相比，VRCM 的重点是移动场景中的社交数据、及时提醒和可直接执行的互动流程；这是一种产品侧重点差异，并不是对其他项目的兼容或替代承诺。

[下载最新版本](https://github.com/vrcm-team/VRCM/releases/latest) · [查看 1.1.1 新功能详解](docs/releases/1.1.1_ZH.md)

</div>

## 核心特色

### 社交关系与相处数据

- **好友关系网**：根据共同好友关系生成社区视图和“以我为中心”视图，可缩放、查看关联、刷新并复用本地缓存。
- **好友活动记录**：在资料页回顾上线、下线、世界切换、状态与简介变化。
- **共同游玩统计**：记录最后见面、见面次数和共同游玩时长，让好友列表不只是一排在线状态。
- **共同好友与共同群组**：从用户资料继续探索彼此的社交联系。

<div align="center">
  <img src="image/Feature-Friend-Network.png" width="300" alt="脱敏后的好友关系网实机图"/>
</div>

> 活动与共同游玩数据来自 VRCM 在运行期间观察到的状态；Android 开启后台监测后可在应用退到后台时继续记录。它不是 VRChat 账户的完整历史。

### 移动端便捷跳转与游戏互动

- **剪贴板识别**：复制 VRChat 用户、世界、群组或模型的官网链接/ID，回到 VRCM 确认后即可直达对应页面。
- **系统链接打开**：Android 可将受支持的 `vrchat.com` 链接直接交给 VRCM。
- **一键分享**：在 Android/iOS 使用系统分享资料链接，桌面端可复制官网链接。
- **快速加入与互动**：查看好友房间、邀请自己加入、向好友发送多种 Boop，并处理好友请求与邀请。

### 手机相册与 VRChat+ Gallery 双向互通

- **从手机传进游戏**：直接选择手机相册中的图片上传到 VRChat+ Gallery；Print 上传前可裁剪并预览构图。
- **从游戏带回手机**：把游戏内拍摄并同步到 Gallery 的照片保存到系统相册，随后可用相册或聊天应用快速分享。
- **预览页直接分享**：打开 Gallery 或 Print 图片后，可通过 Android/iOS 系统分享面板发送原图；分享不会再写入一份系统相册副本。
- **移动端照片管理**：浏览、缩放、下载和批量删除 Gallery 内容；非 VRC+ 用户也可查看 Print。

<div align="center">
  <img src="image/Feature-Gallery-Mobile.png" width="360" alt="展示照片分类与上传入口的 Android 实机 Gallery 画面"/>
</div>

### Android 实时提醒

- 好友上线/下线提醒可按收藏分组设置黑白名单，并对单个好友覆盖规则。
- 支持 Boop、好友请求、群组公告/活动/管理消息和 VRChat 服务状态提醒。
- 可选后台监测，并提供通知权限与系统耗电管理入口。
- 独立通知中心集中查看和处理应用内通知。

<div align="center">
  <img src="image/Feature-Android-Notifications.png" width="300" alt="Android 通知与后台监测设置实机图"/>
</div>

### 线下聚会身份铭牌

- 长按首页头像即可进入适合线下聚会举屏展示的全屏身份铭牌。
- 支持资料栏、聚光和侧签三种模板，以及竖屏/横屏独立布局。
- 照片可来自资料背景、手机相册或 VRChat Gallery；可展示状态、语言、群组和资料特效。
- 可加入 VRChat 个人主页与资料社交链接二维码，最多同时展示 4 个，并可保存到系统图库。

<div align="center">
  <img src="image/Feature-Meetup-Card.png" width="300" alt="线下聚会身份铭牌实机图"/>
</div>

## 更多能力

- **资料与内容**：编辑状态、简介、语言、人称代词和社交链接；查看用户创建的世界、模型与收藏世界。
- **世界与群组**：搜索世界和群组，查看世界房间、最近访问世界、群组帖子、成员、相册与群组房间。
- **模型管理**：查看模型详情、切换或复制可用模型，并编辑自己上传模型的名称、简介和封面。
- **账户与界面**：多账户、邮箱/2FA 登录、多语言、多主题、共享元素动画和宽屏自适应布局。

## 平台支持

| 平台 | 支持情况 | 说明 |
| --- | --- | --- |
| Android | 完整支持 | 包含原生系统提醒、后台好友监测和 VRChat 官网链接接管 |
| iOS | 支持 | 需要[自签](self-signing.md)；不包含 Android 后台系统提醒 |
| Desktop | 支持 | Windows、macOS、Linux 原生发行包；系统分享回退为复制链接 |

## 技术架构

- Kotlin Multiplatform 2.2.20 与 Compose Multiplatform 1.10.3
- Ktor、kotlinx.serialization、Room 与 Coil
- Koin、Lifecycle ViewModel、Navigation 3 与 Material 3 Adaptive
- Android minSdk 24、targetSdk 35、compileSdk 36；Java 21

## 隐私与免责声明

- 好友活动、缓存和身份铭牌配置保存在本地设备；详见[隐私政策](privacy-policy.md)。
- VRCM 与 VRChat Inc. 无关联，也不代表 VRChat Inc. 的观点或意见。
- VRCM 不修改游戏客户端。请合理使用本应用，并遵守 [VRChat 服务条款](https://hello.vrchat.com/legal)及当地法律法规。
- 应用作者不对使用本应用造成的损害负责。

## 许可证与贡献

本项目基于 [MIT 许可证](LICENSE)开源。欢迎提交代码、报告问题或提出功能建议。

<div align="center">

[反馈问题](https://github.com/vrcm-team/VRCM/issues) · [功能建议](https://github.com/vrcm-team/VRCM/discussions)

</div>

<div align="center">

# <img src="image/Logo.png" width="50" height="50"  alt="logo"/> VRCM

<!-- Language Selection -->
**🌐 Languages / 语言 / 言語:**  
[English](README.md) • [中文](README_ZH.md) • [日本語](README_JP.md)


[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![GitHub release](https://img.shields.io/github/release/vrcm-team/VRCM.svg)](https://github.com/vrcm-team/VRCM/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/vrcm-team/VRCM/total?color=6451f1)](https://github.com/vrcm-team/VRCM/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.8.2-blue)](https://www.jetbrains.com/lp/compose-multiplatform/)

## 多平台 VRChat 好友"视奸"应用

一个功能丰富的跨平台 VRChat 好友管理应用，让您随时随地掌握好友动态。

</div>

<div align="center">

## ✨ 核心功能

</div>

### 🔐 账户管理
- **多账户支持** - 快速切换不同的 VRChat 账户
- **登录认证** - 支持邮箱、2FA登陆验证

### 👥 好友系统
- **好友列表** - 实时查看所有好友的在线状态和活动信息
- **好友位置** - 追踪好友当前所在的世界和房间
- **好友资料** - 查看详细的用户信息、状态和BIO等
- **好友管理** - 添加新好友、删除好友等完整操作

### 🔍 搜索功能
- **用户搜索** - 通过用户名快速查找 VRChat 用户
- **世界搜索** - 发现和搜索 VRChat 中的各种世界

### 🌍 世界功能
- **世界详情** - 查看世界的详细信息、描述、标签和预览图
- **世界收藏** - 收藏喜欢的世界，支持多个收藏组管理
- **世界浏览** - 浏览热门和推荐世界
- **房间邀请** - 可以邀请自己进入房间

### 🔔 通知系统
- **实时通知** - 接收好友请求、邀请、群组通知等各类通知
- **通知管理** - 按时间排序显示，支持标记已读和删除操作
- **好友请求** - 处理好友请求，接受或拒绝邀请

### 🎨 界面体验
- **现代化设计** - 遵循 Material Design 设计规范
- **多主题支持** - 深色/浅色与各种配色主题切换
- **国际化** - 支持多种语言界面
- **流畅动画** - 共享元素过渡和精美的交互动画

### 🖼️ VRChat+ 画廊
- **照片浏览** - 查看游戏内拍摄的所有照片
- **照片下载** - 将喜欢的照片保存到本地设备
- **缩放预览** - 支持照片的缩放和详细查看

  <img src="image/Gallery-1.png" width="201" height="437"  alt="Gallery-1"/>
  <img src="image/Gallery-2.png" width="201" height="437"  alt="Gallery-2"/>

<div align="center">

## 📱 平台支持

</div>

- ✅ **Android** - 完整功能支持
- ✅ **iOS** - 完整功能支持(需要[自签](self-signing.md))

<div align="center">

## 🖥️ 界面预览

</div>

### 多平台预览:

<div align="center">

![MultiPlatformPreview.png](image/MultiPlatformPreview.png)

</div>

### UI 界面预览:

<div align="center">

![UIPreview.png](image/UIPreview.png)

</div>

<div align="center">

## 📋 开发路线图

</div>

### 即将推出:
- 📷 **画廊上传功能** - 支持从本地设备通过相册或拍照上传图片到 VRChat+ 画廊中
- 👤 **用户资料编辑** - 支持用户修改自己的个人简介(bio)、头像等资料信息
- 👥 **群组功能** - 群组资料查看、群组房间查看等完整功能支持

### 未来可期?
- 📱 **宽屏适配** - 平板电脑和折叠屏设备的完美适配，支持双屏布局和多窗口操作
- 🖥️ **完整桌面端** - Windows、macOS、Linux 全平台支持
- 📊 **活动历史记录** - 后台持久化记录好友活动历史，支持长期数据存储和查询
- 📢 **系统通知** - 原生系统通知支持
- 🤖 **智能助手** - AI 驱动的好友活动分析

<div align="center">

## 🛠️ 技术架构

</div>

### 核心技术栈
- **[Kotlin Multiplatform](https://kotlinlang.org/multiplatform/)** - 跨平台开发框架
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)** - 现代化UI框架
- **[Ktor](https://ktor.io/)** - 网络请求和API通信
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** - JSON数据序列化

### 架构组件
- **[Koin](https://github.com/InsertKoinIO/koin)** - 依赖注入框架
- **[Voyager](https://github.com/adrielcafe/voyager)** - 导航和状态管理
- **[Multiplatform-Settings](https://github.com/russhwolf/multiplatform-settings)** - 跨平台配置存储
- **[Coil](https://github.com/coil-kt/coil)** - 高性能图片加载

### 开发环境
- **Kotlin API**: 2.1
- **Android SDK Target**: 35
- **Java SDK**: 21
- **Compose**: 1.8.2

<div align="center">

## ⚠️ 免责声明

</div>

- VRCM 与 VRChat Inc 无关联，不代表 VRChat Inc 的观点或意见
- VRCM 不会在您的设备之外存储或收集任何数据
- 应用作者不对此应用造成的任何损害负责
- VRCM 不修改或篡改游戏，不违反 [VRChat 服务条款](https://hello.vrchat.com/legal)
- 请合理使用此应用，遵守相关法律法规和平台规定

<div align="center">

## 📄 许可证

</div>

本项目基于 [MIT 许可证](LICENSE) 开源。

<div align="center">

## 🤝 贡献

</div>

欢迎贡献代码、报告问题或提出功能建议！请查看我们的贡献指南了解更多信息。

---

<div align="center">

**如果这个项目对您有帮助，请给我们一个 ⭐**

[下载最新版本](https://github.com/vrcm-team/VRCM/releases/latest) • [反馈问题](https://github.com/vrcm-team/VRCM/issues) • [功能建议](https://github.com/vrcm-team/VRCM/discussions)

</div>



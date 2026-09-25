# VRCM 产品介绍页

面向用户的单页介绍，线上地址：https://vrcm-team.github.io/VRCM/ （由 `.github/workflows/pages.yml` 在 `main` 分支改动本目录时自动部署）。

```
docs/介绍页/
  index.html          页面本体（英文底稿 + 样式 + 脚本），双击即可打开，无需构建
  i18n.js             简体中文 / 日文译文；按浏览器语言自动选择，顶栏可切换，也可用 ?lang=en|zh|ja 指定
  img/icon.svg        品牌图标（与 iosApp/iosApp/AppIcon.icon 同几何，自带深色外观）
  img/og.png          社交平台分享卡片（1200×630）
  img/screens/*.webp  截图：手机为宽 720（iPhone 17 Pro 模拟器、vivo 折叠屏外屏），desktop-*.webp 为宽 1400 的桌面端整窗
```

首屏是三端首页：桌面端宽屏窗口（竖排标签栏 + 首页列表 + 右侧详情栏）在后，Android 与 iPhone 叠在右下角，深浅色各一套。它和仓库根目录的 README 主图 `image/MultiPlatformPreview.png` 是同一构图，换截图时两处一起换。

## 视觉约定

页面沿用 App 的 Apple HIG 设计系统：颜色、主题色、圆角与字阶与 `composeApp/src/commonMain/kotlin/presentation/designsystem`（`Palette.kt`、`Tokens.kt`）同值；图标是 `presentation/supports/AppIcons.kt` 的路径逐一翻译成的 SVG（`ICONS:BEGIN` / `ICONS:END` 之间），改 App 图标时这里一起改。

「外观」一节的语言、主题模式、六色主题色和 App 设置页是同一套选项，切换后作用于整页并记在浏览器本地。

## 改文案

- 英文直接改 `index.html` 里带 `data-i18n="键名"` 的元素；中文、日文改 `i18n.js` 里同名的键（图片 alt 用 `alt:` 前缀，aria-label 用 `aria:` 前缀）。
- 新增文字时给元素加 `data-i18n`，并在 `i18n.js` 两种语言里都补上，漏掉的键会回退显示英文。
- 与 README 重复的段落（功能说明、平台支持、隐私与免责声明）沿用 `README.md` / `README_ZH.md` / `README_JP.md` 的措辞，改 README 时这里也要同步。

## 下载按钮

页面加载时读取 GitHub API 的最新 Release，按访客平台把首屏按钮换成对应安装包（Android → `.apk`，macOS → `.dmg`，Windows → `.exe` / `.msi`，Linux → `.deb`；iOS 需要自签，跳到下载区看说明）。接口失败或被限流时，按钮保持指向 Releases 页。

## 更换截图

截图里其他玩家的名字、头像已打码，Android 状态栏已换成干净的 9:41。替换时：

1. 英文界面下截图：iPhone 用 `xcrun simctl io <UDID> screenshot`；Android 用 `adb exec-out screencap -d <display id> -p`。
2. 桌面端不需要屏幕录制权限：在 `composeApp/src/desktopTest` 里临时写一个测试，`startKoin` 装上正式的 `commonModules + platformModule`（读本机桌面端的登录数据），`runDesktopComposeUiTest(2200, 1520)` 里用 `LocalDensity provides Density(2f)` 渲染 `App(windowChrome = { DesktopWindowTitleBar(...) })`，得到 1100×760 dp 的 2 倍整窗图；等首页加载后点封面（`WorldImage`）在右侧详情栏打开世界页，再 `captureToImage()` 存 PNG。拍前把设置临时改成英文、指定深浅色、关掉剪贴板读取，拍完恢复原值，并删除这个临时测试。
3. 打码其他玩家的名字和头像，Android 截图抹掉状态栏里的通知图标。
4. 缩放后存为 WebP（手机宽 720、桌面宽 1400，质量 80 左右），文件名沿用 `img/screens/` 里的现有名字。
5. 仓库根目录 `image/` 里的 README 配图（带机身外框的 PNG）用同一批截图生成，两处一起换。

## 本地预览

`.claude/launch.json` 里的 `intro-page` 用 `python3 -m http.server 8765` 服务本目录；或直接双击 `index.html`。

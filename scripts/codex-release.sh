#!/usr/bin/env bash

# Build the release artifacts available on this host and publish them through
# GitHub's REST API. The script intentionally does not commit or push changes.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
APP_CONST_FILE="$ROOT_DIR/composeApp/src/commonMain/kotlin/core/shared/AppConst.kt"
INSTALLER_FILE="$ROOT_DIR/installer/VRCM.iss"
IOS_PROJECT_FILE="$ROOT_DIR/iosApp/iosApp.xcodeproj/project.pbxproj"
IOS_PLIST_FILE="$ROOT_DIR/iosApp/iosApp/Info.plist"
IPA_SCRIPT="$ROOT_DIR/iosApp/package-ipa.sh"
RELEASE_CONTENT_SCHEMA="$SCRIPT_DIR/release-content.schema.json"

REPOSITORY="${GITHUB_REPOSITORY:-vrcm-team/VRCM}"
GITHUB_API="${GITHUB_API_URL:-https://api.github.com}"
VERSION=""
VERSION_CODE=""
PLATFORMS="all"
NOTES_FILE=""
DRY_RUN=0
PUBLISH=0
ALLOW_EXISTING=0
SKIP_VERSION_UPDATE=0
PREVIEW=0
PREVIEW_SET=0

log() { printf '[codex-release] %s\n' "$*"; }
fail() { printf '[codex-release] error: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
用法：
  ./scripts/codex-release.sh <版本> [选项]

示例：
  ./scripts/codex-release.sh 1.2.0
  ./scripts/codex-release.sh 1.2.0 --platforms android,desktop --publish
  ./scripts/codex-release.sh 1.2.0 --preview --publish
  ./scripts/codex-release.sh 1.2.0 --code 7 --notes-file /tmp/release.md --publish

选项：
  --code <整数>              覆盖 Android/iOS 的 version code；省略时仅在版本变化时自动 +1
  --platforms <列表>         all、android、desktop、ios 的逗号列表，默认 all
  --notes-file <文件>        使用指定 Markdown 文案，跳过 Codex 文案与详解生成
  --publish                  上传产物并发布 GitHub Release（自动读取环境变量或本地 GitHub 凭据）
  --preview                 发布为 GitHub 预览版（prerelease），默认发布正式版
  --prerelease              `--preview` 的兼容别名
  --allow-existing           允许更新已有同名 tag 的 Release；默认发现已有 Release 即停止
  --skip-version-update      使用工作区已有版本，不写入版本字段（适合提交版本后再次发布）
  --dry-run                  只生成文案预览，不写版本、不构建、不调用 GitHub 写入 API
  -h, --help                 显示帮助

环境变量：
  GH_TOKEN / GITHUB_TOKEN    GitHub API token，需要 contents:write 权限（可选）
  GITHUB_REPOSITORY          仓库，默认 vrcm-team/VRCM
  ISCC_PATH                  Windows Inno Setup 编译器路径（可选）

本地凭据：
  未设置 GH_TOKEN/GITHUB_TOKEN 时，依次读取 GitHub CLI 和 Git Credential Helper 的本地凭据。
EOF
}

require_command() { command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"; }

read_version() {
  sed -nE 's/^app-version[[:space:]]*=[[:space:]]*"([^"]+)"/\1/p' "$VERSION_FILE" | head -n 1
}

read_version_code() {
  sed -nE 's/^app-code[[:space:]]*=[[:space:]]*"([0-9]+)"/\1/p' "$VERSION_FILE" | head -n 1
}

read_runtime_version() {
  sed -nE 's/^[[:space:]]*const val APP_VERSION[[:space:]]*=[[:space:]]*"([^"]+)"/\1/p' "$APP_CONST_FILE" | head -n 1
}

validate_version() {
  [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] || \
    fail "版本必须符合 SemVer，例如 1.2.0 或 1.2.0-beta.1：$VERSION"
}

parse_platforms() {
  local platform
  case ",$PLATFORMS," in *,all,*) PLATFORMS="android,desktop,ios" ;; esac
  IFS=',' read -r -a PLATFORM_LIST <<< "$PLATFORMS"
  for platform in "${PLATFORM_LIST[@]}"; do
    case "$platform" in
      android|desktop|ios) ;;
      *) fail "不支持的平台：$platform（可选 android、desktop、ios、all）" ;;
    esac
  done
}

has_platform() {
  local platform
  for platform in "${PLATFORM_LIST[@]}"; do [[ "$platform" == "$1" ]] && return 0; done
  return 1
}

has_han_text() {
  printf '%s' "$1" | perl -CSD -e '$text = <>; exit($text =~ /\p{Han}/ ? 0 : 1)'
}

replace_versions() {
  local old_version old_app_version old_code new_code
  old_version="$(read_version)"
  old_app_version="$(read_runtime_version)"
  old_code="$(read_version_code)"
  [[ -n "$old_version" && -n "$old_app_version" && -n "$old_code" ]] || fail "无法读取当前版本字段"

  if [[ -n "$VERSION_CODE" ]]; then
    [[ "$VERSION_CODE" =~ ^[0-9]+$ && "$VERSION_CODE" -gt 0 ]] || fail "--code 必须是正整数"
    new_code="$VERSION_CODE"
  elif [[ "$VERSION" == "$old_version" ]]; then
    new_code="$old_code"
  else
    new_code=$((old_code + 1))
  fi

  OLD_VERSION="$old_version" NEW_VERSION="$VERSION" perl -0pi -e \
    's/^app-version\s*=\s*"\Q$ENV{OLD_VERSION}\E"/app-version = "$ENV{NEW_VERSION}"/m' "$VERSION_FILE"
  OLD_APP_VERSION="$old_app_version" NEW_VERSION="$VERSION" perl -0pi -e \
    's/^(\s*const val APP_VERSION\s*=\s*")\Q$ENV{OLD_APP_VERSION}\E("\s*)$/$1$ENV{NEW_VERSION}$2/m' "$APP_CONST_FILE"
  OLD_CODE="$old_code" NEW_CODE="$new_code" perl -0pi -e \
    's/^app-code\s*=\s*"\Q$ENV{OLD_CODE}\E"/app-code = "$ENV{NEW_CODE}"/m' "$VERSION_FILE"
  OLD_VERSION="$old_version" NEW_VERSION="$VERSION" perl -0pi -e \
    's/^#define AppVersion\s+"\Q$ENV{OLD_VERSION}\E"/#define AppVersion "$ENV{NEW_VERSION}"/m' "$INSTALLER_FILE"
  OLD_VERSION="$old_version" NEW_VERSION="$VERSION" perl -0pi -e \
    's/^(\s*MARKETING_VERSION\s*=\s*)\Q$ENV{OLD_VERSION}\E(\s*;)/$1$ENV{NEW_VERSION}$2/mg' "$IOS_PROJECT_FILE"
  NEW_CODE="$new_code" perl -0pi -e \
    's/^(\s*CURRENT_PROJECT_VERSION\s*=\s*)[0-9]+(\s*;)/$1$ENV{NEW_CODE}$2/mg' "$IOS_PROJECT_FILE"
  OLD_VERSION="$old_version" NEW_VERSION="$VERSION" perl -0pi -e \
    's/(<key>CFBundleShortVersionString<\/key>\s*<string>)\Q$ENV{OLD_VERSION}\E(<\/string>)/$1$ENV{NEW_VERSION}$2/' "$IOS_PLIST_FILE"
  NEW_CODE="$new_code" perl -0pi -e \
    's/(<key>CFBundleVersion<\/key>\s*<string>)[^<]+(<\/string>)/$1$ENV{NEW_CODE}$2/' "$IOS_PLIST_FILE"

  [[ "$(read_version)" == "$VERSION" ]] || fail "Version Catalog 版本同步失败"
  [[ "$(read_runtime_version)" == "$VERSION" ]] || fail "运行时 APP_VERSION 同步失败"
  grep -q "^#define AppVersion \"$VERSION\"" "$INSTALLER_FILE" || fail "Inno Setup 版本同步失败"
  grep -q "MARKETING_VERSION = $VERSION;" "$IOS_PROJECT_FILE" || fail "Xcode 工程版本同步失败"
  grep -q "CURRENT_PROJECT_VERSION = $new_code;" "$IOS_PROJECT_FILE" || fail "Xcode 工程 version code 同步失败"
  grep -A1 -q '<key>CFBundleShortVersionString</key>' "$IOS_PLIST_FILE" || fail "iOS plist 版本字段缺失"
  grep -A1 -q "<string>$VERSION</string>" "$IOS_PLIST_FILE" || fail "iOS plist 版本同步失败"
  grep -A1 -q '<key>CFBundleVersion</key>' "$IOS_PLIST_FILE" || fail "iOS plist version code 字段缺失"
  grep -A1 -q "<string>$new_code</string>" "$IOS_PLIST_FILE" || fail "iOS plist version code 同步失败"
  log "版本已同步：$old_version/$old_app_version/$old_code -> $VERSION/$VERSION/$new_code"
}

run_gradle() {
  chmod +x "$GRADLEW"
  "$GRADLEW" "$@" --console=plain --no-daemon
}

copy_one() {
  local source="$1" target="$2"
  [[ -f "$source" ]] || fail "未找到构建产物：$source"
  cp -f "$source" "$target"
  log "产物：$(basename "$target")"
}

copy_latest_matching() {
  local search_root="$1" pattern="$2" target="$3" candidate
  candidate="$(find "$search_root" -type f -iname "$pattern" -print 2>/dev/null | sort | tail -n 1)"
  [[ -n "$candidate" ]] || fail "在 $search_root 中未找到 $pattern"
  copy_one "$candidate" "$target"
}

build_android() {
  run_gradle :composeApp:assembleRelease
  copy_latest_matching "$ROOT_DIR/composeApp/build/outputs/apk/release" '*.apk' "$ARTIFACT_DIR/VRCM-v$VERSION.apk"
}

find_iscc() {
  if [[ -n "${ISCC_PATH:-}" && -x "$ISCC_PATH" ]]; then printf '%s' "$ISCC_PATH"; return 0; fi
  local candidate
  for candidate in \
    "$ROOT_DIR/.gradle/tools/innosetup/ISCC.exe" \
    '/c/Program Files (x86)/Inno Setup 6/ISCC.exe' \
    '/c/Program Files/Inno Setup 6/ISCC.exe'; do
    [[ -x "$candidate" ]] && { printf '%s' "$candidate"; return 0; }
  done
  return 1
}

build_desktop() {
  run_gradle :composeApp:packageReleaseDistributionForCurrentOS
  local os_name iscc
  os_name="$(uname -s)"
  case "$os_name" in
    Darwin) copy_latest_matching "$ROOT_DIR/composeApp/build/compose/binaries/main-release" '*.dmg' "$ARTIFACT_DIR/VRCM-v$VERSION.dmg" ;;
    Linux) copy_latest_matching "$ROOT_DIR/composeApp/build/compose/binaries/main-release" '*.deb' "$ARTIFACT_DIR/VRCM-v$VERSION.deb" ;;
    MINGW*|MSYS*|CYGWIN*)
      copy_latest_matching "$ROOT_DIR/composeApp/build/compose/binaries/main-release" '*.msi' "$ARTIFACT_DIR/VRCM-v$VERSION.msi"
      if iscc="$(find_iscc)"; then
        "$iscc" "$ROOT_DIR/installer/VRCM.iss"
        copy_one "$ROOT_DIR/composeApp/build/installer/VRCM-v$VERSION-setup.exe" "$ARTIFACT_DIR/VRCM-v$VERSION-setup.exe"
      else
        log "未找到 Inno Setup，跳过 Windows EXE 安装器；可设置 ISCC_PATH 后重试"
      fi
      ;;
    *) fail "无法识别当前系统：$os_name" ;;
  esac
}

build_ios() {
  [[ "$(uname -s)" == "Darwin" ]] || { log "当前不是 macOS，跳过 iOS IPA（需要 macOS + Xcode）"; return 0; }
  require_command xcodebuild
  local archive_dir="$ROOT_DIR/composeApp/build/release/ios/VRCM.xcarchive"
  rm -rf "$archive_dir"
  xcodebuild \
    -project "$ROOT_DIR/iosApp/iosApp.xcodeproj" \
    -scheme iosApp -configuration Release -sdk iphoneos \
    -archivePath "$archive_dir" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO archive
  "$IPA_SCRIPT" "$archive_dir" "$ARTIFACT_DIR/VRCM-v$VERSION.ipa"
}

generate_notes() {
  NOTES_PATH="$RELEASE_DIR/VRCM-v$VERSION-release-notes.md"
  if [[ -n "$NOTES_FILE" ]]; then
    [[ -f "$NOTES_FILE" ]] || fail "文案文件不存在：$NOTES_FILE"
    if [[ "$NOTES_FILE" != "$NOTES_PATH" ]]; then
      cp -f "$NOTES_FILE" "$NOTES_PATH"
    fi
    return 0
  fi
  require_command codex
  require_command curl
  require_command jq

  local base_tag commits previous_release_body artifact_names details_url content_output generated_docs_dir codex_log doc
  base_tag="$(git -C "$ROOT_DIR" describe --tags --abbrev=0 2>/dev/null || true)"
  if [[ "$base_tag" == "$VERSION" ]]; then
    base_tag="$(git -C "$ROOT_DIR" describe --tags --abbrev=0 "${base_tag}^" 2>/dev/null || true)"
  fi
  if [[ -n "$base_tag" ]]; then commits="$(git -C "$ROOT_DIR" log --format='%s' "$base_tag..HEAD")"; else commits="$(git -C "$ROOT_DIR" log -30 --format='%s')"; fi
  previous_release_body=""
  if [[ -n "$base_tag" ]]; then
    previous_release_body="$(curl --fail --silent --show-error \
      "$GITHUB_API/repos/$REPOSITORY/releases/tags/$base_tag" 2>/dev/null | jq -r '.body // empty' || true)"
  fi
  artifact_names="$(find "$ARTIFACT_DIR" -maxdepth 1 -type f -exec basename {} \; | sort)"
  details_url="https://github.com/$REPOSITORY/tree/main/docs/releases/$VERSION.md"
  content_output="$RELEASE_DIR/codex-release-content.json"
  generated_docs_dir="$RELEASE_DIR/docs"
  codex_log="$RELEASE_DIR/codex-release.log"
  mkdir -p "$generated_docs_dir"
  [[ -f "$RELEASE_CONTENT_SCHEMA" ]] || fail "缺少 Codex 结构化输出定义：$RELEASE_CONTENT_SCHEMA"
  rm -f "$content_output"

  log "调用本地 Codex 生成用户可读的双语 Release 文案与三语版本详解"
  if ! codex exec --ephemeral --sandbox read-only --color never \
    --cd "$ROOT_DIR" --output-schema "$RELEASE_CONTENT_SCHEMA" \
    --output-last-message "$content_output" - >"$codex_log" 2>&1 <<EOF
你是 VRCM 的 Release 文案编辑。请为版本 $VERSION 编写 GitHub Release 正文，以及英文、简体中文、日文三份详细更新文档。

请先以只读方式检查仓库中与本版本有关的实际改动（版本范围为 $base_tag..HEAD）、git diff，以及 docs/releases/ 下可用的版本详解。以下提交标题只是线索，不能直接当作文案：

<commits base="$base_tag">
$commits
</commits>

以下是上一版本的 GitHub Release 正文，用它作为篇幅、分组和语气参考，不要复制其具体内容：

<previous_release tag="$base_tag">
$previous_release_body
</previous_release>

当前已经生成、可用于本次 Release 的文件如下。只有文件确实出现在列表中时，才可以写成用户现在能够下载；构建脚本具备某种能力不代表本次 Release 已提供对应文件：

<available_artifacts>
$artifact_names
</available_artifacts>

输出必须符合给定 JSON Schema，四个字段都填写完整 Markdown，不要在 JSON 字段之外附加说明。

GitHub Release 正文（release_body）必须遵守：
1. 固定使用这个顺序：## 新增、## 优化、## 修复、****************、## New Features、## Optimizations、## Bug Fixes。
2. 面向普通用户，优先说明“现在能做什么”“体验有什么改善”“什么问题不再发生”，不要写成 Git 提交清单。
3. 合并同类改动，每个分组保持简洁，通常使用 3 至 6 条；没有可靠依据的内容不要编造。
4. 不要直接照抄提交标题。遇到英文提交标题时必须理解实际改动后，用自然中文归纳到中文区，不能把纯英文句子混入中文区。
5. 避免缓存类型、协程、Cookie 回滚、pipeline、函数名、CI、Shields 等内部实现术语；确有必要时改写成用户能感知的结果。
6. 产品名和平台名（VRCM、VRChat、Gallery、Print、Android、iOS、macOS、Windows、GitHub）可以保留英文，除此之外中文区应使用自然、易读的中文。
7. 英文区必须逐项准确对应中文区，使用自然英文，不能用笼统占位句，也不能遗漏中文要点。
8. 不要把用户无法感知的重构或纯发行维护写成主要功能；只有影响下载、安装、版本识别等用户体验时才可归纳。
9. 最后一行必须原样为：详情 / Details: <$details_url>
10. “支持本地恢复与增量刷新”“旧请求不会覆盖当前视图”“版本产物使用一致元数据”仍然过于技术化。应改写为“打开页面时更快看到已有内容”“切换账号后不会显示上一个账号的收藏”“各平台下载文件的版本号和名称保持一致”等用户结果。
11. 不得把未出现在 available_artifacts 中的安装包写成当前可下载选项；可以省略相关发行维护，或准确说明为尚未提供。

详细更新文档（details_en、details_zh、details_ja）必须遵守：
1. 三份文档分别使用自然的英文、简体中文和日文，不做生硬逐字翻译；内容范围、事实和章节顺序保持一致。
2. 英文标题使用 “# VRCM ${VERSION}: What's New”，中文标题使用“# VRCM ${VERSION}：新功能详解”，日文标题使用“# VRCM ${VERSION}：新機能の詳細”。
3. 标题下提供 English / 中文 / 日本語 三语互链，分别指向 $VERSION.md、${VERSION}_ZH.md、${VERSION}_JP.md。
4. 开头先用短段落说明这次更新对用户最重要的变化，再按真实使用场景组织章节，例如收藏管理、图片上传、更新下载、连接恢复；不要按代码模块或提交顺序罗列。
5. 每个主要章节优先回答：用户从哪里使用、现在可以完成什么、相较以前改善了什么。必要时再说明权限、平台差异、数据范围或失败边界。
6. 使用清晰的小标题、短段落和列表，便于快速浏览。合并重复内容，控制篇幅，不要用大量近义条目堆砌“详尽感”。
7. 内部实现只在直接影响隐私、安全、兼容性或用户预期时说明；避免类名、函数名、提交号、缓存实现、状态机和构建流水线等开发细节。
8. 对尚未支持的平台或有限制的功能明确说明，不夸大能力，不把预览版描述为正式商店发行版。
9. 可以包含简洁的平台差异表和升级提示，但只保留用户作出下载、授权或操作决定所需的信息。
10. 文末提供返回对应语言 README 和下载页的链接，链接必须使用仓库内正确的相对路径。
11. 目标读者是使用 VRCM 的普通用户，不是项目贡献者。不要描述“旧响应被丢弃”“API 行为”“认证状态机”“Compose Desktop”“Inno Setup”等实现或构建过程；改写为页面上能观察到的变化。只有安装包格式或签名限制会影响用户选择时才简要说明。
12. 每个章节的第一段先讲用户收益，再给出操作入口或例子，最后才说明必要限制。平台差异表只比较应用功能，不把打包任务或构建能力当成功能。
EOF
  then
    tail -40 "$codex_log" >&2 || true
    fail "Codex 生成 Release 文案失败；可检查本地 Codex 登录状态，或使用 --notes-file 提供文案"
  fi

  [[ -s "$content_output" ]] || fail "Codex 未生成结构化文案"
  jq -e '.release_body | type == "string" and length > 0' "$content_output" >/dev/null || fail "Codex 未生成 Release 正文"
  jq -e '.details_en | type == "string" and length > 0' "$content_output" >/dev/null || fail "Codex 未生成英文版本详解"
  jq -e '.details_zh | type == "string" and length > 0' "$content_output" >/dev/null || fail "Codex 未生成中文版本详解"
  jq -e '.details_ja | type == "string" and length > 0' "$content_output" >/dev/null || fail "Codex 未生成日文版本详解"
  jq -r '.release_body' "$content_output" > "$NOTES_PATH"
  jq -r '.details_en' "$content_output" > "$generated_docs_dir/$VERSION.md"
  jq -r '.details_zh' "$content_output" > "$generated_docs_dir/${VERSION}_ZH.md"
  jq -r '.details_ja' "$content_output" > "$generated_docs_dir/${VERSION}_JP.md"

  [[ -s "$NOTES_PATH" ]] || fail "Codex 未生成 Release 文案"
  grep -Fxq '## 新增' "$NOTES_PATH" || fail "Codex 文案缺少：## 新增"
  grep -Fxq '## 优化' "$NOTES_PATH" || fail "Codex 文案缺少：## 优化"
  grep -Fxq '## 修复' "$NOTES_PATH" || fail "Codex 文案缺少：## 修复"
  grep -Fxq '****************' "$NOTES_PATH" || fail "Codex 文案缺少中英文分隔线"
  grep -Fxq '## New Features' "$NOTES_PATH" || fail "Codex 文案缺少：## New Features"
  grep -Fxq '## Optimizations' "$NOTES_PATH" || fail "Codex 文案缺少：## Optimizations"
  grep -Fxq '## Bug Fixes' "$NOTES_PATH" || fail "Codex 文案缺少：## Bug Fixes"
  grep -Fxq "详情 / Details: <$details_url>" "$NOTES_PATH" || fail "Codex 文案的 Details 链接不正确"
  ! grep -q '^```' "$NOTES_PATH" || fail "Codex 文案不能包含代码块"
  while IFS= read -r line; do
    case "$line" in
      '- '*) has_han_text "$line" || fail "Codex 在中文区生成了纯英文条目：$line" ;;
    esac
  done < <(awk '$0 == "****************" { exit } { print }' "$NOTES_PATH")
  for doc in "$generated_docs_dir/$VERSION.md" "$generated_docs_dir/${VERSION}_ZH.md" "$generated_docs_dir/${VERSION}_JP.md"; do
    [[ -s "$doc" ]] || fail "Codex 生成的版本详解为空：$doc"
    grep -Fq "${VERSION}_ZH.md" "$doc" || fail "版本详解缺少中文文档链接：$doc"
    grep -Fq "${VERSION}_JP.md" "$doc" || fail "版本详解缺少日文文档链接：$doc"
  done
  if [[ "$DRY_RUN" -eq 0 ]]; then
    copy_one "$generated_docs_dir/$VERSION.md" "$ROOT_DIR/docs/releases/$VERSION.md"
    copy_one "$generated_docs_dir/${VERSION}_ZH.md" "$ROOT_DIR/docs/releases/${VERSION}_ZH.md"
    copy_one "$generated_docs_dir/${VERSION}_JP.md" "$ROOT_DIR/docs/releases/${VERSION}_JP.md"
  fi
  log "Codex Release 文案已生成：$NOTES_PATH"
  log "Codex 版本详解已生成：$generated_docs_dir"
}

api_request() {
  local method="$1" url="$2" body_file="${3:-}" response
  local token="${GITHUB_TOKEN_VALUE:-}"
  [[ -n "$token" ]] || fail "无法获取 GitHub Token；请设置 GH_TOKEN/GITHUB_TOKEN，或先执行 gh auth login"
  if [[ -n "$body_file" ]]; then
    response="$(curl --fail --silent --show-error -X "$method" \
      -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' \
      -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
      --data-binary "@$body_file" "$url")"
  else
    response="$(curl --fail --silent --show-error -X "$method" \
      -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' \
      -H "Authorization: Bearer $token" "$url")"
  fi
  printf '%s' "$response"
}

resolve_github_token() {
  local credential_output token
  if [[ -n "${GH_TOKEN:-}" ]]; then
    printf '%s' "$GH_TOKEN"
    return 0
  fi
  if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    printf '%s' "$GITHUB_TOKEN"
    return 0
  fi
  if command -v gh >/dev/null 2>&1; then
    token="$(gh auth token 2>/dev/null || true)"
    if [[ -n "$token" ]]; then
      printf '%s' "$token"
      return 0
    fi
  fi
  if command -v git >/dev/null 2>&1; then
    credential_output="$(printf 'protocol=https\nhost=github.com\n\n' | GIT_TERMINAL_PROMPT=0 git credential fill 2>/dev/null || true)"
    printf '%s\n' "$credential_output" | sed -n 's/^password=//p' | head -n 1
  fi
}

publish_release() {
  local token="${GITHUB_TOKEN_VALUE:-}" release_json release_id upload_url release_body request_file asset_name asset
  local existing_release=0 publish_request asset_id existing_assets existing_prerelease prerelease_json=false
  [[ "$PUBLISH" -eq 1 ]] || return 0
  [[ "$DRY_RUN" -eq 0 ]] || { log "dry-run：跳过 GitHub API 发布"; return 0; }
  require_command curl; require_command jq
  [[ -n "$token" ]] || fail "--publish 无法获取 GitHub Token；请设置 GH_TOKEN/GITHUB_TOKEN，或先执行 gh auth login"
  [[ "$PREVIEW" -eq 1 ]] && prerelease_json=true
  release_body="$(cat "$NOTES_PATH")"
  request_file="$RELEASE_DIR/release-request.json"
  jq -n --arg tag "$VERSION" --arg name "v$VERSION" --arg body "$release_body" \
    --argjson draft true --argjson prerelease "$prerelease_json" \
    '{tag_name: $tag, target_commitish: "main", name: $name, body: $body, draft: $draft, prerelease: $prerelease, generate_release_notes: false}' \
    > "$request_file"
  release_json="$(curl --silent --show-error -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' -H "Authorization: Bearer $token" \
    "$GITHUB_API/repos/$REPOSITORY/releases/tags/$VERSION" || true)"
  if [[ -n "$release_json" && "$(printf '%s' "$release_json" | jq -r '.id // empty')" != "" ]]; then
    [[ "$ALLOW_EXISTING" -eq 1 ]] || fail "GitHub 已存在 $VERSION Release；如需更新请加 --allow-existing"
    existing_release=1
    release_id="$(printf '%s' "$release_json" | jq -r '.id')"
    if [[ "$PREVIEW_SET" -eq 0 ]]; then
      existing_prerelease="$(printf '%s' "$release_json" | jq -r '.prerelease // false')"
      jq --argjson prerelease "$existing_prerelease" '.draft = false | .prerelease = $prerelease' "$request_file" > "$request_file.tmp"
    else
      jq '.draft = false' "$request_file" > "$request_file.tmp"
    fi
    if [[ "$PREVIEW" -eq 1 ]]; then log "更新已有预览 Release：v$VERSION"; else log "更新已有 Release：v$VERSION"; fi
    mv "$request_file.tmp" "$request_file"
    release_json="$(api_request PATCH "$GITHUB_API/repos/$REPOSITORY/releases/$release_id" "$request_file")"
  else
    if [[ "$PREVIEW" -eq 1 ]]; then log "创建 GitHub 预览 Release：v$VERSION"; else log "创建 GitHub Release：v$VERSION"; fi
    release_json="$(api_request POST "$GITHUB_API/repos/$REPOSITORY/releases" "$request_file")"
    release_id="$(printf '%s' "$release_json" | jq -r '.id')"
  fi
  upload_url="$(printf '%s' "$release_json" | jq -r '.upload_url' | sed 's/{?name,label}$//')"
  [[ -n "$release_id" && "$release_id" != "null" && -n "$upload_url" && "$upload_url" != "null" ]] || fail "GitHub API 返回的 Release 不完整"
  if [[ "$existing_release" -eq 1 ]]; then
    existing_assets="$(api_request GET "$GITHUB_API/repos/$REPOSITORY/releases/$release_id/assets")"
    while IFS= read -r asset_name; do
      [[ -n "$asset_name" ]] || continue
      asset_id="$(printf '%s' "$existing_assets" | jq -r --arg name "$asset_name" '.[] | select(.name == $name) | .id' | head -n 1)"
      if [[ -n "$asset_id" && "$asset_id" != "null" ]]; then
        log "替换已有资产：$asset_name"
        api_request DELETE "$GITHUB_API/repos/$REPOSITORY/releases/assets/$asset_id" >/dev/null
      fi
    done < <(for asset in "$ARTIFACT_DIR"/*; do [[ -f "$asset" ]] && basename "$asset"; done)
  fi
  for asset in "$ARTIFACT_DIR"/*; do
    [[ -f "$asset" ]] || continue
    asset_name="$(basename "$asset")"; log "上传：$asset_name"
    curl --fail --silent --show-error -X POST "$upload_url?name=$(jq -rn --arg name "$asset_name" '$name|@uri')" \
      -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' \
      -H "Authorization: Bearer $token" -H 'Content-Type: application/octet-stream' \
      --data-binary "@$asset" >/dev/null
  done
  if [[ "$existing_release" -eq 0 ]]; then
    publish_request="$RELEASE_DIR/publish-request.json"
    jq '.draft = false' "$request_file" > "$publish_request"
    api_request PATCH "$GITHUB_API/repos/$REPOSITORY/releases/$release_id" "$publish_request" >/dev/null
  fi
  log "GitHub Release 已发布：https://github.com/$REPOSITORY/releases/tag/$VERSION"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --code) [[ $# -ge 2 ]] || fail "$1 需要参数"; VERSION_CODE="$2"; shift 2 ;;
    --platforms) [[ $# -ge 2 ]] || fail "$1 需要参数"; PLATFORMS="$2"; shift 2 ;;
    --notes-file) [[ $# -ge 2 ]] || fail "$1 需要参数"; NOTES_FILE="$2"; shift 2 ;;
    --publish) PUBLISH=1; shift ;;
    --preview|--prerelease) PREVIEW=1; PREVIEW_SET=1; shift ;;
    --allow-existing) ALLOW_EXISTING=1; shift ;;
    --skip-version-update) SKIP_VERSION_UPDATE=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --) shift; break ;;
    -*) fail "未知选项：$1" ;;
    *) [[ -z "$VERSION" ]] || fail "只能指定一个版本：$VERSION 和 $1"; VERSION="$1"; shift ;;
  esac
done

[[ -n "$VERSION" ]] || { usage >&2; exit 2; }
validate_version; parse_platforms
require_command git; require_command perl; require_command sed; require_command find

if [[ "$PUBLISH" -eq 1 && "$DRY_RUN" -eq 0 ]]; then
  GITHUB_TOKEN_VALUE="$(resolve_github_token)"
fi

RELEASE_DIR="$ROOT_DIR/composeApp/build/release/$VERSION"
ARTIFACT_DIR="$RELEASE_DIR/artifacts"
mkdir -p "$ARTIFACT_DIR"
if [[ "$DRY_RUN" -eq 1 ]]; then
  generate_notes
  log "dry-run：跳过版本写入、构建和 GitHub API；源码保持不变"
  exit 0
fi
if [[ "$SKIP_VERSION_UPDATE" -eq 0 ]]; then
  replace_versions
else
  [[ "$(read_version)" == "$VERSION" && "$(read_runtime_version)" == "$VERSION" ]] || \
    fail "--skip-version-update 要求 Version Catalog 和运行时 APP_VERSION 都已经是 $VERSION"
  log "跳过版本写入：使用工作区已有版本"
fi

if has_platform android; then build_android; fi
if has_platform desktop; then build_desktop; fi
if has_platform ios; then build_ios; fi
find "$ARTIFACT_DIR" -type f -print -quit | grep -q . || fail "没有生成任何发布产物"
generate_notes
publish_release
log "完成。产物目录：$ARTIFACT_DIR"

#!/usr/bin/env bash

# Build the release artifacts available on this host and publish them through
# GitHub's REST API. The script intentionally does not commit or push changes.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
INSTALLER_FILE="$ROOT_DIR/installer/VRCM.iss"
IOS_PROJECT_FILE="$ROOT_DIR/iosApp/iosApp.xcodeproj/project.pbxproj"
IOS_PLIST_FILE="$ROOT_DIR/iosApp/iosApp/Info.plist"
IPA_SCRIPT="$ROOT_DIR/iosApp/package-ipa.sh"

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
  --notes-file <文件>        使用指定 Markdown 文案，不自动生成文案
  --publish                  上传产物并发布 GitHub Release（自动读取环境变量或本地 GitHub 凭据）
  --preview                 发布为 GitHub 预览版（prerelease），默认发布正式版
  --prerelease              `--preview` 的兼容别名
  --allow-existing           允许更新已有同名 tag 的 Release；默认发现已有 Release 即停止
  --skip-version-update      使用工作区已有版本，不写入版本字段（适合提交版本后再次发布）
  --dry-run                  只检查配置并生成文案，不写版本、不构建、不调用 GitHub API
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

replace_versions() {
  local old_version old_code new_code
  old_version="$(read_version)"
  old_code="$(read_version_code)"
  [[ -n "$old_version" && -n "$old_code" ]] || fail "无法从 $VERSION_FILE 读取当前版本"

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
  grep -q "^#define AppVersion \"$VERSION\"" "$INSTALLER_FILE" || fail "Inno Setup 版本同步失败"
  grep -q "MARKETING_VERSION = $VERSION;" "$IOS_PROJECT_FILE" || fail "Xcode 工程版本同步失败"
  grep -q "CURRENT_PROJECT_VERSION = $new_code;" "$IOS_PROJECT_FILE" || fail "Xcode 工程 version code 同步失败"
  grep -A1 -q '<key>CFBundleShortVersionString</key>' "$IOS_PLIST_FILE" || fail "iOS plist 版本字段缺失"
  grep -A1 -q "<string>$VERSION</string>" "$IOS_PLIST_FILE" || fail "iOS plist 版本同步失败"
  grep -A1 -q '<key>CFBundleVersion</key>' "$IOS_PLIST_FILE" || fail "iOS plist version code 字段缺失"
  grep -A1 -q "<string>$new_code</string>" "$IOS_PLIST_FILE" || fail "iOS plist version code 同步失败"
  log "版本已同步：$old_version/$old_code -> $VERSION/$new_code"
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
    cp -f "$NOTES_FILE" "$NOTES_PATH"
    return 0
  fi
  local base_tag commits subject type commit_type feature_lines optimization_lines fix_lines
  base_tag="$(git -C "$ROOT_DIR" describe --tags --abbrev=0 2>/dev/null || true)"
  if [[ -n "$base_tag" ]]; then commits="$(git -C "$ROOT_DIR" log --format='%s' "$base_tag..HEAD")"; else commits="$(git -C "$ROOT_DIR" log -30 --format='%s')"; fi
  feature_lines="$(printf '%s\n' "$commits" | while IFS= read -r subject; do
    [[ -n "$subject" ]] || continue
    type="${subject%%:*}"; commit_type="${type%%(*}"
    if [[ "$commit_type" == feat || "$commit_type" == feature ]]; then
      printf -- '- %s\n' "${subject#*: }"
    fi
  done)"
  optimization_lines="$(printf '%s\n' "$commits" | while IFS= read -r subject; do
    [[ -n "$subject" ]] || continue
    type="${subject%%:*}"; commit_type="${type%%(*}"
    if [[ ("$commit_type" == perf || "$commit_type" == refactor || "$commit_type" == build) && "${subject#*: }" != docs ]]; then
      printf -- '- %s\n' "${subject#*: }"
    fi
  done)"
  fix_lines="$(printf '%s\n' "$commits" | while IFS= read -r subject; do
    [[ -n "$subject" ]] || continue
    type="${subject%%:*}"; commit_type="${type%%(*}"
    if [[ "$commit_type" == fix ]]; then
      printf -- '- %s\n' "${subject#*: }"
    fi
  done)"
  {
    printf '## 新增\n'
    if [[ -n "$feature_lines" ]]; then printf '%s\n' "$feature_lines"; else printf '%s\n' '- 持续完善页面体验、跨平台行为和日常使用流程。'; fi
    printf '\n## 优化\n'
    if [[ -n "$optimization_lines" ]]; then printf '%s\n' "$optimization_lines"; else printf '%s\n' '- 优化缓存刷新、账号切换、前后台恢复和多端发行流程。'; fi
    printf '\n## 修复\n'
    if [[ -n "$fix_lines" ]]; then printf '%s\n' "$fix_lines"; else printf '%s\n' '- 修复已知问题，并提升网络连接、状态恢复和多端运行的可靠性。'; fi
    printf '\n****************\n\n'
    printf '## New Features\n'
    printf '%s\n' '- Added the user-facing changes listed in the Chinese section, covering VRChat social, content-management, and update workflows.'
    printf '\n## Optimizations\n'
    printf '%s\n' '- Improved cache refresh, account-switch isolation, foreground/background recovery, and cross-platform distribution.'
    printf '\n## Bug Fixes\n'
    printf '%s\n' '- Fixed the issues listed above across authentication, realtime sessions, favorites, Gallery, notifications, and platform behavior.'
    printf '\n详情 / Details: https://github.com/%s/blob/main/docs/releases/%s.md\n' "$REPOSITORY" "$VERSION"
  } > "$NOTES_PATH"
  log "已生成 Release 文案：$NOTES_PATH"
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
  [[ "$(read_version)" == "$VERSION" ]] || fail "--skip-version-update 要求工作区版本已经是 $VERSION"
  log "跳过版本写入：使用工作区已有版本"
fi

if has_platform android; then build_android; fi
if has_platform desktop; then build_desktop; fi
if has_platform ios; then build_ios; fi
find "$ARTIFACT_DIR" -type f -print -quit | grep -q . || fail "没有生成任何发布产物"
generate_notes
publish_release
log "完成。产物目录：$ARTIFACT_DIR"

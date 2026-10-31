#!/usr/bin/env bash
# 4.0.0 一键发布：版本 → 测试 → 构建 → npatch → 签名校验 → 交付 → SHA256 → 说明
# 用法: bash tools/release.sh 4.0.0 [--owner] [--skip-tests] [--no-patch]
#   默认 public：libxposed API 101 / Medusa 目录 / 新装任务开关全关
#   --owner：作者自用包：API 102 / sesame-M 目录 / 默认开关不变
# 失败即停（set -euo pipefail）；不安装、不连 adb。
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" || "$VERSION" == --* ]]; then
  echo "用法: bash tools/release.sh <version> [--skip-tests] [--no-patch]" >&2
  exit 2
fi
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.]+)?$ ]]; then
  echo "版本号格式不正确: $VERSION" >&2
  exit 2
fi

SKIP_TESTS=0
NO_PATCH=0
OWNER=0
for arg in "${@:2}"; do
  case "$arg" in
    --owner) OWNER=1 ;;
    --skip-tests) SKIP_TESTS=1 ;;
    --no-patch) NO_PATCH=1 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done
PROFILE_TAG=""
EXPECTED_MIN_API=101
PROFILE_NAME="public"
PROFILE_LABEL=""
if [[ "$OWNER" -eq 1 ]]; then
  PROFILE_TAG="-owner"
  EXPECTED_MIN_API=102
  PROFILE_NAME="owner"
  PROFILE_LABEL="_owner"
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MEDUSA="$ROOT"
DELIVER="$ROOT/../deliver/V${VERSION}"
WORK="$ROOT/../_evidence/npatch-build"
NPATCH_JAR="${NPATCH_JAR:-$ROOT/../_evidence/research/npatch-1.0.9.jar}"
ORIGIN_APK="${ORIGIN_APK:-$WORK/origin.apk}"
BUILD_TOOLS="${BUILD_TOOLS:-X:/tools/android-sdk/build-tools/37.0.0}"
OUT_DIR="$WORK/out-medusa-v${VERSION//./}"

step() { printf '\n=== %s ===\n' "$1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }

step "0. 环境检查"
# Git Bash 的 PATH 只认 /x/... 形式，X:/... 不会被解析，统一转换
to_posix() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$1"
  else
    echo "$1"
  fi
}
if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in X:/tools/jdk/*/; do
    if [[ -x "${candidate}bin/java.exe" ]]; then
      export JAVA_HOME="${candidate%/}"
      break
    fi
  done
fi
[[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java.exe" ]] || fail "未找到 JDK（JAVA_HOME）"
export PATH="$(to_posix "$JAVA_HOME")/bin:$PATH"
command -v java >/dev/null || fail "java 不在 PATH"
[[ -f "$BUILD_TOOLS/apksigner.bat" ]] || fail "缺少 apksigner: $BUILD_TOOLS"
[[ -f "$BUILD_TOOLS/aapt2.exe" ]] || fail "缺少 aapt2: $BUILD_TOOLS"
[[ -f "$MEDUSA/keystore.properties" ]] || fail "缺少 keystore.properties"
[[ -f "$NPATCH_JAR" ]] || fail "缺少 npatch: $NPATCH_JAR"
[[ -f "$ORIGIN_APK" ]] || fail "缺少宿主基线: $ORIGIN_APK"
echo "JAVA_HOME=$JAVA_HOME"
echo "VERSION=$VERSION"
echo "NPATCH=$NPATCH_JAR"

step "1. 工程状态"
cd "$MEDUSA"
GIT=(git -c safe.directory="$MEDUSA")
COMMIT="$("${GIT[@]}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
BRANCH="$("${GIT[@]}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
if [[ -n "$("${GIT[@]}" status --porcelain 2>/dev/null)" ]]; then
  echo "警告：工作树有未提交改动（继续，但版本说明里会标注 dirty）"
  DIRTY="+dirty"
else
  DIRTY=""
fi
echo "commit=$COMMIT branch=$BRANCH"
FINGERPRINT="medusa-${VERSION}-${COMMIT}"
echo "fingerprint=$FINGERPRINT"

step "2. 单元测试"
if [[ "$SKIP_TESTS" -eq 1 ]]; then
  echo "跳过（--skip-tests）"
else
  ./gradlew :app:testNormalDebugUnitTest --offline
fi

step "3. 构建模块 APK（${PROFILE_NAME}）"
GRADLE_ARGS=(:app:assembleNormalRelease --offline -Pversion="$VERSION"
  -PmedusaFingerprint="$FINGERPRINT")
if [[ "$OWNER" -eq 1 ]]; then
  GRADLE_ARGS+=(-PownerBuild=true)
fi
./gradlew "${GRADLE_ARGS[@]}"
PROP="$MEDUSA/app/build/generated/moduleProp/META-INF/xposed/module.prop"
grep -q "^version=${VERSION}$" "$PROP" \
  || fail "module.prop version 不是 ${VERSION}（框架市场会读这个字段）"
grep -q "^minApiVersion=${EXPECTED_MIN_API}$" "$PROP" \
  || fail "module.prop minApiVersion 不是 ${EXPECTED_MIN_API}（构建分版错误）"
if [[ "$OWNER" -eq 1 ]]; then
  grep -q "^autoHotReload=false$" "$PROP" \
    || fail "owner 包缺少 autoHotReload=false"
fi
APK_DIR="$MEDUSA/app/build/outputs/apk/normal/release"
UNIVERSAL="$APK_DIR/Medusa-V${VERSION}${PROFILE_TAG}-universal.apk"
ARM64="$APK_DIR/Medusa-V${VERSION}${PROFILE_TAG}-arm64-v8a.apk"
ARM32="$APK_DIR/Medusa-V${VERSION}${PROFILE_TAG}-armeabi-v7a.apk"
[[ -f "$UNIVERSAL" ]] || fail "缺少 universal 产物: $UNIVERSAL"
[[ -f "$ARM64" ]] || fail "缺少 arm64 产物: $ARM64"
[[ -f "$ARM32" ]] || fail "缺少 arm32 产物: $ARM32"

step "4. 校验模块版本号"
BADGING="$("$BUILD_TOOLS/aapt2.exe" dump badging "$ARM64" 2>/dev/null | head -1 || true)"
echo "$BADGING"
echo "$BADGING" | grep -q "versionName='${VERSION}'" \
  || fail "模块 versionName 不是 ${VERSION}: $BADGING"

HOST=""
if [[ "$NO_PATCH" -eq 0 ]]; then
  step "5. npatch 生成宿主"
  # npatch 只认 BKS 密钥库（JKS 会报 Wrong version of key store）；v1.bks 由 signing/tools/ConvertKeystore 从 JKS 转换而来
  KEYSTORE="$ROOT/../signing/medusa-release-v1.bks"
  KEY_PROPS="$MEDUSA/keystore.properties"
  STORE_PASS="$(grep '^storePassword=' "$KEY_PROPS" | cut -d= -f2-)"
  KEY_ALIAS="$(grep '^keyAlias=' "$KEY_PROPS" | cut -d= -f2-)"
  KEY_PASS="$(grep '^keyPassword=' "$KEY_PROPS" | cut -d= -f2-)"
  [[ -f "$KEYSTORE" ]] || fail "缺少 BKS 签名文件: $KEYSTORE（用 signing/tools/ConvertKeystore 从 JKS 转换）"
  rm -rf "$OUT_DIR"
  mkdir -p "$OUT_DIR"
  java -jar "$NPATCH_JAR" "$ORIGIN_APK" \
    -m "$UNIVERSAL" \
    -o "$OUT_DIR" \
    -k "$KEYSTORE" "$STORE_PASS" "$KEY_ALIAS" "$KEY_PASS" \
    -l 2 --hidelibs --outputLog
  HOST_RAW="$OUT_DIR/origin-833-npatched.apk"
  [[ -f "$HOST_RAW" ]] || HOST_RAW="$(find "$OUT_DIR" -maxdepth 1 -name '*npatched*.apk' | head -1)"
  [[ -n "$HOST_RAW" && -f "$HOST_RAW" ]] || fail "npatch 未产出宿主 APK"
  HOST="$HOST_RAW"
else
  echo "跳过 npatch（--no-patch）"
fi

step "6. 签名与版本校验"
verify_sig() {
  local apk="$1"
  local label="$2"
  local out
  out="$("$BUILD_TOOLS/apksigner.bat" verify --print-certs "$apk" 2>&1 | grep -i "SHA-256 digest" | head -1 | tr -d '\r')"
  echo "$label: $out"
  echo "$out" | grep -q "0699038ea17842a49eaec941360e33723d1a547ad0c82f36932322265c986016" \
    || fail "$label 签名不是 Medusa 证书: $out"
}
verify_sig "$ARM64" "arm64"
verify_sig "$UNIVERSAL" "universal"
if [[ -n "$HOST" ]]; then
  verify_sig "$HOST" "host"
  HOST_BADGING="$("$BUILD_TOOLS/aapt2.exe" dump badging "$HOST" 2>/dev/null | head -1 || true)"
  echo "$HOST_BADGING"
fi

step "7. 组装交付目录"
mkdir -p "$DELIVER"
HOST_NAME="美杜莎_V${VERSION}${PROFILE_LABEL}_支付宝免root.apk"
MOD_ARM_NAME="美杜莎_V${VERSION}${PROFILE_LABEL}_模块_arm64.apk"
MOD_UNI_NAME="美杜莎_V${VERSION}${PROFILE_LABEL}_模块_universal.apk"
cp -f "$ARM64" "$DELIVER/$MOD_ARM_NAME"
cp -f "$UNIVERSAL" "$DELIVER/$MOD_UNI_NAME"
if [[ -n "$HOST" ]]; then
  cp -f "$HOST" "$DELIVER/$HOST_NAME"
fi
(
  cd "$DELIVER"
  : > SHA256SUMS.txt
  for f in *.apk; do
    sha256sum "$f" >> SHA256SUMS.txt
  done
)

step "8. 生成说明"
NOTES_TEMPLATE="$ROOT/docs/release-notes-${VERSION}.md"
{
  echo "V${VERSION}（$(date '+%Y-%m-%d %H:%M')）"
  echo "====================="
  echo
  echo "版本与校验"
  echo "- 模块 ${VERSION} / commit ${COMMIT}${DIRTY} / branch ${BRANCH}"
  echo "- 构建：${PROFILE_NAME} / libxposed API ${EXPECTED_MIN_API}"
  if [[ "$OWNER" -eq 1 ]]; then
    echo "- 目录：sesame-M；默认开关：保持原样；仅供作者自用，不对外分发"
  else
    echo "- 目录：Medusa；新装任务/域开关默认全关（老配置不重置）"
  fi
  echo "- 宿主：支付宝 10.8.30.8000（versionCode 3610，npatch $(basename "$OUT_DIR")）"
  echo "- 签名证书 SHA-256：0699038e…6016（Medusa 正式证书）"
  echo "- SHA256：见同目录 SHA256SUMS.txt"
  echo
  if [[ -f "$NOTES_TEMPLATE" ]]; then
    cat "$NOTES_TEMPLATE"
  else
    echo "本版改动"
    echo "- 见 commit ${COMMIT} 提交说明。"
  fi
  echo
  echo "回退"
  echo "- 宿主和模块必须成对回退；回退前备份 plans.json / config_v2.json / risk_state.json / ledger。"
  echo
  echo "源码"
  echo "- commit ${COMMIT}${DIRTY}，branch ${BRANCH}"
} > "$DELIVER/V${VERSION}_说明.txt"

step "完成"
cat "$DELIVER/SHA256SUMS.txt"
echo
echo "交付目录: $DELIVER"

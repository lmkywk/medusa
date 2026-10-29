#!/usr/bin/env bash
# 4.0.0 本地 CI：测试 / 构建 / 校验 / 许可，一键跑。
# 用法: bash tools/ci.sh [test|build|verify|licenses|all]
set -euo pipefail

CMD="${1:-all}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MEDUSA="$ROOT"
BUILD_TOOLS="${BUILD_TOOLS:-X:/tools/android-sdk/build-tools/37.0.0}"
VERSION="${VERSION:-4.0.0}"

step() { printf '\n=== %s ===\n' "$1"; }

ensure_java() {
  # Git Bash 的 PATH 只认 /x/... 形式
  if command -v cygpath >/dev/null 2>&1; then
    PATH_PREFIX="$(cygpath -u "$JAVA_HOME" 2>/dev/null || true)"
  fi
  if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java.exe" ]]; then
    for candidate in X:/tools/jdk/*/; do
      if [[ -x "${candidate}bin/java.exe" ]]; then
        export JAVA_HOME="${candidate%/}"
        break
      fi
    done
  fi
  [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java.exe" ]] || { echo "未找到 JDK" >&2; exit 1; }
  if command -v cygpath >/dev/null 2>&1; then
    export PATH="$(cygpath -u "$JAVA_HOME")/bin:$PATH"
  else
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
  command -v java >/dev/null || { echo "java 不在 PATH" >&2; exit 1; }
}

run_test() {
  step "单元测试（golden + 回放）"
  (cd "$MEDUSA" && ./gradlew :app:testNormalDebugUnitTest)
}

run_build() {
  step "构建模块 APK"
  (cd "$MEDUSA" && ./gradlew :app:assembleNormalRelease -Pversion="$VERSION")
}

run_verify() {
  step "校验产物"
  local dir="$MEDUSA/app/build/outputs/apk/normal/release"
  local expected="0699038ea17842a49eaec941360e33723d1a547ad0c82f36932322265c986016"
  for apk in "$dir"/Medusa-V*-arm64-v8a.apk "$dir"/Medusa-V*-universal.apk; do
    [[ -f "$apk" ]] || { echo "缺少产物: $apk" >&2; exit 1; }
    local name
    name="$(basename "$apk")"
    local cert
    cert="$("$BUILD_TOOLS/apksigner.bat" verify --print-certs "$apk" 2>&1 | grep -i 'SHA-256 digest' | head -1 | tr -d '\r')"
    echo "$name"
    echo "  $cert"
    echo "$cert" | grep -q "$expected" || { echo "签名不匹配: $name" >&2; exit 1; }
    local badging
    badging="$("$BUILD_TOOLS/aapt2.exe" dump badging "$apk" 2>/dev/null | head -1 || true)"
    echo "$badging" | grep -q "versionName='${VERSION}'" || { echo "版本号不是 ${VERSION}: $badging" >&2; exit 1; }
  done
  step "SHA256"
  (cd "$dir" && sha256sum Medusa-V*.apk)
}

run_licenses() {
  step "依赖许可复核"
  local cache="X:/tools/gradle-home/caches/modules-2/files-2.1"
  for dep in \
    "androidx.appcompat/appcompat" \
    "io.github.libxposed/api" \
    "org.projectlombok/lombok" \
    "com.elvishew/xlog" \
    "com.squareup.okhttp3/okhttp" \
    "com.fasterxml.jackson.core/jackson-databind" \
    "org.jetbrains.kotlin/kotlin-stdlib" \
    "top.yukonga.miuix.kmp/miuix-ui" \
    "androidx.compose.material/material-icons-extended"; do
    local pom
    pom="$(find "$cache/$dep" -name "*.pom" 2>/dev/null | head -1)"
    if [[ -z "$pom" ]]; then
      echo "$dep: 未在本地缓存找到 POM"
      continue
    fi
    local name
    name="$(sed -n '/<licenses>/,/<\/licenses>/p' "$pom" | grep -m1 "<name>" | sed -e 's/.*<name>//' -e 's/<\/name>.*//' | tr -d '\r')"
    echo "$dep: ${name:-（POM 未声明，见 docs/NOTICE.md）}"
  done
  echo
  echo "完整清单与待确认项见 docs/NOTICE.md；许可原文见 docs/licenses/"
}

ensure_java
case "$CMD" in
  test) run_test ;;
  build) run_build ;;
  verify) run_verify ;;
  licenses) run_licenses ;;
  all) run_test; run_build; run_verify; run_licenses ;;
  *) echo "用法: bash tools/ci.sh [test|build|verify|licenses|all]" >&2; exit 2 ;;
esac

step "完成: $CMD"
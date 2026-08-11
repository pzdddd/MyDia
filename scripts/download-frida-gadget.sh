#!/usr/bin/env bash
#
# 下载 frida-gadget 并放到 jniLibs/，让 MyDia 的 FridaHook 开箱即用。
#
# 用法（在项目根目录执行）：
#   bash scripts/download-frida-gadget.sh            # 下载最新版
#   bash scripts/download-frida-gadget.sh 17.17.0    # 指定版本
#
# 产物：app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libfrida-gadget.so
# 这两个 so 已在 .gitignore 排除（约 40-80MB，不提交仓库）。
#
# 放 jniLibs 后，AGP 自动按 abi 打包进 apk/lib/，System.loadLibrary("frida-gadget")
# 直接命中（FridaHook 的第一加载路径），无需走 assets 释放。
#
set -euo pipefail

VERSION="${1:-17.17.0}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JNI_DIR="$PROJECT_DIR/app/src/main/jniLibs"

echo "==> 下载 frida-gadget $VERSION"
mkdir -p "$JNI_DIR/arm64-v8a" "$JNI_DIR/armeabi-v7a"

# arm64-v8a
ARM64_URL="https://github.com/frida/frida/releases/download/$VERSION/frida-gadget-$VERSION-android-arm64.so.xz"
ARM64_OUT="$JNI_DIR/arm64-v8a/libfrida-gadget.so"
if [ -f "$ARM64_OUT" ]; then
    echo "==> arm64 已存在，跳过（删 $ARM64_OUT 可强制重下）"
else
    echo "==> 下载 arm64: $ARM64_URL"
    curl -fL "$ARM64_URL" | xz -d > "$ARM64_OUT"
    echo "==> arm64 完成: $(du -h "$ARM64_OUT" | cut -f1)"
fi

# armeabi-v7a
ARM_URL="https://github.com/frida/frida/releases/download/$VERSION/frida-gadget-$VERSION-android-arm.so.xz"
ARM_OUT="$JNI_DIR/armeabi-v7a/libfrida-gadget.so"
if [ -f "$ARM_OUT" ]; then
    echo "==> arm 已存在，跳过（删 $ARM_OUT 可强制重下）"
else
    echo "==> 下载 arm: $ARM_URL"
    curl -fL "$ARM_URL" | xz -d > "$ARM_OUT"
    echo "==> arm 完成: $(du -h "$ARM_OUT" | cut -f1)"
fi

echo ""
echo "✓ 完成。重新编译即可：./gradlew assembleDebug"
echo "  apk 会增大约 $(du -sh "$JNI_DIR" | cut -f1)（两个架构的 gadget）"
echo "  如需移除：rm -rf $JNI_DIR"

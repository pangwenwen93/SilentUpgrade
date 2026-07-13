#!/bin/bash
set -e

# 用法：
#   ./build_apk.sh factory release    打 factoryPadRelease 包
#   ./build_apk.sh factory debug      打 factoryPadDebug 包
#   ./build_apk.sh common release     打 commonPadRelease 包
#   ./build_apk.sh common debug       打 commonPadDebug 包
#   ./build_apk.sh all                打全部 4 个包

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$PROJECT_DIR/outputs"

print_usage() {
    echo "用法:"
    echo "  $0 factory release    打包 factoryPadRelease"
    echo "  $0 factory debug      打包 factoryPadDebug"
    echo "  $0 common  release    打包 commonPadRelease"
    echo "  $0 common  debug      打包 commonPadDebug"
    echo "  $0 all                打包全部（release + debug）"
}

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    print_usage
    exit 1
fi

FLAVOR="$1"
BUILD_TYPE="$2"

build_apk() {
    local flavor=$1
    local buildType=$2
    local flavorCap=$(echo "$flavor" | awk '{print toupper(substr($0,1,1)) tolower(substr($0,2))}')
    local buildTypeCap=$(echo "$buildType" | awk '{print toupper(substr($0,1,1)) tolower(substr($0,2))}')
    local task="assemble${flavorCap}Pad${buildTypeCap}"

    echo ""
    echo "============================================"
    echo "开始打包: $flavor Pad $buildType"
    echo "Gradle Task: $task"
    echo "============================================"

    "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" "$task"

    local src_apk
    src_apk=$(find "$PROJECT_DIR/app/build/outputs/apk/${flavor}Pad/${buildType}" -name "*.apk" -type f | head -n 1)

    if [ -z "$src_apk" ]; then
        echo "错误：未找到生成的 APK 文件"
        exit 1
    fi

    mkdir -p "$OUTPUT_DIR"
    local file_name="SilentUpgrade_${flavor}Pad_${buildType}.apk"
    cp "$src_apk" "$OUTPUT_DIR/$file_name"

    echo ""
    echo "APK 已生成: $OUTPUT_DIR/$file_name"
    echo "源文件: $src_apk"
}

case "$FLAVOR" in
    factory)
        if [ "$BUILD_TYPE" != "release" ] && [ "$BUILD_TYPE" != "debug" ]; then
            print_usage
            exit 1
        fi
        build_apk "factory" "$BUILD_TYPE"
        ;;
    common)
        if [ "$BUILD_TYPE" != "release" ] && [ "$BUILD_TYPE" != "debug" ]; then
            print_usage
            exit 1
        fi
        build_apk "common" "$BUILD_TYPE"
        ;;
    all)
        build_apk "factory" "release"
        build_apk "factory" "debug"
        build_apk "common" "release"
        build_apk "common" "debug"
        ;;
    *)
        print_usage
        exit 1
        ;;
esac

echo ""
echo "全部完成，输出目录: $OUTPUT_DIR"

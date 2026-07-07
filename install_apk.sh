#!/bin/bash
echo "=== VoiceBridge Android 真机一键刷写工具 ==="
adb="/opt/homebrew/bin/adb"
if [ ! -f "$adb" ]; then
    adb="adb"
fi

echo "正在检查已连接设备..."
devices=$($adb devices | grep -v "List" | grep "device")

if [ -z "$devices" ]; then
    echo "❌ 错误：未检测到任何连接且已授权的 Android 设备！"
    echo "请按以下步骤操作："
    echo "1. 用 USB 线将 Android 手机连接到 Mac"
    echo "2. 在手机的「设置 - 开发者选项」中打开「USB 调试」"
    echo "3. 在手机弹出的「允许 USB 调试吗？」窗口中选择「总是允许」"
    echo "4. 重新运行此脚本"
    exit 1
fi

echo "检测到设备，正在尝试安装 app-debug.apk..."
$adb install -r build-outputs/app-debug.apk

if [ $? -eq 0 ]; then
    echo "🎉 安装成功！您可以在手机上打开 VoiceBridge 开始测试了！"
else
    echo "❌ 安装失败，请检查 USB 连接或是否有安全软件拦截。"
fi

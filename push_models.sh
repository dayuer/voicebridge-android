#!/bin/bash
echo "=== VoiceBridge Android 离线模型一键部署工具 ==="
adb="/opt/homebrew/bin/adb"
if [ ! -f "$adb" ]; then
    adb="adb"
fi

src_dir="/Users/liyuqing/sproot/translate/DerivedData/Build/Products/Debug-iphoneos/VoiceBridge.app"
dest_app="com.voicebridge.android"

# 检查 adb 设备
devices=$($adb devices | grep -v "List" | grep "device")
if [ -z "$devices" ]; then
    echo "❌ 错误：未检测到任何已连接的 Android 设备！"
    exit 1
fi

echo "正在手机上创建 Models 目录结构..."
$adb shell run-as $dest_app mkdir -p files/Models
$adb shell run-as $dest_app mkdir -p files/Models/sherpa-onnx-pyannote-segmentation-3-0

echo "开始部署模型..."

# 需要同步的模型列表
files=(
    "model.int8.onnx"
    "sense-voice-tokens.txt"
    "silero_vad.onnx"
    "punct.int8.onnx"
    "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"
)

for file in "${files[@]}"; do
    if [ -f "$src_dir/$file" ]; then
        echo "正在传输: $file (大小: $(du -sh "$src_dir/$file" | cut -f1))..."
        $adb push "$src_dir/$file" "/data/local/tmp/$file"
        echo "正在将 $file 写入 App 私有存储..."
        $adb shell "cat /data/local/tmp/$file | run-as $dest_app sh -c 'cat > files/Models/$file'"
        $adb shell rm "/data/local/tmp/$file"
    else
        echo "⚠️ 警告：源文件不存在: $src_dir/$file"
    fi
done

# 传输 Pyannote (在 iOS 包里叫 model.onnx)
if [ -f "$src_dir/model.onnx" ]; then
    echo "正在传输 Pyannote 分割模型..."
    $adb push "$src_dir/model.onnx" "/data/local/tmp/model.onnx"
    echo "正在将 Pyannote 写入 App 对应的 Models/sherpa-onnx-pyannote-segmentation-3-0/ 路径..."
    $adb shell "cat /data/local/tmp/model.onnx | run-as $dest_app sh -c 'cat > files/Models/sherpa-onnx-pyannote-segmentation-3-0/model.onnx'"
    $adb shell "cat /data/local/tmp/model.onnx | run-as $dest_app sh -c 'cat > files/Models/model.onnx'"
    $adb shell rm "/data/local/tmp/model.onnx"
else
    echo "⚠️ 警告：未找到 Pyannote 分割模型"
fi

echo "🎉 离线模型物理部署完毕！"

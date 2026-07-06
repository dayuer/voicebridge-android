# VoiceBridge 项目迁移到 Android 平台可行性评估报告（勘误修订版）

> 本修订版基于对 iOS 侧真实源码（`Sources/`、`project.yml`、`Resources/AssetPacks/`）的逐条核对，
> 更正了初版中的两处事实性错误（最低系统版本、模型体积），补齐了被遗漏的子系统（说话人分离管线、
> Share Extension、双轨分发的 Bundle 内置回退路径），并据此重估了工作量与风险。
> **核对结论：架构级技术映射准确可信；但真实迁移量比初版结论更重、包体更大。**

本报告评估现有 iOS 原生项目 **VoiceBridge**（畅译）迁移至 Android 平台的可行性，提供技术栈映射、
核心难点剖析、技术选型建议及工作量估算。

---

## 〇、初版勘误摘要（本次修订项）

| # | 初版说法 | 真实情况（源码核对） | 影响 |
| :-- | :-- | :-- | :-- |
| 1 | 最低系统 **iOS 26.0+** | `project.yml` `deploymentTarget: iOS 17.0` | 前提性错误；且初版遗漏了 iOS 已有的**双轨分发**设计 |
| 2 | core-asr **~100MB+** / speaker ~50MB+ | core-asr 打包 **222MB**（原始 ~315MB）；speaker 打包 **32MB** | 包体风险被严重低估；SenseVoice 单模型即 239MB |
| 3 | 说话人分离 = pyannote + CAM++ + AHC | 实为 **~10 个专用 Service** 的两阶段管线 | 第 6–8 周（说话人分离）工作量被低估 |
| 4 | 未提及 | 存在 **Share Extension**（系统分享导入）与 **BADownloader** 独立 target | 路线图缺项 |
| 5 | 动态下载 vs 内置为"待决策" | iOS 已同时实现两条路径（BA + Bundle 内置回退） | Android 决策有现成先例 |

---

## 一、 核心结论

**可行性评分：高度可行 (8.0 / 10)**（初版 8.5，因包体与说话人子系统复杂度下调 0.5）

- **关键有利因素**：本应用最核心的"护城河"技术 —— 端侧离线语音识别 (SenseVoice)、
  CT-Transformer 标点恢复、以及声纹分割与说话人分离 (CAM++ + pyannote) —— 底座均基于开源的
  `sherpa-onnx` 框架。该框架官方对 Android (Java/Kotlin API) 提供原生支持，底层 `.onnx` 模型和
  核心算法逻辑可 **100% 无缝复用**。
- **主要技术挑战**：iOS 独占的系统级服务（`Background Assets` 资产包管理、
  `NaturalLanguage.NLEmbedding` 零包体积句向量、`UIPrintPageRenderer` 原生 PDF 渲染）在 Android 侧
  无直接对应系统 API，需替代方案。
- **需重点重估的两点**：① **包体**——原始模型合计约 350MB（334 MiB，SenseVoice int8 单模型即 239MB），
  叠加各架构 native 库后"内置回退"方案约 400MB 级；② **说话人分离子系统**——iOS 侧远不止三个模型，
  而是一套两阶段（文字/声纹分离）、约 10 个 Service 的精密管线，是全项目最难移植的部分。
- **UI & 架构映射**：SwiftUI → **Jetpack Compose**，SwiftData → **Jetpack Room**，
  MVVM + Observation 架构在 Android 侧有成熟 Jetpack 对应。

---

## 二、 核心技术栈映射表 (iOS vs Android)

| 模块维度 | iOS 原生实现（源码核实） | Android 推荐映射方案 | 迁移可行性与开发成本 |
| :--- | :--- | :--- | :--- |
| **编程语言** | Swift 6（Strict Concurrency，warning 即 error） | **Kotlin**（Coroutines & Flows） | **极高**。协程与 Swift Concurrency 概念高度一致。 |
| **UI 框架** | SwiftUI | **Jetpack Compose** | **极高**。皆为现代声明式 UI。 |
| **数据持久化** | SwiftData（`@Model`：`MeetingRecord` / `TranscriptSegment` / `AISummaryItem`） | **Jetpack Room** | **极高**。Room + Flow 完美替代响应式绑定。 |
| **键值存储** | UserDefaults + `SettingsStore` | **Jetpack DataStore (Preferences)** | **极高**。 |
| **离线 ASR 引擎** | `sherpa-onnx` + SenseVoice int8（`model.int8.onnx` **239MB**） | **sherpa-onnx Android SDK** | **极高**。复用同一 `.onnx` 模型。 |
| **系统 ASR 降级** | `SFSpeechRecognizer`（`AppleSpeechService`） | **Whisper-tiny-onnx** 或 **在线 API** | **中等**。Google `SpeechRecognizer` 对长音频转写不佳。 |
| **标点恢复** | CT-Transformer int8（`punct.int8.onnx` **75MB**，`PunctuationService`） | **sherpa-onnx `OfflinePunctuation`** | **极高**。 |
| **说话人分离** | pyannote-segmentation-3.0 + CAM++（3D-Speaker）**+ 约 10 个编排 Service** | **sherpa-onnx Diarization JNI + Kotlin 重写编排层** | **中等偏难**（见 §三.6）。 |
| **本地 AHC 聚类** | Swift（Average Linkage，`SpeakerMatcher`，`import Accelerate`） | **Kotlin 纯 CPU 重写** | **极高**。百级段落 < 10ms，无需硬件加速。 |
| **本地 RAG 向量化** | `NLEmbedding.sentenceEmbedding`（512 维，`RAGEmbeddingService`） | **ONNX Runtime (bge-micro)** 或 **SQLite FTS** | **中等**。Android 无系统级免包体句向量。 |
| **大资产包下载** | Background Assets（core-asr essential / speaker prefetch）**+ Bundle 内置回退** | **Play Asset Delivery** / **自建 CDN** **+ APK 内置回退** | **中等**。iOS 双轨可直接对标（见 §三.2）。 |
| **系统分享导入** | **Share Extension**（`ShareViewController`，系统分享面板导入音频） | **`ACTION_SEND` intent-filter Activity** | **高**。初版遗漏，需补入路线图。 |
| **后台运行保护** | `beginBackgroundTask` / BGTask + `BADownloader` 独立 target | **WorkManager + Foreground Service** | **极高（优于 iOS）**。 |
| **AI 协作跳转** | `canOpenURL` + `UIPasteboard.changeCount` + `verificationKey`（`AIAppLauncher`） | **PackageManager `<queries>`** + **ClipData** + 校验标记 | **中等**。Android 11+ 可见性限制。 |
| **PDF 高保真导出** | `UIPrintPageRenderer`（HTML，`PDFExportService`） | **WebView + PrintManager** | **极高**。同为 HTML+CSS 模板渲染。 |

---

## 三、 核心难点与平台差异深度剖析

### 1. 本地向量 RAG (RAGEmbeddingService) —— 核心系统能力断层
- **iOS 现状**：`NLEmbedding.sentenceEmbedding(for:)` 提取中/英句向量（512 维），系统内置、零网络、
  不占 App 包体、速度极快。代码含语种回退（非内置语种回退简体中文句向量）。
- **Android 阻碍**：Android 无系统内置、面向开发者开放的通用多语言句向量 API。
- **建议对策**：
  1. **方案 A（离线向量化）**：引入 ONNX Runtime + 超轻量多语言句向量模型（如 `bge-micro-v2`，
     ~45MB），保证 100% 离线与体验一致，代价是包体。
  2. **方案 B（本地全文检索）**：Room 的 **FTS4/FTS5**，零包体增量，但仅字面匹配、无语义。
  3. **方案 C（在线 API）**：有网可用、无网不可用。

### 2. ASR 素材包管理与动态分发 (AssetPackLocator) —— iOS 已是双轨，Android 有现成先例
- **iOS 现状（更正）**：项目采用**双轨分发**（`project.yml` 注释与 `AssetPackLocator` 逻辑）：
  - **iOS 26+**：`core-asr`（essential，随 App 下载）与 `speaker`（prefetch，安装后后台拉取）
    两个 Apple-Hosted Background Assets 包分发，不占安装体积。
  - **iOS 17–25**：模型**直接内置进 App bundle**（约 +350MB），首次安装即可用。
  - `AssetPackLocator.path()` 优先查 BA 命中版本，未命中则回退 Bundle 内置版本；
    `silero_vad.onnx`（632KB）始终常驻 bundle，不属于任何资产包。
- **真实体积（更正）**：
  - `core-asr`：`model.int8.onnx` 239MB + `punct.int8.onnx` 75MB + tokens ≈ **原始 315MB，打包 222MB**
  - `speaker`：CAM++ 28MB + pyannote 分段 ≈ **打包 32MB**
- **Android 迁移**：
  - **Google Play 渠道**：`core-asr` → **Fast-follow**，`speaker` → **On-demand**（PAD），与 iOS 双轨高度契合。
  - **国内渠道（华为/小米/OPPO/VIVO）**：无统一 PAD，需自建 CDN，`WorkManager` 断点续传下载至
    `context.filesDir`，UI 明确展示进度。
  - **关键提醒**：iOS 的"iOS 17–25 内置回退"路径就是 Android"内置进 APK"方案的直接先例——同一套
    `AssetPackLocator`「先查动态包、未命中回退内置」的定位逻辑应原样映射到 Android。

### 3. 后台转录稳定性 (ImportTaskQueue)
- **iOS 现状**：后台限制严格，`beginBackgroundTask` 仅争取几分钟；长音频易被杀，依赖断点续传与
  `BGProcessingTask` 重调度；并有独立 `BADownloader` target 处理 BA 后台下载。
- **Android 优势 / 对策**：**WorkManager** 调度 + 转录时转 **Foreground Service**（常驻状态栏通知），
  即使后台/熄屏/被划掉也不被杀。这将极大简化 `ImportTaskQueue` 的"防后台被杀"逻辑，断点续传仅作
  关机/极低内存兜底。

### 4. 跨 App 协作与剪贴板监听 (AIAppLauncher)
- **iOS 现状**：`canOpenURL` 探测 AI App 是否安装；剪贴板携带 Prompt 唤醒；
  `UIPasteboard.changeCount` 免授权监听变化；`verificationKey` 校验剪贴板内容是否源自本 App。
- **Android 阻碍**：① Android 11+ 应用可见性限制，须在 `AndroidManifest.xml` 用 `<queries>` 声明
  目标包名（如 `com.openai.chatgpt`）；② `ClipboardManager` 监听仅前台生效，Android 12+ 读取他 App
  剪贴板会弹 Toast。
- **对策**：`<queries>` 白名单；放弃后台强监听，改在 Activity `onResume()` 检查剪贴板，命中
  `verificationKey` 标记则弹归档横幅——最大限度保护隐私与合规。

### 5. 音频提取与重采样
- **iOS 现状**：`AVAudioFile` + `AVAudioConverter` 读取 m4a/mp3/wav 并重采样为 16kHz Mono Float32 PCM。
- **Android 对策**：`MediaExtractor`/`MediaCodec` 较低级、工作量大；推荐 **Oboe** 或
  **FFmpeg-NDK** 一步完成多格式 → 16kHz mono PCM 转码（不追求极致包体时最稳妥）。

### 6. 说话人分离子系统 —— 全项目最难移植部分（初版严重低估）
- **iOS 现状（更正）**：并非"pyannote + CAM++ + AHC"三件套，而是一套两阶段、约 10 个 Service 的管线：
  - **模型层**：pyannote-segmentation-3.0（分段）+ CAM++（3D-Speaker 中英双语声纹，**唯一双语声纹模型，
    不可替换**）。
  - **编排层**：`DiarizationService`（聚类+拆分的唯一真理源）、`SpeakerMatcher`（AHC）、
    `SpeakerDiarizationEngine`、`SpeakerDiarizationService`、`SpeakerEmbeddingService`、
    `VoiceprintExtractionService`、`VoiceprintPipelineService`、`TranscriptComposer`、
    `TranscriptFinalizer`、`DiarizationTaskQueue`。
  - **两阶段设计**：文字管线就绪即可用；声纹提取进**独立后台队列**（`DiarizationTaskQueue` /
    `VoiceprintPipelineService`），不阻塞文字结果。
  - **已知陷阱**：张冠李戴根因在切分层（非声纹层）；说话人存在硬上限（易被 refactor 回退，需保留）。
- **Android 迁移**：sherpa-onnx 提供 Android diarization JNI 可复用模型层，但**上述编排层需在 Kotlin
  侧完整重写**并保留两阶段队列语义与说话人上限约束。**这是第 3–6 周里真正的成本大头，应单列。**

---

## 四、 开发工作量估算与路线图

假设 1 名熟悉 Android Jetpack 与 C++/JNI 集成的资深工程师，预计总工期 **12–14 周**（初版 10–12，
因说话人子系统与 Share Extension 上调）：

### 阶段一：基础架构与数据层 (第 1–2 周)
- [ ] 工程搭建，配置 Kotlin / Coroutines / Serialization。
- [ ] Room 重建 `MeetingRecord` / `TranscriptSegment` / `AISummaryItem`（及 `SpeakerProfile` /
      `VoiceSample` / `GlossaryEntry`）。
- [ ] Preferences DataStore 替代 `SettingsStore`。

### 阶段二：语音转录与标点 (第 3–5 周)
- [ ] 集成 `sherpa-onnx` Android SDK。
- [ ] 音频解码与重采样模块（FFmpeg-NDK 优先），多格式 → 16kHz mono PCM。
- [ ] 移植 SenseVoice 离线转录、silero VAD 分段与断点续传。
- [ ] 移植 CT-Transformer 标点恢复。

### 阶段三：说话人分离子系统 (第 6–8 周，**单列**)
- [ ] 集成 sherpa-onnx diarization JNI（pyannote + CAM++）。
- [ ] Kotlin 重写编排层：AHC 聚类、`DiarizationService` 真理源、段落拆分/合成。
- [ ] 复刻**两阶段队列**（文字即用 / 声纹进独立后台队列）与说话人硬上限约束。

### 阶段四：后台任务与动态分发 (第 9–10 周)
- [ ] WorkManager + Foreground Service 后台排队转录。
- [ ] PAD 动态下载（国内渠道走 WorkManager HTTP 断点续传）**+ APK 内置回退路径**（对标 iOS 双轨）。
- [ ] AI 跳转（`<queries>` + Intent）+ `onResume()` 剪贴板 `verificationKey` 比对。
- [ ] **Share Extension 对标**：`ACTION_SEND` intent-filter 音频导入。

### 阶段五：UI 与 PDF 导出 (第 11–12 周)
- [ ] Compose 1:1 重构 `HomeView` / `RecordingLibraryView` / `MeetingRecordsListView`。
- [ ] 详情页：播放器 + 逐字稿高亮排版 + 说话人重命名。
- [ ] 无界面 WebView + PrintManager PDF 导出（A4）。

### 阶段六：验证与优化 (第 13–14 周)
- [ ] LeakCanary 压测数小时转录内存平稳。
- [ ] arm64-v8a / armeabi-v7a ONNX 推理调优。
- [ ] 弱网/无网验证断点续传与降级。

---

## 五、 关键迁移风险与规避策略

1. **包体积过大风险（体量更正后上调）**
   - *风险*：原始模型合计约 **350MB（334 MiB）**（SenseVoice int8 单模型即 239MB），加 onnxruntime
     device-arch 静态库（ios-arm64 约 64MB）与 `sherpa-onnx` 各架构 native 代码，"内置回退"APK 达
     ~400MB 级。初版"APK 可能突破 200MB"实为低估。
   - *规避*：`.so` 走 App Bundle 按架构分发；`.onnx` 全部放动态下载包（PAD / CDN）；主 APK 控制在
     25MB 内；内置回退仅作无 PAD 渠道兜底并明确告知用户下载量。
2. **底层 C++ 库引起的 Crash**
   - *风险*：JNI(sherpa-onnx) 内存越界或配置错误直接闪退，Room 无法捕获。
   - *规避*：封装进独立 Worker，wrapper 层防空指针；Sentry / Firebase Crashlytics 收集 Native 堆栈。
3. **Android 厂商后台保活限制**
   - *风险*：华为/小米等激进省电，前台服务也可能数十分钟后被休眠。
   - *规避*：引导用户加入"后台高电耗 / 自启动"白名单，标准前台通知锁保活。
4. **说话人分离管线移植风险（新增）**
   - *风险*：编排层逻辑复杂（两阶段队列、切分层张冠李戴、说话人硬上限），Kotlin 重写易引入回归。
   - *规避*：以 `DiarizationService` 为唯一真理源单点重写；保留说话人上限；用同一批音频做 iOS/Android
     逐段对拍验证。

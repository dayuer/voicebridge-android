# VoiceBridge Android — iOS 功能对齐 + Android 原生设计 设计文档

日期：2026-07-07
参照：`/Users/liyuqing/sproot/translate`（iOS VoiceBridge 畅译）

## 一、差距分析结论

Android 端核心转录管线（ASR/VAD/标点/声纹两阶段管线/PDF 导出/AI 九宫格/分享导入/前台服务）已完成。
与 iOS 对比后确认的缺口分两类：

### A. Android 原生设计缺口（Material 3 规范）

| # | 问题 | 现状 | 方案 |
|---|------|------|------|
| A1 | `VoiceBridgeTheme` composable 是空壳，未包 `MaterialTheme`，全 App 的 `MaterialTheme.colorScheme` 落在默认紫色基线 | `Theme.kt` 末尾 `content()` 直通 | 用品牌靛蓝（亮 #5B5BD6 / 暗 #7A7AF0）构建 light/dark `ColorScheme`，语义色对齐 iOS `Theme.swift`（bgCanvas→background、bgSurface→surface、separator→outlineVariant 等） |
| A2 | 无外观设置 | iOS 有 自动/亮色/暗色 分段选择 | SharedPreferences 持久化 `appearance`（system/light/dark），设置页 SegmentedButton，`VoiceBridgeTheme(appearance)` 应用 |
| A3 | 系统返回键任何页面直接退出 App | MainActivity 手写状态导航、无 `BackHandler` | 详情页/设置页/设置子页各加 `BackHandler`；Manifest 开 `enableOnBackInvokedCallback`（predictive back） |
| A4 | 非 edge-to-edge，Manifest 用平台旧主题 `@android:style/Theme.Material.NoActionBar` | — | `enableEdgeToEdge()` + `Theme.SplashScreen`（core-splashscreen，DayNight 底色） |

### B. 功能缺口（iOS 有 / Android 无）

| # | 功能 | iOS 参照 | Android 方案 |
|---|------|---------|-------------|
| B1 | "声波成桥"启动动画 | `SplashView.swift`（声波弧滑入→桥体描边→品牌字浮起→淡出，1.25s+0.3s；减弱动态降级） | core-splashscreen 衔接 Compose `SplashOverlay`：Canvas 绘制同款矢量（200×110 设计坐标），`Animatable` 时间线；`Settings.Global.ANIMATOR_DURATION_SCALE==0` 时降级静态 |
| B2 | AI 数据分享同意门禁 | `AIDataConsentView.swift` + `requestAIFlow` 守卫 | `AIDataConsentSheet`（ModalBottomSheet，三张披露卡），`MeetingDetailCompose` 打开九宫格前查 `ai_data_sharing_consent`；撤销隐私授权时联动重置（对齐 `SettingsStore.hasAgreedToPrivacyConsent` didSet 逻辑：同意隐私→AI 同意置 true，撤销→置 false） |
| B3 | 默认识别语言设置不生效 | `SettingsStore.defaultLanguage` → 导入时的默认语种 | 设置页下拉（自动检测 + 12 语种），存 `default_language`；`HomeCompose`/`ShareImportActivity` 创建 `MeetingRecordEntity` 时写入 `importLanguageCode` |
| B4 | 日志中心 + 调试控制台 | `AppLog.swift`/`LogStore.swift`/`DebugConsoleView.swift` | `AppLog` object（分类打点，写 Logcat + `LogStore` 内存环形缓冲 1000 条/批量裁 200），核心服务接入；设置-系统与诊断新增"调试日志控制台"页（分类过滤、复制、清空） |
| B5 | 声纹库管理页功能薄弱 | `SpeakerLibraryView.swift` + `SpeakerLibraryViewModel.swift`（Faces 全局聚类范式） | 升级 `SpeakerSubView`：①已登记发言人卡片（样本数、重命名、删除时重置关联段落为"未知发言人"并游离化样本）；②待标定未知声音簇（游离 `VoiceSampleEntity` 用现有 `SpeakerMatcher` AHC 聚类，代表样本播放定位原音频，标定到已有/新建发言人、拆分簇、忽略） |
| B6 | 词库管理页功能薄弱 | `GlossaryView.swift` | 升级 `GlossarySubView`：计数/上限胶囊（n/200）、内联添加框、查重、搜索（>10 条时）、批量导入（换行/逗号/顿号分隔去重）、导出（系统分享）、Toast 反馈 |

### 不迁移项（有意排除）

- `RAGEmbeddingService`：iOS 依赖系统级 `NLEmbedding`（零包体）。iOS 端该向量目前无用户可见消费场景（列表搜索是纯文本匹配，Android 已有）。Android 引入 bge-micro 需 +45MB 包体，收益为零，跳过。
- `AppleSpeechService` 小语种回退：Android 无等价系统离线长音频 ASR；现状（非 SenseVoice 语种明确报"暂不支持"）保留。
- iOS `DebugConsoleView` 的悬浮气泡形态：Android 以设置页入口的全屏控制台等价实现。

## 二、实施顺序

1. Material 3 主题体系 + 外观设置 + edge-to-edge（A1/A2/A4）
2. 返回键导航 + predictive back（A3）
3. 启动动画（B1）
4. AI 数据同意门禁（B2）
5. 默认识别语言（B3）
6. AppLog/LogStore/调试控制台（B4）
7. 声纹库升级（B5）
8. 词库升级（B6）

每步 `./gradlew assembleDebug` 验证后独立提交。

## 三、关键实现细节

- **设置存储**：沿用现有 `voicebridge_settings` SharedPreferences（项目未真正接 Hilt/DataStore，不引入新依赖体系），新增键：`appearance`、`default_language`、`ai_data_sharing_consent`。
- **主题应用**：`VoiceBridgeTheme(appearance: AppAppearance, content)`；`AppAppearance` 枚举（SYSTEM/LIGHT/DARK）。MainActivity 顶层 `mutableStateOf` 监听 prefs 变化即时切换。既有 `VoiceBridgeTheme` object 的自定义令牌（speakerPalette 等）保留，但暗色判定改为跟随所选外观而非 `isSystemInDarkTheme()`（通过 CompositionLocal 提供当前暗色态）。
- **声纹簇聚类**：复用 `SpeakerMatcher` 的 AHC；候选簇 = `speaker_profile_id IS NULL` 的样本聚类结果；代表样本 = 簇内时长最长者；播放用 `MediaPlayer` 打开会议音频 seek 到 `startTime`，到 `endTime` 自动停。
- **删除发言人语义**（对齐 iOS）：档案删除 → 其样本 `speaker_profile_id = NULL`（Room FK 已 SET_NULL）→ 关联 `TranscriptSegment` 重置标签为"未知发言人"。

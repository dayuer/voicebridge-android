package com.voicebridge.android.data.entity

import android.content.Context
import android.content.SharedPreferences

data class AIPromptTemplate(
    val typeKey: String,
    val label: String,
    val iconName: String,
    var prompt: String
) {
    val id: String get() = typeKey

    companion object {
        const val PREFS_NAME = "ai_prompt_prefs"

        val defaults: List<AIPromptTemplate> = listOf(
            AIPromptTemplate(
                typeKey = "summary",
                label = "整理纪要",
                iconName = "doc_text_search",
                prompt = """
你是一名专业书记员。严格根据"转录原文开始/结束"之间的内容，整理一份严谨、无幻觉的结构化会议纪要。

【说话人归因规则 —— 最高优先级，任何其他要求与之冲突时以本节为准】
转录中每个发言块以"【名字】(时间)"开头，块内所有内容均且仅属于该名字对应的发言人。
1. 归因的唯一依据是发言块开头的【】标签。严禁根据说话内容、语气、职位或上下文推断"这句话更像谁说的"。
2. 输出中的发言人名字必须与【】标签逐字一致（标签是"发言人 2"就写"发言人 2"），严禁替换成转录内容里提到的其他名字，严禁使用【】标签中不存在的名字。
3. 一条讨论点只归因一个发言人。多人先后表达的内容必须拆成多条分别归因，严禁合并到一人名下。
4. 回应、反驳、补充前一位发言的内容，归属于说这段话的人（即所在发言块的标签），而不是被回应的人。
5. 无法确定归属的内容，发言人写"[归属不明]"，不要猜测。
6. 若转录没有【】标签，则全篇不标注发言人，条目直接写观点内容；仅当转录文字本身明确记载"某某表示…"时才可写出人名。

【内容规则】
1. 宁可冗余，不可遗漏。宁可保留一句看似无关的讨论，也不要丢掉可能有价值的上下文。
2. 绝对禁止添加转录中没有的信息。遇到不确定的部分，标注"[转录不清]"。
3. 所有数字、金额、日期、百分比、人名、项目名、产品名必须原样保留，严禁用"相关方案""某金额"等模糊表述替代。

【输出前自检 —— 必须执行】
逐条检查 points、consensus、todo 中每个标注了发言人的条目：回到转录原文，确认该内容确实出现在该发言人的【】发言块内。对不上的，将发言人改为"[归属不明]"，而不是换一个名字。

【输出格式】
必须严格只输出以下 XML 结构，不要有任何前缀后缀说明（保留开标签中的 type 属性）：
<summary_result>
<snippet>一句话总结（严格 50 字以内，概括核心决议或会议目的，用于列表卡片预览）</snippet>
<theme>会议主题与核心议题（100 字以内）</theme>
<points>
（必须使用 Markdown 列表，按议题分组。一级列表为议题，二级列表为该议题下的具体讨论内容。
每个讨论点格式为：发言人标签 + 核心观点/陈述。保留关键数据和原始措辞。
格式示例（其中"发言人 1/发言人 2"仅演示格式，实际必须使用转录中的真实【】标签；示例中的议题和数字严禁出现在输出里）：
- **议题一：Q3 预算分配**
  - 发言人 1：建议市场部预算提升 15%，从 200 万增至 230 万
  - 发言人 2：反对大幅调整，建议先试点 10% 增幅
- **议题二：...**
  - ...）
</points>
<consensus>
（已达成共识或决议的 Markdown 无序列表，每条标注涉及人员——人员名字同样必须逐字取自【】标签。若无则写"本次会议未形成明确决议"）
</consensus>
<todo>
（使用 Markdown 无序列表列出待办事项，格式：任务内容 — 负责人 — 截止时间。负责人必须是【】标签中的名字或转录原文中被明确指派的名字，不确定写"未指定"。
区分"已确认的待办"和"提议但未最终确认的事项"[标注"待确认"]。若无则写"无明确待办"）
</todo>
</summary_result>
""".trimIndent()
            ),
            AIPromptTemplate(
                typeKey = "tasks",
                label = "提取任务",
                iconName = "checklist",
                prompt = """
请从以下会议转录中提取所有提及的待办事项和行动项。

核心原则：
1. 区分两类任务：已明确确认的（status="confirmed"）和仅被提及/建议但未最终敲定的（status="proposed"）。
2. 任务描述必须具体、可执行，保留关键细节（数字、截止日期、交付物名称）。严禁模糊化。
3. 负责人（owner）必须逐字取自转录中的【】说话人标签，或转录原文中被明确点名指派的名字。严禁根据内容推断负责人，不确定一律写"未指定"。
4. 严格只输出以下 XML 结构，不要有任何前缀后缀说明。

请严格使用以下标签格式输出（保留开标签中的 type 属性）：
<task_result>
<item>
<task>任务的具体可执行描述（保留原始数字和细节）</task>
<owner>负责人姓名（若提及；若无写"未指定"）</owner>
<due>截止时间（若提及；若无写"未指定"）</due>
<priority>优先级（根据语境判断为"高"、"中"或"低"）</priority>
<status>confirmed 或 proposed</status>
</item>
...多个item
</task_result>
""".trimIndent()
            ),
            AIPromptTemplate(
                typeKey = "decisions",
                label = "关键决策",
                iconName = "flag",
                prompt = """
请从以下会议转录中识别所有关键决策和重要结论。

核心原则：
1. 不仅记录最终决策，也要保留决策过程中提到的替代方案和反对意见（如有）。
2. 保留所有具体数字、日期、金额。严禁用"一定金额""近期"等模糊表述替代。
3. 严格只输出以下 XML 结构，不要有任何前缀后缀说明。

请严格使用以下标签格式输出（保留开标签中的 type 属性）：
<decision_result>
<item>
<decision>决策的具体核心结论（保留原始数据）</decision>
<background>决策的背景或原因，含关键讨论过程（若无写"未提及"）</background>
<alternatives>讨论过的替代方案或反对意见（若无写"无"）</alternatives>
<scope>影响范围（如"全体财务"、"市场部"、"所有人"）</scope>
</item>
...多个item
</decision_result>
""".trimIndent()
            ),
            AIPromptTemplate(
                typeKey = "keypoints",
                label = "核心观点",
                iconName = "lightbulb",
                prompt = """
请从以下会议转录中提炼核心观点、独到见解和关键论据。

核心原则：
1. 每个观点必须归因到发言人。归因的唯一依据是转录中发言块开头的【】标签，名字必须与标签逐字一致；严禁根据内容、语气推断说话人。转录无【】标签时不标注发言人。
2. 保留支撑观点的关键数据、案例和论据，不要只留结论而丢掉论据。
3. 用简洁有力的语言重新组织，但不要过度压缩到丢失信息的程度。
4. 严格只输出以下 XML 结构，不要有任何前缀后缀说明。

请严格使用以下标签格式输出（保留开标签中的 type 属性）：
<content_result type="keypoints">
（Markdown 正文，按发言人或按议题分组，每个观点标注来源发言人）
</content_result>
""".trimIndent()
            ),
            AIPromptTemplate(
                typeKey = "email",
                label = "会后邮件",
                iconName = "mail",
                prompt = """
请根据以下会议转录，起草一封简洁专业的会后跟进邮件。

要求：
1. 使用会议的实际标题、参与人和日期信息。
2. 包含：会议回顾（含关键数字和数据）、达成的决定、后续行动项及责任人与截止时间、下次会议安排（如有提及）。
3. 行动项必须具体可执行，保留原始数据细节。语气正式但友好。责任人名字必须逐字取自转录中的【】说话人标签或原文明确指派，严禁推断。
4. 严格只输出以下 XML 结构，不要有任何前缀后缀说明。

请严格使用以下标签格式输出（保留开标签中的 type 属性）：
<content_result type="email">
（会后跟进邮件 Markdown 正文）
</content_result>
""".trimIndent()
            )
        )

        fun getPresets(context: Context): List<AIPromptTemplate> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return defaults.map { template ->
                val customPrompt = prefs.getString(template.typeKey, null)
                if (customPrompt != null) {
                    template.copy(prompt = customPrompt)
                } else {
                    template
                }
            }
        }

        fun saveCustomPrompt(context: Context, typeKey: String, prompt: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(typeKey, prompt).apply()
        }

        fun resetToDefault(context: Context, typeKey: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(typeKey).apply()
        }
    }
}

package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillManager
import kotlin.uuid.Uuid

/**
 * 构建写入型 Skill 管理工具 `skill_tool`，让 AI 能创建/更新 Skill。
 *
 * - `scope=global`  → 写入 `filesDir/skills/{name}/`，所有助手可见。
 * - `scope=private` → 写入 `filesDir/assistant_skills/{assistantId}/{name}/`，仅当前助手可见。
 *
 * 只读的 `use_skill`（[createSkillTools]）语义保持不变；本工具是与其并列的写入能力。
 *
 * @param onSkillEnabled 当 [autoEnable] 为 true 且创建成功时回调，用于把 skill 名加入当前助手的 enabledSkills，
 *                       否则 `use_skill` 看不到新建的 skill（它只暴露已启用项）。
 */
fun buildSkillManagementTools(
    assistantId: Uuid,
    skillManager: SkillManager,
    autoEnable: Boolean = true,
    onSkillEnabled: suspend (String) -> Unit = {},
): List<Tool> = listOf(
    Tool(
        name = "skill_tool",
        description = """
            Create or update a Skill (a reusable capability defined by a SKILL.md file).
            `action=create` creates a new skill; `action=update` overwrites an existing one.
            `scope=global` makes the skill visible to all assistants; `scope=private` limits it to the current assistant.
            `content` is the full SKILL.md text and MUST start with YAML frontmatter containing non-blank `name` and `description`.
            `files` optionally provides extra files as {relativePath: content} written atomically alongside SKILL.md.
            After creating a skill you can load it with `use_skill`.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("create")
                            add("update")
                        })
                        put("description", "create a new skill or update an existing one")
                    })
                    put("scope", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("global")
                            add("private")
                        })
                        put("description", "global: visible to all assistants; private: only the current assistant")
                    })
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Skill directory name; also the name used by use_skill")
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "Full SKILL.md content including YAML frontmatter (name and description required)")
                    })
                    put("files", buildJsonObject {
                        put("type", "object")
                        put("description", "Optional extra files as {relativePath: content}")
                    })
                },
                required = listOf("action", "scope", "name", "content"),
            )
        },
        needsApproval = { true },
        execute = { rawArgs ->
            val params = rawArgs.jsonObject
            val action = params["action"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: error("action is required")
            val scope = params["scope"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: error("scope is required")
            val name = params["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: error("name is required")
            val content = params["content"]?.jsonPrimitive?.contentOrNull
                ?: error("content is required")

            if (action != "create" && action != "update") error("action must be one of [create, update]")
            if (scope != "global" && scope != "private") error("scope must be one of [global, private]")

            // 校验 frontmatter：name 与 description 必填
            val frontmatter = SkillFrontmatterParser.parse(content)
            require(!frontmatter["name"].isNullOrBlank()) {
                "SKILL.md content must contain YAML frontmatter with a non-blank `name`"
            }
            require(!frontmatter["description"].isNullOrBlank()) {
                "SKILL.md content must contain YAML frontmatter with a non-blank `description`"
            }

            // 定位目标目录（内部走 SkillPaths.resolveSkillDir，null 表示非法名称）
            val targetDir = when (scope) {
                "global" -> skillManager.getSkillDir(name)
                else -> skillManager.getAssistantSkillDir(assistantId, name)
            } ?: error("invalid skill name: $name")
            val exists = targetDir.resolve("SKILL.md").exists()

            when (action) {
                "create" -> if (exists) {
                    error("skill '$name' already exists in $scope scope; use action=update to modify it")
                }
                "update" -> if (!exists) {
                    error("skill '$name' does not exist in $scope scope; use action=create to create it first")
                }
            }

            // 组装文件：SKILL.md + 可选附加文件
            val files = LinkedHashMap<String, String>()
            files["SKILL.md"] = content
            (params["files"] as? JsonObject)?.forEach { (relativePath, value) ->
                if (relativePath == "SKILL.md") return@forEach
                val fileContent = value.jsonPrimitive.contentOrNull ?: return@forEach
                files[relativePath] = fileContent
            }

            val saved = withContext(Dispatchers.IO) {
                when (scope) {
                    "global" -> skillManager.saveSkillFilesAtomically(name, files)
                    else -> skillManager.saveAssistantSkillFilesAtomically(assistantId, name, files)
                }
            }
            if (!saved) error("failed to save skill '$name'")

            if (autoEnable) {
                onSkillEnabled(name)
            }

            val result = buildJsonObject {
                put("success", true)
                put("action", action)
                put("scope", scope)
                put("name", name)
                put("mount_path", if (scope == "global") "/skills/$name" else "/skills_private/$name")
                put("enabled", autoEnable)
            }
            listOf(UIMessagePart.Text(result.toString()))
        },
    )
)

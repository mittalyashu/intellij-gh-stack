package dev.mittalyashu.ghstack.cli

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.mittalyashu.ghstack.model.PullRequestRef
import dev.mittalyashu.ghstack.model.StackLayer
import dev.mittalyashu.ghstack.model.StackView
import dev.mittalyashu.ghstack.model.TrackedStack
import dev.mittalyashu.ghstack.model.TrackedStacks

object StackJson {
    fun parseView(json: String): StackView {
        val root = JsonParser.parseString(json).asObjectOrNull()
            ?: error("gh stack view --json did not return an object")
        val branches = root.getAsJsonArray("branches")?.mapNotNull { parseViewBranch(it) }.orEmpty()
        return StackView(
            trunk = root.string("trunk").orEmpty(),
            currentBranch = root.string("currentBranch").orEmpty(),
            branches = branches,
        )
    }

    fun parseTrackedFile(json: String): TrackedStacks {
        val root = JsonParser.parseString(json).asObjectOrNull()
            ?: error(".git/gh-stack is not a JSON object")
        val stacks = root.getAsJsonArray("stacks")?.mapNotNull { parseTrackedStack(it) }.orEmpty()
        return TrackedStacks(
            repository = root.string("repository"),
            stacks = stacks,
        )
    }

    private fun parseViewBranch(element: JsonElement): StackLayer? {
        val obj = element.asObjectOrNull() ?: return null
        val name = obj.string("name") ?: return null
        val prElement = obj.get("pr") ?: obj.get("pullRequest")
        return StackLayer(
            name = name,
            head = obj.string("head"),
            base = obj.string("base"),
            isCurrent = obj.boolean("isCurrent"),
            isMerged = obj.boolean("isMerged"),
            isQueued = obj.boolean("isQueued"),
            needsRebase = obj.boolean("needsRebase"),
            pr = parsePr(prElement),
        )
    }

    private fun parseTrackedStack(element: JsonElement): TrackedStack? {
        val obj = element.asObjectOrNull() ?: return null
        val trunk = obj.get("trunk")?.let { trunkEl ->
            trunkEl.asObjectOrNull()?.string("branch") ?: trunkEl.takeIf { it.isJsonPrimitive }?.asString
        } ?: return null
        val branches = obj.getAsJsonArray("branches")?.mapNotNull { branchEl ->
            branchEl.asObjectOrNull()?.string("branch") ?: branchEl.takeIf { it.isJsonPrimitive }?.asString
        }.orEmpty()
        return TrackedStack(
            id = obj.string("id"),
            number = obj.int("number")?.takeIf { it > 0 },
            trunk = trunk,
            branches = branches,
        )
    }

    private fun parsePr(element: JsonElement?): PullRequestRef? {
        val obj = element?.asObjectOrNull() ?: return null
        val number = obj.int("number") ?: return null
        return PullRequestRef(
            number = number,
            url = obj.string("url"),
            state = obj.string("state"),
            merged = obj.get("merged")?.takeIf { it.isJsonPrimitive }?.asBoolean,
        )
    }
}

private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.string(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull || !value.isJsonPrimitive) return null
    return value.asString
}

private fun JsonObject.boolean(name: String): Boolean {
    val value = get(name) ?: return false
    if (!value.isJsonPrimitive) return false
    return runCatching { value.asBoolean }.getOrDefault(false)
}

private fun JsonObject.int(name: String): Int? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive) return null
    return runCatching { value.asInt }.getOrNull()
}

private fun JsonObject.getAsJsonArray(name: String): JsonArray? {
    val value = get(name) ?: return null
    return value.takeIf { it.isJsonArray }?.asJsonArray
}

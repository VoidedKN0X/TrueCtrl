package com.vknxworks.truectrl.data.system

import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.double
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One question of a catalog app's install form (`schema.questions`, or a dict's `attrs`, or a
 * list's `items`). Everything is read leniently from the JSON: a field TrueCtrl doesn't know is
 * still shown, as raw text or JSON, so a new catalog never breaks the form.
 */
data class AppQuestion(
    val variable: String,
    val label: String,
    val description: String?,
    val group: String?,
    val schema: JsonObject,
) {
    val type: String get() = schema["type"].str() ?: "string"
    val required: Boolean get() = schema["required"].bool() ?: false
    val hidden: Boolean get() = schema["hidden"].bool() ?: false
    val editable: Boolean get() = schema["editable"].bool() ?: true
    /** Passwords and keys: typed masked and never shown in screenshots. */
    val secret: Boolean get() = schema["private"].bool() ?: false
    val nullable: Boolean get() = schema["null"].bool() ?: false
    val default: JsonElement? get() = schema["default"]
    val min: Long? get() = schema["min"].long()
    val max: Long? get() = schema["max"].long()
    val minLength: Int? get() = schema["min_length"].long()?.toInt()
    val maxLength: Int? get() = schema["max_length"].long()?.toInt()
    /** Like TrueNAS's check (Python re.match): the pattern must match from the start. */
    val validChars: Regex? get() = schema["valid_chars"].str()?.let { runCatching { Regex("^(?:$it)") }.getOrNull() }
    /** `[{value, description}]`: a choice. Values can be strings, numbers, booleans or null. */
    val choices: List<Pair<JsonElement, String>>?
        get() = schema["enum"].arr()?.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val v = o["value"] ?: JsonNull
            v to (o["description"].str()?.takeIf { it.isNotBlank() } ?: v.str() ?: "—")
        }?.takeIf { it.isNotEmpty() }
    /** `[[variable, operator, value], …]` against the sibling values; all must hold. */
    val showIf: JsonArray? get() = schema["show_if"].arr()?.takeIf { it.isNotEmpty() }
    val attrs: List<AppQuestion> get() = parseList(schema["attrs"])
    /** The schema of a list's items (TrueNAS uses exactly one). */
    val item: AppQuestion? get() = parseList(schema["items"]).firstOrNull()

    /** A port: TrueCtrl warns when another app already uses it. */
    val isPort: Boolean get() = type == "int" && (variable == "port_number" || variable.endsWith("_port") || variable == "port")

    val kind: FieldKind
        get() = when {
            choices != null && type in setOf("string", "int", "boolean", "text") -> FieldKind.ENUM
            else -> when (type) {
                "string", "text" -> FieldKind.TEXT
                "int" -> FieldKind.INT
                "boolean" -> FieldKind.BOOLEAN
                "path" -> FieldKind.PATH
                "hostpath" -> FieldKind.HOSTPATH
                "ipaddr", "uri" -> FieldKind.TEXT
                "dict" -> if (schema["attrs"].arr()?.isNotEmpty() == true) FieldKind.DICT else FieldKind.RAW
                "list" -> if (item != null) FieldKind.LIST else FieldKind.RAW
                else -> FieldKind.RAW
            }
        }

    /** The value for a new field or list item: the schema's default, or an empty value of the type. */
    fun initialValue(): JsonElement {
        default?.let { if (it !is JsonNull || nullable) return it }
        return when (kind) {
            FieldKind.BOOLEAN -> JsonPrimitive(false)
            FieldKind.INT -> if (nullable) JsonNull else JsonPrimitive(min ?: 0L)
            FieldKind.ENUM -> choices?.firstOrNull()?.first ?: JsonNull
            FieldKind.DICT -> JsonObject(attrs.associate { it.variable to it.initialValue() })
            FieldKind.LIST -> JsonArray(emptyList())
            FieldKind.RAW -> when (type) {
                "dict" -> JsonObject(emptyMap())
                "list" -> JsonArray(emptyList())
                else -> JsonNull
            }
            else -> if (nullable) JsonNull else JsonPrimitive("")
        }
    }

    companion object {
        fun parse(j: JsonElement?): AppQuestion? {
            val o = j.obj() ?: return null
            val variable = o["variable"].str() ?: return null
            return AppQuestion(
                variable = variable,
                label = o["label"].str()?.takeIf { it.isNotBlank() } ?: variable,
                description = o["description"].str()?.takeIf { it.isNotBlank() },
                group = o["group"].str(),
                schema = o["schema"].obj() ?: JsonObject(emptyMap()),
            )
        }

        fun parseList(j: JsonElement?): List<AppQuestion> = j.arr()?.mapNotNull { parse(it) }.orEmpty()
    }
}

enum class FieldKind { TEXT, INT, BOOLEAN, ENUM, PATH, HOSTPATH, DICT, LIST, RAW }

/** A form group (`schema.groups`): a heading and its questions. */
data class AppGroup(val name: String, val description: String?, val questions: List<AppQuestion>)

/**
 * The install form of one app version: its questions in their groups, the values being edited
 * (starting from TrueNAS's own defaults for the version), and helpers to read and change them.
 */
object AppForm {
    fun groups(schema: JsonObject?): List<AppGroup> {
        val questions = AppQuestion.parseList(schema?.get("questions"))
        val declared = schema?.get("groups").arr().orEmpty().mapNotNull { g ->
            val o = g.obj() ?: return@mapNotNull null
            val name = o["name"].str() ?: return@mapNotNull null
            name to o["description"].str()?.takeIf { it.isNotBlank() }
        }
        val known = declared.map { it.first }.toSet()
        val result = declared.map { (name, desc) -> AppGroup(name, desc, questions.filter { it.group == name }) }.toMutableList()
        val rest = questions.filter { it.group == null || it.group !in known }
        if (rest.isNotEmpty()) result += AppGroup("", null, rest)
        return result.filter { g -> g.questions.any { !it.hidden } }
    }

    /** The starting values: the version's defaults from TrueNAS, filled in with each question's own default. */
    fun initialValues(schema: JsonObject?, defaults: JsonObject?): JsonObject =
        fill(AppQuestion.parseList(schema?.get("questions")), defaults ?: JsonObject(emptyMap()))

    private fun fill(questions: List<AppQuestion>, values: JsonObject): JsonObject {
        val out = values.toMutableMap()
        for (q in questions) {
            val v = out[q.variable]
            out[q.variable] = when {
                v == null -> q.initialValue().let { if (q.kind == FieldKind.DICT && it is JsonObject) fill(q.attrs, it) else it }
                q.kind == FieldKind.DICT && v is JsonObject -> fill(q.attrs, v)
                else -> v
            }
        }
        return JsonObject(out)
    }

    /** Whether [q] is shown, given its sibling [values] (its show_if). Unknown operators count as shown. */
    fun visible(q: AppQuestion, values: JsonObject?): Boolean {
        val cond = q.showIf ?: return true
        return cond.all { c ->
            val parts = c.arr() ?: return@all true
            val key = parts.getOrNull(0).str() ?: return@all true
            val op = parts.getOrNull(1).str() ?: "="
            val want = parts.getOrNull(2) ?: JsonNull
            val have = values?.get(key) ?: JsonNull
            when (op) {
                "=" -> same(have, want)
                "!=" -> !same(have, want)
                "in" -> want.arr()?.any { same(have, it) } ?: true
                "nin" -> want.arr()?.none { same(have, it) } ?: true
                ">" -> cmp(have, want)?.let { it > 0 } ?: true
                ">=" -> cmp(have, want)?.let { it >= 0 } ?: true
                "<" -> cmp(have, want)?.let { it < 0 } ?: true
                "<=" -> cmp(have, want)?.let { it <= 0 } ?: true
                else -> true
            }
        }
    }

    private fun same(a: JsonElement, b: JsonElement): Boolean {
        if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
        if (a is JsonPrimitive && b is JsonPrimitive) {
            val da = a.double()
            val db = b.double()
            if (da != null && db != null && a.bool() == null && b.bool() == null) return da == db
            return a.content == b.content
        }
        return a == b
    }

    private fun cmp(a: JsonElement, b: JsonElement): Int? {
        val da = a.double() ?: return null
        val db = b.double() ?: return null
        return da.compareTo(db)
    }

    /**
     * What's sent to app.create: the values with every field that's hidden by its show_if left
     * out (TrueNAS would otherwise validate them, like the web UI doesn't send them either).
     */
    fun prune(questions: List<AppQuestion>, values: JsonObject): JsonObject {
        val known = questions.associateBy { it.variable }
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, v) in values) {
            val q = known[k]
            if (q == null) {
                out[k] = v
                continue
            }
            if (!visible(q, values)) continue
            out[k] = pruneValue(q, v)
        }
        return JsonObject(out)
    }

    private fun pruneValue(q: AppQuestion, v: JsonElement): JsonElement = when {
        q.kind == FieldKind.DICT && v is JsonObject -> prune(q.attrs, v)
        q.kind == FieldKind.LIST && v is JsonArray -> {
            val item = q.item
            if (item == null) v else JsonArray(v.map { pruneValue(item, it) })
        }
        else -> v
    }

    /**
     * The first problem with the visible [values], as the field's label path and what's wrong;
     * null when everything that's checked locally is fine (TrueNAS checks the rest).
     */
    fun firstProblem(questions: List<AppQuestion>, values: JsonObject, prefix: String = ""): Pair<String, Problem>? {
        for (q in questions) {
            if (!visible(q, values) || q.hidden) continue
            val v = values[q.variable] ?: JsonNull
            val where = if (prefix.isEmpty()) q.label else "$prefix › ${q.label}"
            problem(q, v)?.let { return where to it }
            when {
                q.kind == FieldKind.DICT && v is JsonObject -> firstProblem(q.attrs, v, where)?.let { return it }
                q.kind == FieldKind.LIST && v is JsonArray -> {
                    val item = q.item ?: continue
                    v.forEachIndexed { i, e ->
                        val w = "$where ${i + 1}"
                        if (item.kind == FieldKind.DICT && e is JsonObject) {
                            firstProblem(item.attrs, e, w)?.let { return it }
                        } else {
                            problem(item, e)?.let { return w to it }
                        }
                    }
                }
            }
        }
        return null
    }

    enum class Problem { REQUIRED, NUMBER, RANGE, LENGTH, CHARS, JSON }

    fun problem(q: AppQuestion, v: JsonElement): Problem? {
        val empty = v is JsonNull || (v is JsonPrimitive && v.isString && v.content.isEmpty())
        if (q.required && empty && q.kind !in setOf(FieldKind.BOOLEAN, FieldKind.DICT, FieldKind.LIST)) return Problem.REQUIRED
        // An empty number that can't be null can't be sent.
        if (empty && q.kind == FieldKind.INT && !q.nullable) return Problem.REQUIRED
        if (empty) return null
        when (q.kind) {
            FieldKind.INT -> {
                val n = (v as? JsonPrimitive)?.takeIf { !it.isString }?.long() ?: return Problem.NUMBER
                if ((q.min != null && n < q.min!!) || (q.max != null && n > q.max!!)) return Problem.RANGE
            }
            FieldKind.TEXT, FieldKind.PATH, FieldKind.HOSTPATH -> {
                val s = v.str() ?: return null
                if ((q.minLength != null && s.length < q.minLength!!) || (q.maxLength != null && s.length > q.maxLength!!)) return Problem.LENGTH
                val chars = q.validChars
                if (chars != null && !chars.containsMatchIn(s)) return Problem.CHARS
            }
            FieldKind.LIST -> {
                val size = (v as? JsonArray)?.size ?: return null
                if ((q.min != null && size < q.min!!) || (q.max != null && size > q.max!!)) return Problem.RANGE
            }
            else -> Unit
        }
        return null
    }

    /** Text for a raw (unknown type) field: a string as it is, anything else as JSON. */
    fun rawText(v: JsonElement): String = when {
        v is JsonNull -> ""
        v is JsonPrimitive && v.isString -> v.content
        else -> v.toString()
    }

    /** Reads a raw field back: JSON when it parses as JSON, otherwise the text as a string; null when invalid JSON was meant. */
    fun parseRaw(text: String, type: String): JsonElement? {
        val t = text.trim()
        if (t.isEmpty()) return when (type) {
            "dict" -> JsonObject(emptyMap())
            "list" -> JsonArray(emptyList())
            else -> JsonNull
        }
        val looksJson = t.startsWith("{") || t.startsWith("[") || type == "dict" || type == "list"
        return if (looksJson) {
            runCatching { TnJson.parseToJsonElement(t) }.getOrNull()
        } else {
            JsonPrimitive(text)
        }
    }

    /** Every question at any depth is private (passwords): the screen then blocks screenshots. */
    fun anyPrivate(questions: List<AppQuestion>): Boolean = questions.any { q ->
        q.secret || anyPrivate(q.attrs) || (q.item?.let { anyPrivate(listOf(it)) } ?: false)
    }

    /** App names TrueNAS accepts: lowercase letters, digits and hyphens, starting with a letter, up to 40. */
    val APP_NAME = Regex("^[a-z]([-a-z0-9]*[a-z0-9])?$")

    fun appNameValid(name: String): Boolean = name.length in 1..40 && APP_NAME.matches(name)

    /** Ports used by [values] at any depth (port fields that are set), to check against other apps. */
    fun ports(questions: List<AppQuestion>, values: JsonObject?): List<Long> {
        if (values == null) return emptyList()
        return questions.flatMap { q ->
            if (!visible(q, values)) return@flatMap emptyList()
            val v = values[q.variable]
            when {
                q.isPort -> listOfNotNull(v.long())
                q.kind == FieldKind.DICT -> ports(q.attrs, v.obj())
                else -> emptyList()
            }
        }
    }
}

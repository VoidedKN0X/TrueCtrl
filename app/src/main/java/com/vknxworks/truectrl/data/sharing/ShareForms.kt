package com.vknxworks.truectrl.data.sharing

import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the SMB share editor edits. TrueNAS 25.10 moved `ro`/`abe` to `readonly` and
 * `access_based_share_enumeration`, and guest access into the purpose `options` (only the
 * LEGACY_SHARE purpose has it); 25.04 has them all as flat fields. [toPayload] writes the right shape.
 */
data class SmbForm(
    val name: String = "",
    val path: String = "",
    val purpose: String = DEFAULT_PURPOSE,
    val comment: String = "",
    val enabled: Boolean = true,
    val readonly: Boolean = false,
    val browsable: Boolean = true,
    val abe: Boolean = false,
    val guestOk: Boolean = false,
    /** 25.10: the existing share's purpose options, kept as they are when the purpose doesn't change. */
    val options: JsonObject? = null,
    val originalPurpose: String? = null,
) {
    /** Guest access can be set: always on 25.04, only for the legacy purpose from 25.10 on. */
    fun guestAvailable(newApi: Boolean): Boolean = !newApi || purpose == LEGACY_PURPOSE

    fun toPayload(newApi: Boolean): JsonObject = buildJsonObject {
        put("name", name.trim())
        put("path", path.trim())
        put("purpose", purpose)
        put("comment", comment)
        put("enabled", enabled)
        put("browsable", browsable)
        if (newApi) {
            put("readonly", readonly)
            put("access_based_share_enumeration", abe)
            // TrueNAS needs `options` whenever `purpose` is sent. Keep the share's own options when the
            // purpose stays the same; a new purpose starts from its defaults.
            val base = if (purpose == originalPurpose && options != null) options else JsonObject(emptyMap())
            val opts = base.toMutableMap()
            opts.remove("purpose")
            if (purpose == LEGACY_PURPOSE) opts["guestok"] = JsonPrimitive(guestOk) else opts.remove("guestok")
            put("options", JsonObject(opts))
        } else {
            put("ro", readonly)
            put("abe", abe)
            put("guestok", guestOk)
        }
    }

    companion object {
        const val DEFAULT_PURPOSE = "DEFAULT_SHARE"
        const val LEGACY_PURPOSE = "LEGACY_SHARE"

        /** Purposes TrueCtrl doesn't offer for new shares: they need settings it doesn't edit, or TrueNAS Enterprise. */
        val HIDDEN_PURPOSES = setOf("EXTERNAL_SHARE", "VEEAM_REPOSITORY_SHARE", "FCP_SHARE")

        private val ILLEGAL = "\\/[]:|<>+=;,*?\"%".toSet()
        private val RESERVED = setOf("global", "printers", "homes")

        /** A share name TrueNAS accepts: up to 80 characters, none of `\ / [ ] : | < > + = ; , * ? " %`. */
        fun nameValid(name: String): Boolean {
            val n = name.trim()
            return n.isNotEmpty() && n.length <= 80 && n.none { it in ILLEGAL || it.isISOControl() } && n.lowercase() !in RESERVED
        }

        fun from(o: JsonObject): SmbForm {
            val opts = o["options"].obj()
            val purpose = o["purpose"].str() ?: DEFAULT_PURPOSE
            return SmbForm(
                name = o["name"].str().orEmpty(),
                path = o["path"].str().orEmpty(),
                purpose = purpose,
                comment = o["comment"].str().orEmpty(),
                enabled = o["enabled"].bool() ?: true,
                readonly = o["readonly"].bool() ?: o["ro"].bool() ?: false,
                browsable = o["browsable"].bool() ?: true,
                abe = o["access_based_share_enumeration"].bool() ?: o["abe"].bool() ?: false,
                guestOk = opts?.get("guestok").bool() ?: o["guestok"].bool() ?: false,
                options = opts,
                originalPurpose = purpose,
            )
        }
    }
}

/** What the NFS share editor edits. The same fields on 25.04 and 25.10. */
data class NfsForm(
    val path: String = "",
    val comment: String = "",
    /** One network (CIDR) per line. Empty: every network. */
    val networks: String = "",
    /** One host or IP per line. Empty: every host. */
    val hosts: String = "",
    val readonly: Boolean = false,
    val enabled: Boolean = true,
    val maprootUser: String = "",
    val maprootGroup: String = "",
    val mapallUser: String = "",
    val mapallGroup: String = "",
    /** SYS, KRB5, KRB5I, KRB5P. Empty: TrueNAS's default. */
    val security: Set<String> = emptySet(),
) {
    val networkList: List<String> get() = split(networks)
    val hostList: List<String> get() = split(hosts)

    /** Map root and map all can't both be set. */
    val mappingConflict: Boolean
        get() = (maprootUser.isNotBlank() || maprootGroup.isNotBlank()) && (mapallUser.isNotBlank() || mapallGroup.isNotBlank())

    fun toPayload(): JsonObject = buildJsonObject {
        put("path", path.trim())
        put("comment", comment)
        put("networks", JsonArray(networkList.map { JsonPrimitive(it) }))
        put("hosts", JsonArray(hostList.map { JsonPrimitive(it) }))
        put("ro", readonly)
        put("enabled", enabled)
        put("maproot_user", orNull(maprootUser))
        put("maproot_group", orNull(maprootGroup))
        put("mapall_user", orNull(mapallUser))
        put("mapall_group", orNull(mapallGroup))
        put("security", JsonArray(SECURITY.filter { it in security }.map { JsonPrimitive(it) }))
    }

    companion object {
        val SECURITY = listOf("SYS", "KRB5", "KRB5I", "KRB5P")
        const val MAX_ENTRIES = 42

        private fun split(text: String): List<String> =
            text.split('\n', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        private fun orNull(s: String): JsonElement = s.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull

        fun from(o: JsonObject): NfsForm = NfsForm(
            path = o["path"].str() ?: o["paths"].arr()?.firstOrNull().str().orEmpty(),
            comment = o["comment"].str().orEmpty(),
            networks = o["networks"].arr().orEmpty().mapNotNull { it.str() }.joinToString("\n"),
            hosts = o["hosts"].arr().orEmpty().mapNotNull { it.str() }.joinToString("\n"),
            readonly = o["ro"].bool() ?: o["readonly"].bool() ?: false,
            enabled = o["enabled"].bool() ?: true,
            maprootUser = o["maproot_user"].str().orEmpty(),
            maprootGroup = o["maproot_group"].str().orEmpty(),
            mapallUser = o["mapall_user"].str().orEmpty(),
            mapallGroup = o["mapall_group"].str().orEmpty(),
            security = o["security"].arr().orEmpty().mapNotNull { it.str() }.toSet(),
        )
    }
}

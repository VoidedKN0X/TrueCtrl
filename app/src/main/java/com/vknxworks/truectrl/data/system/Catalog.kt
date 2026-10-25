package com.vknxworks.truectrl.data.system

import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.bool
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.obj
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.server.Connection
import com.vknxworks.truectrl.data.server.Jobs
import com.vknxworks.truectrl.data.server.Sessions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** An app in the TrueNAS catalog, from app.available. */
data class CatalogApp(
    val name: String,
    val title: String,
    val description: String,
    val train: String,
    val categories: List<String>,
    /** The catalog version (what app.create's `version` takes). */
    val latestVersion: String?,
    /** The upstream app's own version, like 10.10.3. */
    val appVersion: String?,
    val iconUrl: String?,
    val installed: Boolean,
    val recommended: Boolean,
    val popularity: Int?,
    val tags: List<String>,
) {
    companion object {
        fun from(j: JsonElement): CatalogApp? {
            val o = j.obj() ?: return null
            val name = o["name"].str() ?: return null
            return CatalogApp(
                name = name,
                title = o["title"].str()?.takeIf { it.isNotBlank() } ?: name,
                description = o["description"].str().orEmpty(),
                train = o["train"].str() ?: "stable",
                categories = o["categories"].arr().orEmpty().mapNotNull { it.str() },
                latestVersion = o["latest_version"].str(),
                appVersion = o["latest_app_version"].str() ?: o["latest_human_version"].str(),
                iconUrl = o["icon_url"].str(),
                installed = o["installed"].bool() ?: false,
                recommended = o["recommended"].bool() ?: false,
                popularity = o["popularity_rank"].long()?.toInt(),
                tags = o["tags"].arr().orEmpty().mapNotNull { it.str() },
            )
        }
    }
}

/** One version of a catalog app, with the questions its install form is built from. */
data class CatalogVersion(
    val version: String,
    val humanVersion: String,
    val healthy: Boolean,
    /** The `schema` of the version: `groups` and `questions`. */
    val schema: JsonObject?,
    /** The version's default values, normalised by TrueNAS. */
    val values: JsonObject?,
)

/** A catalog app's page, from catalog.get_app_details. */
data class CatalogDetail(
    val app: CatalogApp,
    /** HTML from the catalog. */
    val readme: String?,
    val home: String?,
    val sources: List<String>,
    val screenshots: List<String>,
    val maintainers: List<String>,
    val versions: List<CatalogVersion>,
) {
    companion object {
        fun from(j: JsonElement, train: String): CatalogDetail? {
            val o = j.obj() ?: return null
            val app = CatalogApp.from(JsonObject(o + ("train" to (o["train"] ?: JsonPrimitive(train))))) ?: return null
            val versions = o["versions"].obj()?.mapNotNull { (key, v) ->
                val vo = v.obj() ?: return@mapNotNull null
                CatalogVersion(
                    version = vo["version"].str() ?: key,
                    humanVersion = vo["human_version"].str() ?: vo["version"].str() ?: key,
                    healthy = vo["healthy"].bool() ?: true,
                    schema = vo["schema"].obj() ?: vo.takeIf { it["questions"] != null },
                    values = vo["values"].obj(),
                )
            }.orEmpty().sortedWith { a, b -> compareVersions(b.version, a.version) }
            return CatalogDetail(
                app = app,
                readme = o["app_readme"].str()?.takeIf { it.isNotBlank() },
                home = o["home"].str()?.takeIf { it.isNotBlank() },
                sources = o["sources"].arr().orEmpty().mapNotNull { it.str() },
                screenshots = o["screenshots"].arr().orEmpty().mapNotNull { it.str() },
                maintainers = o["maintainers"].arr().orEmpty().mapNotNull { m -> m["name"].str()?.takeIf { it.isNotBlank() } },
                versions = versions,
            )
        }

        /** 1.2.10 > 1.2.9: number by number, falling back to text. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.', '-')
            val pb = b.split('.', '-')
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrNull(i)
                val y = pb.getOrNull(i)
                if (x == null) return -1
                if (y == null) return 1
                val nx = x.toLongOrNull()
                val ny = y.toLongOrNull()
                val d = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
                if (d != 0) return d
            }
            return 0
        }
    }
}

/** The app catalog: browsing (app.available, app.categories), app details, installing and uninstalling. */
class CatalogRepository(private val sessions: Sessions) {

    private fun conn(): Connection = sessions.active.value?.connection ?: throw TnError.Unreachable(null)

    private var cacheServer: String? = null
    private var cache: List<CatalogApp>? = null

    /** The catalog as last loaded for the active server, without asking TrueNAS again. */
    fun cached(): List<CatalogApp>? = cache?.takeIf { cacheServer == sessions.active.value?.server?.id }

    private val listFields = listOf(
        "name", "title", "description", "train", "categories", "latest_version", "latest_app_version",
        "latest_human_version", "icon_url", "installed", "recommended", "popularity_rank", "tags",
    )

    /**
     * Every app in the catalog's preferred trains. Asks only for the fields the list needs (each
     * app's readme makes the full answer large), and asks for everything if TrueNAS refuses that.
     * The first call after a boot can take a while: TrueNAS syncs the catalog first.
     */
    suspend fun available(): List<CatalogApp> {
        val c = conn()
        val options = buildJsonObject { put("select", JsonArray(listFields.map { JsonPrimitive(it) })) }
        val result = try {
            c.call("app.available", listOf(JsonArray(emptyList()), options))
        } catch (e: TnError.Call) {
            c.call("app.available")
        }
        val list = result.arr()?.mapNotNull(CatalogApp::from).orEmpty()
            .sortedWith(compareBy<CatalogApp> { it.popularity ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
        cacheServer = sessions.active.value?.server?.id
        cache = list
        return list
    }

    suspend fun categories(): List<String> =
        runCatching { conn().call("app.categories").arr()?.mapNotNull { it.str() } }.getOrNull().orEmpty()

    suspend fun details(name: String, train: String): CatalogDetail? {
        val r = conn().call("catalog.get_app_details", listOf(JsonPrimitive(name), buildJsonObject { put("train", train) }))
        return CatalogDetail.from(r, train)
    }

    /** Host ports already used by installed apps, to warn before a clash. Empty when unknown. */
    suspend fun usedPorts(): Set<Long> {
        val caps = sessions.access.value.capabilities
        if (caps?.has("app.used_ports") != true) return emptySet()
        return runCatching { conn().call("app.used_ports").arr()?.mapNotNull { it.long() }?.toSet() }.getOrNull().orEmpty()
    }

    /**
     * Installs [catalogApp] as [appName] with [values] (app.create, a job). Returns the job's ID as
     * soon as TrueNAS has accepted it; errors in the values come back at once.
     */
    suspend fun install(catalogApp: String, appName: String, train: String, version: String, values: JsonObject): Long? {
        val c = conn()
        val args = buildJsonObject {
            put("custom_app", false)
            put("catalog_app", catalogApp)
            put("app_name", appName)
            put("train", train)
            put("version", version)
            put("values", values)
        }
        val result = c.call("app.create", listOf(args))
        Jobs.awaitStart(c, result, graceMs = 5_000)
        return result.long()
    }

    /**
     * Uninstalls [appName] (app.delete, a job) and waits for it, up to 10 minutes. Its images are
     * removed; its ix-volumes (data TrueNAS created for it) only with [removeIxVolumes].
     */
    suspend fun uninstall(appName: String, removeIxVolumes: Boolean) {
        val c = conn()
        val options = buildJsonObject {
            put("remove_images", true)
            put("remove_ix_volumes", removeIxVolumes)
        }
        Jobs.awaitResult(c, c.call("app.delete", listOf(JsonPrimitive(appName), options)), timeoutMs = 600_000)
    }

    fun forget() {
        cache = null
        cacheServer = null
    }
}

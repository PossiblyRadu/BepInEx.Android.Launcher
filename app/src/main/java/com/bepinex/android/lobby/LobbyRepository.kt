package com.bepinex.android.lobby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A modded Among Us region (an Impostor-style HTTP matchmaker). */
data class LobbyRegion(
    val name: String,
    val baseUrl: String
)

/** One public lobby as reported by a region's matchmaker. */
data class Lobby(
    val code: String,
    val players: Int,
    val maxPlayers: Int,
    val hostName: String,
    val mapName: String,
    val impostors: Int
)

/** Result of querying one region. */
sealed class RegionResult {
    data class Success(val lobbies: List<Lobby>) : RegionResult()
    data class Failure(val message: String) : RegionResult()
}

object LobbyRepository {

    private const val REGION_INFO_URL = "https://moddedamong.us/data/regioninfo.json"
    private const val TIMEOUT_MS = 8000

    /** Used when the online region list cannot be downloaded. Source: moddedamong.us/regions. */
    val builtInRegions = listOf(
        LobbyRegion("MAUL-NA", "https://au.moddedamong.us"),
        LobbyRegion("MAUL-EU", "https://eu.moddedamong.us"),
        LobbyRegion("Niko233(NA)", "https://au-us.niko233.top"),
        LobbyRegion("Niko233(EU)", "https://au-eu.niko233.top"),
        LobbyRegion("Niko233(AS)", "https://au-as.niko233.top"),
        LobbyRegion("Niko233(CN1)", "https://au-cn.niko233.top"),
        LobbyRegion("AOU-Europe", "https://eu.allofus.dev"),
        LobbyRegion("Modded-NA", "https://aumods.org"),
        LobbyRegion("Modded-EU", "https://au-eu.duikbo.at"),
        LobbyRegion("Modded-Asia", "https://au-as.duikbo.at"),
        LobbyRegion("Jarne's-MEU", "https://modmanagere.eu.amongusclub.cn")
    )

    /**
     * Loads the modded region list. Official Innersloth regions (North America, Europe, Asia)
     * are left out: their matchmaker needs real account authentication.
     */
    suspend fun loadRegions(): List<LobbyRegion> = withContext(Dispatchers.IO) {
        try {
            val text = httpRequest(REGION_INFO_URL, "GET", null, null)
            val regions = JSONObject(text).getJSONArray("Regions")
            val result = mutableListOf<LobbyRegion>()
            for (i in 0 until regions.length()) {
                val region = regions.getJSONObject(i)
                val name = region.optString("Name")
                val servers = region.optJSONArray("Servers") ?: continue
                if (servers.length() == 0) continue
                var url = servers.getJSONObject(0).optString("Ip")
                if (name.isBlank() || url.isBlank()) continue
                if (!url.startsWith("http")) url = "https://$url"
                if (isOfficialHost(url)) continue
                result.add(LobbyRegion(name, url.trimEnd('/')))
            }
            if (result.isEmpty()) builtInRegions else result
        } catch (_: Exception) {
            builtInRegions
        }
    }

    /** Queries all given regions at the same time. */
    suspend fun fetchAll(
        regions: List<LobbyRegion>,
        clientVersion: Int
    ): Map<String, RegionResult> = coroutineScope {
        regions.map { region ->
            async(Dispatchers.IO) { region.name to fetchLobbies(region, clientVersion) }
        }.awaitAll().toMap()
    }

    fun fetchLobbies(region: LobbyRegion, clientVersion: Int): RegionResult {
        return try {
            val token = requestToken(region.baseUrl, clientVersion)
            val body = httpRequest(
                "${region.baseUrl}/api/games?mapId=0&lang=0&numImpostors=0",
                "GET",
                null,
                token
            )
            RegionResult.Success(parseListings(body))
        } catch (e: Exception) {
            RegionResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun requestToken(baseUrl: String, clientVersion: Int): String {
        val payload = JSONObject()
            .put("Puid", UUID.randomUUID().toString().replace("-", ""))
            .put("Username", "LobbyBrowser")
            .put("ClientVersion", clientVersion)
            .put("Language", 0)
            .toString()
        val response = httpRequest("$baseUrl/api/user", "POST", payload, null).trim()
        return response.removeSurrounding("\"")
    }

    private fun parseListings(body: String): List<Lobby> {
        val trimmed = body.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val obj = JSONObject(trimmed)
            obj.optJSONArray("games") ?: obj.optJSONArray("Games") ?: JSONArray()
        }
        val lobbies = mutableListOf<Lobby>()
        for (i in 0 until array.length()) {
            val game = array.getJSONObject(i)
            lobbies.add(
                Lobby(
                    code = gameCodeToString(game.optInt("GameId")),
                    players = game.optInt("PlayerCount"),
                    maxPlayers = game.optInt("MaxPlayers"),
                    hostName = game.optString("HostName").ifBlank { "Unknown host" },
                    mapName = mapName(game.optInt("MapId")),
                    impostors = game.optInt("NumImpostors")
                )
            )
        }
        return lobbies
    }

    private fun httpRequest(url: String, method: String, body: String?, bearer: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun isOfficialHost(url: String): Boolean {
        val host = runCatching { URL(url).host }.getOrDefault("")
        return host.endsWith("among.us")
    }

    private const val V2 = "QWXRTYLPESDFGHUJKZOCVBINMA"

    /** Converts the numeric game id from the matchmaker into the code players type in. */
    fun gameCodeToString(input: Int): String {
        if (input < -1) {
            val a = input and 0x3FF
            val b = (input shr 10) and 0xFFFFF
            val indexes = intArrayOf(
                a % 26,
                a / 26,
                b % 26,
                (b / 26) % 26,
                (b / (26 * 26)) % 26,
                (b / (26 * 26 * 26)) % 26
            )
            return indexes.map { V2.getOrElse(it) { '?' } }.joinToString("")
        }
        val chars = CharArray(4) { i -> ((input shr (8 * i)) and 0xFF).toChar() }
        return String(chars)
    }

    private fun mapName(id: Int): String = when (id) {
        0 -> "The Skeld"
        1 -> "MIRA HQ"
        2 -> "Polus"
        3 -> "Dleks"
        4 -> "Airship"
        5 -> "The Fungle"
        else -> "Map $id"
    }

    /**
     * Turns a version name such as "2026.9.9" into the integer Among Us sends to matchmakers.
     * Returns null when the name is not in the year.month.day[.revision] form.
     */
    fun encodeVersion(versionName: String): Int? {
        val parts = versionName.trim().split(".")
        if (parts.size < 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        val revision = parts.getOrNull(3)?.toIntOrNull() ?: 0
        if (year < 2000) return null
        return year * 25000 + month * 1800 + day * 50 + revision
    }
}

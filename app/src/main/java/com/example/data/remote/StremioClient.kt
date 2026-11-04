package com.example.data.remote

import android.util.Log
import com.example.data.model.CatalogResponse
import com.example.data.model.EpisodeItem
import com.example.data.model.MetaResponse
import com.example.data.model.StremioCatalogMeta
import com.example.data.model.StremioManifest
import com.example.data.model.StremioStream
import com.example.data.model.TrailerItem
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StremioClient {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Stremio/1.6.11")
                .header("Accept", "application/json, text/plain, */*")
                .build()
            chain.proceed(request)
        }
        .build()

    companion object {
        const val NETFLIX_CATALOG_BASE = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club"
        const val HDHUB_STREAM_BASE = "https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyJ9"
        const val PENGU_STREAM_BASE = "https://pengu.uk/%7B%22auth_token%22%3A%22IR2sr-l13r2YcdnTYkmmI_3rrmgsnNWKOdzALLL1vKs%22%7D"
        const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
        const val TMDB_API_BASE = "https://api.themoviedb.org/3"
        const val TMDB_API_KEY = "b2eba0c2c3f1a6b7ee2728f0520de526"

        val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.moomoo.me",
            "https://pipedapi.smnz.de",
            "https://api.piped.projectsegfau.lt"
        )
    }

    private val trailerCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val resolutionInfoCache = java.util.concurrent.ConcurrentHashMap<String, com.example.data.model.TrailerResolutionInfo>()

    suspend fun getCatalogManifest(): Result<StremioManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$NETFLIX_CATALOG_BASE/manifest.json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Failed to fetch manifest: ${response.code}")
            }
            val json = JSONObject(body)
            val catalogsJson = json.optJSONArray("catalogs")
            val catalogs = mutableListOf<com.example.data.model.StremioCatalogDefinition>()
            if (catalogsJson != null) {
                for (i in 0 until catalogsJson.length()) {
                    val cat = catalogsJson.getJSONObject(i)
                    catalogs.add(
                        com.example.data.model.StremioCatalogDefinition(
                            id = cat.optString("id"),
                            type = cat.optString("type"),
                            name = cat.optString("name")
                        )
                    )
                }
            }
            StremioManifest(
                id = json.optString("id"),
                name = json.optString("name"),
                version = json.optString("version"),
                description = json.optString("description"),
                catalogs = catalogs
            )
        }
    }

    suspend fun getCatalogItems(type: String, catalogId: String): Result<List<StremioCatalogMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$NETFLIX_CATALOG_BASE/catalog/$type/$catalogId.json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Failed to fetch catalog: ${response.code}")
            }
            val parsed = jsonParser.decodeFromString<CatalogResponse>(body)
            parsed.metas
        }
    }

    suspend fun getCinemetaCatalog(type: String, catalogId: String = "top"): Result<List<StremioCatalogMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$CINEMETA_BASE/catalog/$type/$catalogId.json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Failed to fetch Cinemeta catalog: ${response.code}")
            }
            val parsed = jsonParser.decodeFromString<CatalogResponse>(body)
            parsed.metas
        }
    }

    suspend fun searchCatalog(query: String, type: String = "movie"): Result<List<StremioCatalogMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$CINEMETA_BASE/catalog/$type/top/search=$encodedQuery.json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Search failed with code: ${response.code}")
            }
            val parsed = jsonParser.decodeFromString<CatalogResponse>(body)
            parsed.metas
        }
    }

    /**
     * Parallel progressive stream fetching:
     * HDHub4u -> Streamer 1 (First)
     * PenguPlay -> Streamer 2 (Second)
     * Streams strictly filtered to remove anything below 720p, >= 10GB, and download-only links!
     */
    fun getStreamsProgressive(
        type: String,
        id: String,
        hdhubEnabled: Boolean = true,
        penguEnabled: Boolean = true
    ): kotlinx.coroutines.flow.Flow<List<StremioStream>> = kotlinx.coroutines.flow.flow {
        val allAccumulated = java.util.Collections.synchronizedList(mutableListOf<StremioStream>())

        coroutineScope {
            val totalSources = (if (hdhubEnabled) 1 else 0) + (if (penguEnabled) 1 else 0)
            if (totalSources == 0) {
                emit(emptyList())
                return@coroutineScope
            }

            val channel = Channel<List<StremioStream>>(totalSources)

            if (hdhubEnabled) {
                launch(Dispatchers.IO) {
                    val list = runCatching { fetchHdhubStreams(type, id) }.getOrDefault(emptyList())
                    channel.send(list)
                }
            }
            if (penguEnabled) {
                launch(Dispatchers.IO) {
                    val list = runCatching { fetchPenguStreams(type, id) }.getOrDefault(emptyList())
                    channel.send(list)
                }
            }

            var received = 0
            while (received < totalSources) {
                val incoming = channel.receive()
                received++
                if (incoming.isNotEmpty()) {
                    synchronized(allAccumulated) {
                        allAccumulated.addAll(incoming.filterNot { it.isBelow720p })
                    }
                    val currentList = synchronized(allAccumulated) { allAccumulated.toList() }
                    val sorted = currentList
                        .sortedWith(
                            compareBy<StremioStream> { it.streamerNumber }
                                .thenByDescending { stream ->
                                    when {
                                        stream.cleanQuality.contains("4k", ignoreCase = true) || stream.cleanQuality.contains("2160p") -> 3
                                        stream.cleanQuality.contains("1080p") -> 2
                                        stream.cleanQuality.contains("720p") -> 1
                                        else -> 0
                                    }
                                }
                        )
                        .distinctBy { it.playableUrl }
                    emit(sorted)
                }
            }
            channel.close()
        }

        if (allAccumulated.isEmpty()) {
            emit(emptyList())
        }
    }

    fun getStreamsFlow(
        type: String,
        id: String,
        hdhubEnabled: Boolean = true,
        penguEnabled: Boolean = true
    ): kotlinx.coroutines.flow.Flow<List<StremioStream>> =
        getStreamsProgressive(type, id, hdhubEnabled, penguEnabled)

    suspend fun getStreams(
        type: String,
        id: String,
        hdhubEnabled: Boolean = true,
        penguEnabled: Boolean = true
    ): List<StremioStream> = coroutineScope {
        val hdhubDeferred = async(Dispatchers.IO) {
            if (hdhubEnabled) fetchHdhubStreams(type, id) else emptyList()
        }
        val penguDeferred = async(Dispatchers.IO) {
            if (penguEnabled) fetchPenguStreams(type, id) else emptyList()
        }

        val hdhubList = hdhubDeferred.await()
        val penguList = penguDeferred.await()

        (hdhubList + penguList)
            .filterNot { it.isBelow720p }
            .sortedWith(
                compareBy<StremioStream> { it.streamerNumber }
                    .thenByDescending { stream ->
                        when {
                            stream.cleanQuality.contains("4k", ignoreCase = true) || stream.cleanQuality.contains("2160p") -> 3
                            stream.cleanQuality.contains("1080p") -> 2
                            stream.cleanQuality.contains("720p") -> 1
                            else -> 0
                        }
                    }
            )
            .distinctBy { it.playableUrl }
    }

    private fun isDownloadOnlyOrTooLarge(stream: StremioStream, streamerNum: Int): Boolean {
        val url = stream.url.orEmpty().lowercase()
        // Discard explicit donation or discord links
        if (url.contains("donation.html") || url.contains("discord.gg") || url.contains("donate") || url.contains("paypal.me")) {
            return true
        }

        val name = stream.name.orEmpty().lowercase()
        if (name.contains("no streams found") || name.contains("donation needed") || name.contains("join the discord") || name.contains("support the project")) {
            return true
        }

        val fullText = "${stream.name.orEmpty()} ${stream.title.orEmpty()} ${stream.description.orEmpty()} $url".lowercase()

        // Remove download only options from ALL providers (HDHub4u & PenguPlay)
        if (fullText.contains("download only") ||
            fullText.contains("download-only") ||
            fullText.contains("dl only") ||
            fullText.contains("download link only") ||
            fullText.contains("not streamable") ||
            url.contains("/download/") ||
            url.contains("download_only")
        ) {
            return true
        }

        // Strictly only show less than 10 GB streamable provider links
        if (Regex("""\b\d+(?:\.\d+)?\s*(?:tb|tib)\b""").containsMatchIn(fullText)) {
            return true
        }
        val gbMatch = Regex("""\b(\d+(?:\.\d+)?)\s*(?:gb|gib)\b""").find(fullText)
        if (gbMatch != null) {
            val sizeGb = gbMatch.groupValues[1].toFloatOrNull() ?: 0f
            if (sizeGb >= 10.0f) {
                return true
            }
        }
        val mbMatch = Regex("""\b(\d+(?:\.\d+)?)\s*(?:mb|mib)\b""").find(fullText)
        if (mbMatch != null) {
            val sizeMb = mbMatch.groupValues[1].toFloatOrNull() ?: 0f
            if (sizeMb >= 10240.0f) {
                return true
            }
        }
        return false
    }

    private fun fetchHdhubStreams(type: String, id: String): List<StremioStream> {
        val list = mutableListOf<StremioStream>()
        runCatching {
            val url = "$HDHUB_STREAM_BASE/stream/$type/$id.json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val streamsArr = json.optJSONArray("streams")
                if (streamsArr != null) {
                    for (i in 0 until streamsArr.length()) {
                        val sObj = streamsArr.getJSONObject(i)
                        val stream = parseSingleStream(sObj, streamerNum = 1)
                        if (!stream.playableUrl.isNullOrBlank() && !stream.isBelow720p && !isDownloadOnlyOrTooLarge(stream, streamerNum = 1)) {
                            list.add(stream)
                        }
                    }
                }
            }
        }.onFailure {
            Log.d("StremioClient", "HDHub stream fetch note: ${it.message}")
        }
        return list
    }

    private fun fetchPenguStreams(type: String, id: String): List<StremioStream> {
        val list = mutableListOf<StremioStream>()
        runCatching {
            val endpoint = "$PENGU_STREAM_BASE/stream/$type/$id.json"
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Stremio/1.6.11")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val streamsArr = json.optJSONArray("streams")
                if (streamsArr != null) {
                    for (i in 0 until streamsArr.length()) {
                        val sObj = streamsArr.getJSONObject(i)
                        val stream = parseSingleStream(sObj, streamerNum = 2)
                        if (!stream.playableUrl.isNullOrBlank() && !stream.isBelow720p && !isDownloadOnlyOrTooLarge(stream, streamerNum = 2)) {
                            list.add(stream)
                        }
                    }
                }
            }
        }.onFailure {
            Log.d("StremioClient", "Pengu stream fetch: ${it.message}")
        }
        return list
    }

    private fun extractSubProvider(rawName: String, title: String, desc: String, defaultSource: String): String {
        val full = "$rawName $title $desc"
        val lower = full.lowercase()
        return when {
            lower.contains("2peckle") -> "2Peckle"
            lower.contains("moviebox") -> "MovieBox"
            lower.contains("moviesdrives") || lower.contains("movies drives") -> "MoviesDrives"
            lower.contains("vaplayer") -> "VAPlayer"
            lower.contains("4khdhub") -> "4KHDHub"
            lower.contains("vegamovies") || lower.contains("vega movies") -> "VegaMovies"
            lower.contains("miruro") -> "Miruro"
            lower.contains("vidlink") -> "VidLink"
            lower.contains("vidfast") -> "VidFast"
            lower.contains("cinefreak") -> "CineFreak"
            lower.contains("cinejoy") -> "Cinejoy"
            lower.contains("kisskh") -> "KissKH"
            lower.contains("anikoto") -> "Anikoto"
            lower.contains("vidy") -> "Vidy"
            lower.contains("arctic") -> "Arctic"
            lower.contains("toonworld4all") -> "ToonWorld4All"
            lower.contains("reanime") -> "Reanime"
            lower.contains("pixeldrain") -> "PixelDrain"
            lower.contains("hubcloud") -> "HubCloud"
            lower.contains("hdhub4u") || lower.contains("hdhub") -> "HDHub4u"
            lower.contains("fastdl") -> "FastDL"
            lower.contains("gofile") -> "GoFile"
            lower.contains("streamtape") -> "StreamTape"
            lower.contains("vidhide") -> "VidHide"
            lower.contains("filelions") -> "FileLions"
            lower.contains("doodstream") || lower.contains("dood") -> "DoodStream"
            lower.contains("v-cloud") || lower.contains("vcloud") -> "V-Cloud"
            lower.contains("torrentio") -> "Torrentio"
            lower.contains("galaxy") || lower.contains("tgx") -> "TorrentGalaxy"
            lower.contains("1337x") -> "1337x"
            lower.contains("yts") -> "YTS"
            defaultSource == "HDHub4u" -> "HDHub4u"
            defaultSource == "PenguPlay" -> "PenguPlay"
            else -> defaultSource
        }
    }

    private fun parseSingleStream(obj: JSONObject, streamerNum: Int): StremioStream {
        val rawName = obj.optString("name", "")
        val title = obj.optString("title", "")
        val desc = obj.optString("description", "")
        val url = obj.optString("url", "").takeIf { it.isNotBlank() }
        val infoHash = obj.optString("infoHash", "").takeIf { it.isNotBlank() }

        val addonSource = if (streamerNum == 1) "HDHub4u" else "PenguPlay"
        val subProvider = extractSubProvider(rawName, title, desc, addonSource)

        val displayName = if (subProvider.equals(addonSource, ignoreCase = true)) {
            addonSource
        } else {
            "$addonSource • $subProvider"
        }

        // Ensure description includes download/file details for HDHub streams
        val finalDescription = if (streamerNum == 1 && desc.isBlank() && !url.isNullOrBlank()) {
            "Direct Stream & Download Link • ${obj.optJSONObject("behaviorHints")?.optLong("videoSize")?.let { "${it / (1024 * 1024)} MB" } ?: "Fast Server"}"
        } else {
            desc.takeIf { it.isNotBlank() }
        }

        return StremioStream(
            name = displayName,
            title = if (title.isNotBlank()) title else "1080p FHD Stream",
            description = finalDescription,
            url = url,
            infoHash = infoHash,
            fileIdx = if (obj.has("fileIdx")) obj.optInt("fileIdx") else null,
            streamerNumber = streamerNum,
            addonSource = addonSource,
            subProvider = subProvider
        )
    }

    suspend fun getMovieDetails(type: String, id: String): Result<StremioCatalogMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$CINEMETA_BASE/meta/$type/$id.json"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Failed to fetch movie details: ${response.code}")
            }
            val obj = JSONObject(body).getJSONObject("meta")
            val trailers = mutableListOf<TrailerItem>()
            fun sanitizeYtSource(raw: String?): String? {
                if (raw.isNullOrBlank()) return null
                val trimmed = raw.trim()
                return when {
                    trimmed.contains("v=") -> trimmed.substringAfter("v=").substringBefore("&").substringBefore("?")
                    trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                    trimmed.contains("embed/") -> trimmed.substringAfter("embed/").substringBefore("?").substringBefore("&")
                    else -> trimmed
                }.takeIf { it.isNotBlank() }
            }

            val trailersArr = obj.optJSONArray("trailers")
            if (trailersArr != null) {
                for (j in 0 until trailersArr.length()) {
                    val tObj = trailersArr.optJSONObject(j)
                    if (tObj != null) {
                        val src = sanitizeYtSource(tObj.optString("source"))
                        if (src != null) {
                            trailers.add(TrailerItem(source = src, type = tObj.optString("type", "Trailer")))
                        }
                    }
                }
            }

            // Also check trailerStreams array often provided by Cinemeta
            val trailerStreamsArr = obj.optJSONArray("trailerStreams")
            if (trailerStreamsArr != null) {
                for (j in 0 until trailerStreamsArr.length()) {
                    val tsObj = trailerStreamsArr.optJSONObject(j)
                    if (tsObj != null) {
                        val ytId = sanitizeYtSource(tsObj.optString("ytId").ifEmpty { tsObj.optString("yt_id") })
                        if (ytId != null) {
                            trailers.add(TrailerItem(source = ytId, type = "Trailer"))
                        }
                    }
                }
            }

            // Also check single trailer string field
            val singleTrailer = sanitizeYtSource(
                obj.optString("trailer").ifEmpty {
                    obj.optString("youtubeTrailer").ifEmpty {
                        obj.optString("youtube_trailer")
                    }
                }
            )
            if (singleTrailer != null && trailers.none { it.source == singleTrailer }) {
                trailers.add(TrailerItem(source = singleTrailer, type = "Trailer"))
            }

            val movieTitle = obj.optString("name")
            val movieYear = obj.optString("year").takeIf { it.isNotBlank() }
            val imdbIdStr = obj.optString("imdb_id").ifEmpty { id }

            val resolutionInfo = resolveTrailerDetailed(
                imdbId = imdbIdStr,
                title = movieTitle,
                year = movieYear,
                type = type,
                metaTrailers = trailers
            )

            resolutionInfo.candidateIds.forEach { cand ->
                if (trailers.none { it.source == cand }) {
                    trailers.add(TrailerItem(source = cand, type = "Trailer"))
                }
            }

            val videos = mutableListOf<EpisodeItem>()
            val videosArr = obj.optJSONArray("videos")
            if (videosArr != null) {
                for (k in 0 until videosArr.length()) {
                    val vObj = videosArr.optJSONObject(k)
                    if (vObj != null) {
                        val seasonNum = vObj.optInt("season", 1)
                        val epNum = vObj.optInt("number", if (vObj.has("episode")) vObj.optInt("episode") else k + 1)
                        videos.add(
                            EpisodeItem(
                                id = vObj.optString("id", "${id}:$seasonNum:$epNum"),
                                name = vObj.optString("name", "Episode $epNum"),
                                season = seasonNum,
                                number = epNum,
                                overview = vObj.optString("overview").takeIf { it.isNotBlank() },
                                thumbnail = vObj.optString("thumbnail").takeIf { it.isNotBlank() },
                                rating = vObj.optString("rating").takeIf { it.isNotBlank() },
                                released = vObj.optString("released").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }

            val genres = mutableListOf<String>()
            val genreArr = obj.optJSONArray("genre")
            if (genreArr != null) {
                for (g in 0 until genreArr.length()) {
                    genres.add(genreArr.getString(g))
                }
            }

            val cast = mutableListOf<String>()
            val castArr = obj.optJSONArray("cast")
            if (castArr != null) {
                for (c in 0 until castArr.length()) {
                    cast.add(castArr.getString(c))
                }
            }

            val directors = mutableListOf<String>()
            val dirArr = obj.optJSONArray("director")
            if (dirArr != null) {
                for (d in 0 until dirArr.length()) {
                    directors.add(dirArr.getString(d))
                }
            }

            StremioCatalogMeta(
                id = obj.optString("id"),
                name = obj.optString("name"),
                type = obj.optString("type", type),
                poster = obj.optString("poster").takeIf { it.isNotBlank() },
                background = obj.optString("background").takeIf { it.isNotBlank() },
                logo = obj.optString("logo").takeIf { it.isNotBlank() },
                description = obj.optString("description").takeIf { it.isNotBlank() },
                year = obj.optString("year").takeIf { it.isNotBlank() },
                imdbRating = obj.optString("imdbRating").takeIf { it.isNotBlank() },
                genre = genres.ifEmpty { null },
                runtime = obj.optString("runtime").takeIf { it.isNotBlank() },
                director = directors.ifEmpty { null },
                cast = cast.ifEmpty { null },
                trailers = trailers.ifEmpty { null },
                videos = videos.ifEmpty { null },
                imdbId = obj.optString("imdb_id").takeIf { it.isNotBlank() },
                trailerResolution = resolutionInfo
            )
        }
    }

    suspend fun resolveTrailerDetailed(
        imdbId: String,
        title: String,
        year: String? = null,
        type: String = "movie",
        metaTrailers: List<TrailerItem> = emptyList()
    ): com.example.data.model.TrailerResolutionInfo = withContext(Dispatchers.IO) {
        val cacheKey = imdbId.ifBlank { title.trim().lowercase() }
        if (cacheKey.isNotBlank()) {
            resolutionInfoCache[cacheKey]?.let { return@withContext it }
        }

        // STEP 1 a) Check catalog/meta response first for trailers (source = YouTube ID) or trailerStreams
        val metaCandidates = metaTrailers.mapNotNull { it.source?.trim() }.filter { it.isNotBlank() }.distinct()
        if (metaCandidates.isNotEmpty()) {
            val bestMeta = metaCandidates.first()
            Log.d("TrailerPlayer", "[Meta] Found trailer in catalog meta: $bestMeta")
            val info = com.example.data.model.TrailerResolutionInfo(
                youtubeId = bestMeta,
                source = "meta",
                candidateIds = metaCandidates
            )
            if (cacheKey.isNotBlank()) {
                resolutionInfoCache[cacheKey] = info
                trailerCache[cacheKey] = bestMeta
            }
            return@withContext info
        }

        // STEP 1 b) If none, call TMDB
        var tmdbStatus = 0
        val cleanImdb = imdbId.trim()
        val isImdbValid = cleanImdb.startsWith("tt")

        val tmdbResult = runCatching {
            var tmdbId: Int? = null
            var isMovie = !type.equals("series", ignoreCase = true) && !type.equals("tv", ignoreCase = true)

            if (isImdbValid) {
                // With an IMDb id (tt...): GET /find/{imdb_id}?external_source=imdb_id&api_key=KEY
                val findUrl = "$TMDB_API_BASE/find/$cleanImdb?api_key=$TMDB_API_KEY&external_source=imdb_id"
                val findReq = Request.Builder()
                    .url(findUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()
                val findResp = client.newCall(findReq).execute()
                tmdbStatus = findResp.code
                val findBody = findResp.body?.string().orEmpty()
                Log.d("TrailerPlayer", "[TMDB] /find/$cleanImdb HTTP status: $tmdbStatus")

                if (findResp.isSuccessful && findBody.isNotBlank()) {
                    val findJson = JSONObject(findBody)
                    val movieResults = findJson.optJSONArray("movie_results")
                    val tvResults = findJson.optJSONArray("tv_results")

                    if (!isMovie) {
                        if (tvResults != null && tvResults.length() > 0) {
                            tmdbId = tvResults.getJSONObject(0).optInt("id")
                        } else if (movieResults != null && movieResults.length() > 0) {
                            tmdbId = movieResults.getJSONObject(0).optInt("id")
                            isMovie = true
                        }
                    } else {
                        if (movieResults != null && movieResults.length() > 0) {
                            tmdbId = movieResults.getJSONObject(0).optInt("id")
                        } else if (tvResults != null && tvResults.length() > 0) {
                            tmdbId = tvResults.getJSONObject(0).optInt("id")
                            isMovie = false
                        }
                    }
                }
            }

            // With no IMDb id: use /search/movie or /search/tv with title + year
            if (tmdbId == null && title.isNotBlank()) {
                val cleanTitle = java.net.URLEncoder.encode(title.trim(), "UTF-8")
                val searchEndpoint = if (isMovie) "movie" else "tv"
                val yearParam = if (!year.isNullOrBlank()) "&year=${year.trim()}" else ""
                val searchUrl = "$TMDB_API_BASE/search/$searchEndpoint?api_key=$TMDB_API_KEY&query=$cleanTitle$yearParam"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()
                val searchResp = client.newCall(searchReq).execute()
                tmdbStatus = searchResp.code
                val searchBody = searchResp.body?.string().orEmpty()
                Log.d("TrailerPlayer", "[TMDB] /search/$searchEndpoint for '$title' HTTP status: $tmdbStatus")

                if (searchResp.isSuccessful && searchBody.isNotBlank()) {
                    val searchJson = JSONObject(searchBody)
                    val results = searchJson.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        tmdbId = results.getJSONObject(0).optInt("id")
                    }
                }
            }

            if (tmdbId == null || tmdbId == 0) {
                Log.d("TrailerPlayer", "[TMDB] No matching TMDB entry found for $title ($imdbId)")
                return@runCatching null
            }

            // GET /movie/{id}/videos or /tv/{id}/videos?api_key=KEY&include_video_language=en,null
            val endpoint = if (isMovie) "movie" else "tv"
            val videosUrl = "$TMDB_API_BASE/$endpoint/$tmdbId/videos?api_key=$TMDB_API_KEY&include_video_language=en,null"
            val vReq = Request.Builder()
                .url(videosUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()
            val vResp = client.newCall(vReq).execute()
            tmdbStatus = vResp.code
            val vBody = vResp.body?.string().orEmpty()
            Log.d("TrailerPlayer", "[TMDB] /$endpoint/$tmdbId/videos HTTP status: $tmdbStatus")

            if (!vResp.isSuccessful || vBody.isBlank()) return@runCatching null

            val vJson = JSONObject(vBody)
            val resultsArr = vJson.optJSONArray("results") ?: return@runCatching null

            data class VideoCandidate(val key: String, val type: String, val official: Boolean)
            val allCandidates = mutableListOf<VideoCandidate>()

            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.getJSONObject(i)
                val site = item.optString("site")
                val key = item.optString("key").trim()
                val itemType = item.optString("type")
                val official = item.optBoolean("official", false)

                if (site.equals("YouTube", ignoreCase = true) && key.isNotBlank()) {
                    allCandidates.add(VideoCandidate(key, itemType, official))
                }
            }

            // STEP 1 c) Pick, in order: official YouTube "Trailer", any YouTube "Trailer", "Teaser", "Clip"
            val officialTrailer = allCandidates.firstOrNull { it.type.equals("Trailer", ignoreCase = true) && it.official }
            val anyTrailer = allCandidates.firstOrNull { it.type.equals("Trailer", ignoreCase = true) }
            val teaser = allCandidates.firstOrNull { it.type.equals("Teaser", ignoreCase = true) }
            val clip = allCandidates.firstOrNull { it.type.equals("Clip", ignoreCase = true) }

            val chosen = officialTrailer ?: anyTrailer ?: teaser ?: clip ?: allCandidates.firstOrNull()
            Log.d("TrailerPlayer", "[TMDB] Selected YouTube key: ${chosen?.key} (type: ${chosen?.type}, official: ${chosen?.official})")

            val candidateKeys = allCandidates.map { it.key }.distinct()
            chosen?.key?.let { key ->
                com.example.data.model.TrailerResolutionInfo(
                    youtubeId = key,
                    source = "TMDB",
                    tmdbHttpStatus = tmdbStatus,
                    candidateIds = candidateKeys
                )
            }
        }.getOrNull()

        if (tmdbResult != null) {
            if (cacheKey.isNotBlank()) {
                resolutionInfoCache[cacheKey] = tmdbResult
                trailerCache[cacheKey] = tmdbResult.youtubeId ?: ""
            }
            return@withContext tmdbResult
        }

        // Fallback: search YouTube directly if TMDB returned nothing
        if (title.isNotBlank()) {
            val searched = searchYoutubeTrailer(title, year)
            if (!searched.isNullOrBlank()) {
                Log.d("TrailerPlayer", "[Fallback] Web search found YouTube trailer: $searched")
                val searchInfo = com.example.data.model.TrailerResolutionInfo(
                    youtubeId = searched,
                    source = "search",
                    tmdbHttpStatus = if (tmdbStatus > 0) tmdbStatus else null,
                    candidateIds = listOf(searched)
                )
                if (cacheKey.isNotBlank()) {
                    resolutionInfoCache[cacheKey] = searchInfo
                    trailerCache[cacheKey] = searched
                }
                return@withContext searchInfo
            }
        }

        // STEP 1 d) Cache per title. If nothing is found, hide the trailer area cleanly with no error UI
        Log.d("TrailerPlayer", "[Resolution] No trailer found for $title ($imdbId)")
        val emptyInfo = com.example.data.model.TrailerResolutionInfo(
            youtubeId = null,
            source = "none",
            tmdbHttpStatus = if (tmdbStatus > 0) tmdbStatus else null,
            candidateIds = emptyList()
        )
        if (cacheKey.isNotBlank()) {
            resolutionInfoCache[cacheKey] = emptyInfo
            trailerCache[cacheKey] = ""
        }
        emptyInfo
    }

    suspend fun fetchTrailerFromTmdb(imdbId: String, type: String = "movie"): String? {
        val info = resolveTrailerDetailed(imdbId = imdbId, title = "", type = type)
        return info.youtubeId
    }

    /**
     * STEP 2 B) Direct stream fallback:
     * Piped API /streams/{id} across configurable instance list (in config),
     * race them with a 4s timeout, pick a progressive mp4 with audio (about 720p).
     */
    suspend fun racePipedDirectStream(youtubeId: String): String? = withContext(Dispatchers.IO) {
        if (youtubeId.isBlank()) return@withContext null
        Log.d("TrailerPlayer", "[Piped] Starting 4s race for direct stream of YouTube ID: $youtubeId")

        withTimeoutOrNull(4000) {
            val resultChannel = Channel<String?>(Channel.UNLIMITED)
            coroutineScope {
                PIPED_INSTANCES.forEach { instance ->
                    launch {
                        try {
                            val url = "$instance/streams/$youtubeId"
                            val req = Request.Builder()
                                .url(url)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .header("Accept", "application/json")
                                .build()
                            val resp = client.newCall(req).execute()
                            val body = resp.body?.string().orEmpty()
                            if (resp.isSuccessful && body.isNotBlank()) {
                                val json = JSONObject(body)
                                val vStreams = json.optJSONArray("videoStreams")
                                if (vStreams != null) {
                                    data class StreamOption(val url: String, val quality: String, val score: Int)
                                    val options = mutableListOf<StreamOption>()

                                    for (i in 0 until vStreams.length()) {
                                        val stream = vStreams.getJSONObject(i)
                                        val isVideoOnly = stream.optBoolean("videoOnly", false)
                                        val format = stream.optString("format", "")
                                        val mime = stream.optString("mimeType", "")
                                        val streamUrl = stream.optString("url", "")
                                        val quality = stream.optString("quality", "")

                                        // Progressive MP4 with audio
                                        val isMp4 = format.equals("MPEG_4", ignoreCase = true) || mime.contains("mp4")
                                        if (!isVideoOnly && isMp4 && streamUrl.isNotBlank()) {
                                            val score = when {
                                                quality.contains("720") -> 100
                                                quality.contains("1080") -> 90
                                                quality.contains("480") -> 80
                                                quality.contains("360") -> 70
                                                else -> 50
                                            }
                                            options.add(StreamOption(streamUrl, quality, score))
                                        }
                                    }

                                    val best = options.maxByOrNull { it.score }
                                    if (best != null) {
                                        Log.d("TrailerPlayer", "[Piped] Direct stream resolved from $instance (${best.quality}): ${best.url}")
                                        resultChannel.trySend(best.url)
                                        return@launch
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("TrailerPlayer", "[Piped] Instance $instance failed: ${e.message}")
                        }
                    }
                }
            }
            // If none succeeded before completion
            resultChannel.tryReceive().getOrNull()
        }
    }

    fun getCachedTrailerId(key: String): String? = trailerCache[key]?.takeIf { it.isNotBlank() }

    fun setCachedTrailerId(key: String, trailerId: String) {
        trailerCache[key] = trailerId
    }

    suspend fun searchYoutubeTrailer(title: String, year: String? = null): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("""[^\w\s]"""), " ").trim()
        val queryStr = if (!year.isNullOrBlank()) "$cleanTitle $year official trailer" else "$cleanTitle official trailer"
        val encodedQuery = java.net.URLEncoder.encode(queryStr, "UTF-8")

        // 1. YouTube with consent bypass cookie
        val ytResult = runCatching {
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+478; SOCS=CAESEwgDEgk2MTc0NTA0OTIaAmVuIAEaBgiA_LyaBg")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            Regex(""""videoId":"([a-zA-Z0-9_-]{11})"""").find(body)?.groupValues?.get(1)
                ?: Regex("""/watch\?v=([a-zA-Z0-9_-]{11})""").find(body)?.groupValues?.get(1)
                ?: Regex(""""id":"([a-zA-Z0-9_-]{11})"""").find(body)?.groupValues?.get(1)
        }.getOrNull()

        if (!ytResult.isNullOrBlank()) return@withContext ytResult

        // 2. DuckDuckGo Video Search Fallback
        val ddgResult = runCatching {
            val ddgUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery+youtube"
            val request = Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            val match = Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})""").find(body)
                ?: Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""").find(body)
            match?.groupValues?.get(1)
        }.getOrNull()

        ddgResult
    }
}

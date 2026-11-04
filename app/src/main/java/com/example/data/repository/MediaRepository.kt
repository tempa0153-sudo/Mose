package com.example.data.repository

import com.example.data.local.SavedMediaDao
import com.example.data.local.SavedMediaEntity
import com.example.data.model.StremioCatalogMeta
import com.example.data.model.StremioStream
import com.example.data.pref.UserPreferences
import com.example.data.remote.StremioClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow

data class CategorySection(
    val id: String,
    val title: String,
    val platformName: String,
    val type: String,
    val items: List<StremioCatalogMeta>
)

class MediaRepository(
    private val client: StremioClient,
    private val savedDao: SavedMediaDao,
    private val userPreferences: UserPreferences
) {
    @Volatile
    private var cachedCategories: List<CategorySection>? = null

    private val movieDetailsCache = java.util.concurrent.ConcurrentHashMap<String, StremioCatalogMeta>()
    private val customStreamsMap = java.util.concurrent.ConcurrentHashMap<String, List<StremioStream>>()
    private val customMetaMap = java.util.concurrent.ConcurrentHashMap<String, StremioCatalogMeta>()

    fun saveCustomStreamEdits(id: String, streams: List<StremioStream>) {
        customStreamsMap[id] = streams
    }

    fun saveCustomMetaEdits(meta: StremioCatalogMeta) {
        customMetaMap[meta.id] = meta
        customMetaMap[meta.displayImdbId] = meta
        movieDetailsCache[meta.id] = meta
        movieDetailsCache[meta.displayImdbId] = meta
    }

    fun getPermanentTop8HeroList(): List<StremioCatalogMeta> {
        return listOf(
            StremioCatalogMeta(
                id = "tt10430968",
                name = "Jujutsu Kaisen",
                type = "series",
                poster = "poster_jujutsu_kaisen",
                background = "poster_jujutsu_kaisen",
                year = "2020",
                imdbRating = "8.6",
                genre = listOf("Anime", "Action", "Supernatural"),
                description = "Yuji Itadori, a kind-hearted teenager, joins his school's Occult Club for fun, but discovers that its members are actual sorcerers who can manipulate energy between beings for their own use. He swallows a cursed finger to protect his friends and becomes possessed by Ryomen Sukuna, the King of Curses."
            ),
            StremioCatalogMeta(
                id = "tt0998631",
                name = "2012",
                type = "movie",
                poster = "poster_2012",
                background = "poster_2012",
                year = "2009",
                imdbRating = "5.8",
                genre = listOf("Action", "Adventure", "Sci-Fi"),
                description = "Dr. Adrian Helmsley, part of a worldwide geophysical team investigating the effect on Earth of radiation from unprecedented solar storms, learns that the Earth's core is heating up. He warns U.S. President Thomas Wilson that the crust of the planet is becoming unstable, leading to a catastrophic global apocalypse."
            ),
            StremioCatalogMeta(
                id = "tt21209876",
                name = "Solo Leveling",
                type = "series",
                poster = "poster_solo_leveling",
                background = "poster_solo_leveling",
                year = "2024",
                imdbRating = "8.4",
                genre = listOf("Anime", "Action", "Fantasy"),
                description = "In a world where hunters, humans who possess magical powers, must battle deadly monsters to protect mankind from annihilation, a notoriously weak hunter named Sung Jinwoo finds himself in a seamless struggle for survival inside a mysterious double dungeon."
            ),
            StremioCatalogMeta(
                id = "tt0409591",
                name = "Naruto",
                type = "series",
                poster = "poster_naruto",
                background = "poster_naruto",
                year = "2002",
                imdbRating = "8.4",
                genre = listOf("Anime", "Action", "Adventure"),
                description = "Naruto Uzumaki, a hyperactive and knuckle-headed ninja, lives in Konohagakure, the Hidden Leaf Village. Moments before his birth, a huge demon known as the Nine-Tailed Fox attacked the village and caused havoc, forcing the leader to seal the spirit inside newborn Naruto."
            ),
            StremioCatalogMeta(
                id = "tt1663662",
                name = "Pacific Rim",
                type = "movie",
                poster = "poster_pacific_rim",
                background = "poster_pacific_rim",
                year = "2013",
                imdbRating = "6.9",
                genre = listOf("Action", "Adventure", "Sci-Fi"),
                description = "When legions of monstrous creatures, known as Kaiju, started rising from the sea, a war began that would take millions of lives and consume humanity's resources. To combat the giant Kaiju, human scientists designed colossal robots called Jaegers."
            ),
            StremioCatalogMeta(
                id = "tt2632424",
                name = "The Originals",
                type = "series",
                poster = "poster_the_originals",
                background = "poster_the_originals",
                year = "2013",
                imdbRating = "8.3",
                genre = listOf("Drama", "Fantasy", "Horror"),
                description = "A family of power-hungry thousand-year-old vampires look to take back the city that they built and dominate all those who have done them wrong in the supernatural cauldron of New Orleans."
            ),
            StremioCatalogMeta(
                id = "tt3909224",
                name = "The Seven Deadly Sins",
                type = "series",
                poster = "poster_seven_deadly_sins",
                background = "poster_seven_deadly_sins",
                year = "2014",
                imdbRating = "7.8",
                genre = listOf("Anime", "Action", "Adventure"),
                description = "The Seven Deadly Sins are a band of knights in the land of Britannia who disbanded ten years earlier after being framed for plotting a coup against the Liones Kingdom. Princess Elizabeth embarks on a journey to find the Sins to help reclaim her kingdom."
            ),
            StremioCatalogMeta(
                id = "tt0460228",
                name = "A Haunting",
                type = "series",
                poster = "poster_a_haunting",
                background = "poster_a_haunting",
                year = "2005",
                imdbRating = "7.9",
                genre = listOf("Documentary", "Drama", "Horror"),
                description = "An American paranormal anthology television series depicting true accounts of terrifying paranormal activity, demonic possessions, haunted locations, exorcisms, and dark supernatural encounters across North America as broadcast on Discovery Channel."
            )
        )
    }

    fun build20HeroList(categories: List<CategorySection>): List<StremioCatalogMeta> {
        val permanentTop8 = getPermanentTop8HeroList()
        val permanentIds = permanentTop8.map { it.id }.toSet()

        val dynamicCandidates = mutableListOf<StremioCatalogMeta>()
        val itemsPerCategory = categories.map { cat ->
            cat.items
                .filter { it.id !in permanentIds }
                .sortedByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
        }

        var idx = 0
        while (dynamicCandidates.size < 12) {
            var addedAny = false
            for (catItems in itemsPerCategory) {
                if (idx < catItems.size) {
                    val candidate = catItems[idx]
                    if (dynamicCandidates.none { it.id == candidate.id }) {
                        dynamicCandidates.add(candidate)
                        addedAny = true
                        if (dynamicCandidates.size >= 12) break
                    }
                }
            }
            if (!addedAny) break
            idx++
        }

        val slots = arrayOfNulls<StremioCatalogMeta>(20)
        val jumbledPermIndices = listOf(1, 4, 7, 9, 12, 14, 17, 19)

        permanentTop8.forEachIndexed { permIdx, meta ->
            if (permIdx < jumbledPermIndices.size) {
                val slotIndex = jumbledPermIndices[permIdx]
                slots[slotIndex] = meta
            }
        }

        var candidateIdx = 0
        for (i in 0 until 20) {
            if (slots[i] == null && candidateIdx < dynamicCandidates.size) {
                slots[i] = dynamicCandidates[candidateIdx]
                candidateIdx++
            }
        }

        return slots.filterNotNull()
    }

    fun getCategoriesFlow(forceRefresh: Boolean = false): Flow<List<CategorySection>> = flow {
        if (!forceRefresh && !cachedCategories.isNullOrEmpty()) {
            emit(cachedCategories!!)
            return@flow
        }

        val accumulated = mutableListOf<CategorySection>()

        val visibleBatch = coroutineScope {
            val nfxMoviesDeferred = async { client.getCatalogItems("movie", "nfx").getOrDefault(emptyList()) }
            val nfxSeriesDeferred = async { client.getCatalogItems("series", "nfx").getOrDefault(emptyList()) }
            val nfxMovies = nfxMoviesDeferred.await()
            val nfxSeries = nfxSeriesDeferred.await()

            val list = mutableListOf<CategorySection>()
            if (nfxMovies.isNotEmpty()) {
                list.add(CategorySection("nfx_movie", "Netflix — Popular Movies & Hits", "Netflix", "movie", nfxMovies))
            }
            if (nfxSeries.isNotEmpty()) {
                list.add(CategorySection("nfx_series", "Netflix — Trending Series & Binge", "Netflix", "series", nfxSeries))
            }
            list
        }

        if (visibleBatch.isNotEmpty()) {
            accumulated.addAll(visibleBatch)
            cachedCategories = accumulated.toList()
            emit(accumulated.toList())
        }

        val batchA = coroutineScope {
            val atpMoviesDeferred = async { client.getCatalogItems("movie", "atp").getOrDefault(emptyList()) }
            val atpSeriesDeferred = async { client.getCatalogItems("series", "atp").getOrDefault(emptyList()) }
            val dnpMoviesDeferred = async { client.getCatalogItems("movie", "dnp").getOrDefault(emptyList()) }
            val dnpSeriesDeferred = async { client.getCatalogItems("series", "dnp").getOrDefault(emptyList()) }

            val atpMovies = atpMoviesDeferred.await()
            val atpSeries = atpSeriesDeferred.await()
            val dnpMovies = dnpMoviesDeferred.await()
            val dnpSeries = dnpSeriesDeferred.await()

            val list = mutableListOf<CategorySection>()
            if (atpMovies.isNotEmpty()) {
                list.add(CategorySection("atp_movie", "Apple TV+ — Award-Winning Cinema", "Apple TV+", "movie", atpMovies))
            }
            if (atpSeries.isNotEmpty()) {
                list.add(CategorySection("atp_series", "Apple TV+ — Acclaimed Original Series", "Apple TV+", "series", atpSeries))
            }
            if (dnpMovies.isNotEmpty()) {
                list.add(CategorySection("dnp_movie", "Disney+ — Premier Blockbusters & Animation", "Disney+", "movie", dnpMovies))
            }
            if (dnpSeries.isNotEmpty()) {
                list.add(CategorySection("dnp_series", "Disney+ — Hit Franchises & Series", "Disney+", "series", dnpSeries))
            }
            list
        }

        if (batchA.isNotEmpty()) {
            accumulated.addAll(batchA)
            cachedCategories = accumulated.toList()
            emit(accumulated.toList())
        }

        val batchB = coroutineScope {
            val ampMoviesDeferred = async { client.getCatalogItems("movie", "amp").getOrDefault(emptyList()) }
            val ampSeriesDeferred = async { client.getCatalogItems("series", "amp").getOrDefault(emptyList()) }
            val hbmMoviesDeferred = async { client.getCatalogItems("movie", "hbm").getOrDefault(emptyList()) }
            val hbmSeriesDeferred = async { client.getCatalogItems("series", "hbm").getOrDefault(emptyList()) }

            val ampMovies = ampMoviesDeferred.await()
            val ampSeries = ampSeriesDeferred.await()
            val hbmMovies = hbmMoviesDeferred.await()
            val hbmSeries = hbmSeriesDeferred.await()

            val list = mutableListOf<CategorySection>()
            if (ampMovies.isNotEmpty()) {
                list.add(CategorySection("amp_movie", "Prime Video — Top Movies & Blockbusters", "Prime Video", "movie", ampMovies))
            }
            if (ampSeries.isNotEmpty()) {
                list.add(CategorySection("amp_series", "Prime Video — Exclusive Prime Series", "Prime Video", "series", ampSeries))
            }
            if (hbmMovies.isNotEmpty()) {
                list.add(CategorySection("hbm_movie", "HBO Max — Box Office Hits & Cinema", "HBO Max", "movie", hbmMovies))
            }
            if (hbmSeries.isNotEmpty()) {
                list.add(CategorySection("hbm_series", "HBO Max — Acclaimed Prestige Drama", "HBO Max", "series", hbmSeries))
            }
            list
        }

        if (batchB.isNotEmpty()) {
            accumulated.addAll(batchB)
            cachedCategories = accumulated.toList()
            emit(accumulated.toList())
        }

        val batchC = coroutineScope {
            val cineMoviesDeferred = async { client.getCinemetaCatalog("movie", "top").getOrDefault(emptyList()) }
            val cineSeriesDeferred = async { client.getCinemetaCatalog("series", "top").getOrDefault(emptyList()) }

            val cineMovies = cineMoviesDeferred.await()
            val cineSeries = cineSeriesDeferred.await()

            val list = mutableListOf<CategorySection>()
            if (cineMovies.isNotEmpty()) {
                list.add(CategorySection("cinemeta_movie", "Cinemeta — All-Time Greatest Top Movies", "Cinemeta", "movie", cineMovies))
            }
            if (cineSeries.isNotEmpty()) {
                list.add(CategorySection("cinemeta_series", "Cinemeta — Worldwide Popular Series", "Cinemeta", "series", cineSeries))
            }
            list
        }

        if (batchC.isNotEmpty()) {
            accumulated.addAll(batchC)
            cachedCategories = accumulated.toList()
            emit(accumulated.toList())
        }
    }

    private val permanentHeroMap: Map<String, StremioCatalogMeta> by lazy {
        getPermanentTop8HeroList().associateBy { it.id }
    }

    suspend fun prefetchMovieDetails(items: List<StremioCatalogMeta>) = withContext(Dispatchers.IO) {
        items.take(8).forEach { item ->
            if (!movieDetailsCache.containsKey(item.id)) {
                val res = client.getMovieDetails(item.type, item.displayImdbId)
                res.getOrNull()?.let { movieDetailsCache[item.id] = it }
            }
        }
    }

    fun getCachedMovieDetails(id: String): StremioCatalogMeta? {
        customMetaMap[id]?.let { return it }
        val perm = permanentHeroMap[id]
        val cached = movieDetailsCache[id]
        if (perm != null) {
            return (cached ?: perm).copy(poster = perm.poster, background = perm.background)
        }
        return cached
    }

    suspend fun getMovieDetails(type: String, id: String): Result<StremioCatalogMeta> {
        customMetaMap[id]?.let { return Result.success(it) }

        val permItem = permanentHeroMap[id]
        if (movieDetailsCache.containsKey(id)) {
            val cached = movieDetailsCache[id]!!
            if (permItem != null) {
                val locked = cached.copy(poster = permItem.poster, background = permItem.background)
                return Result.success(locked)
            }
            return Result.success(cached)
        }

        val result = client.getMovieDetails(type, id)
        val remote = result.getOrNull()
        if (permItem != null) {
            val combined = (remote ?: permItem).copy(
                id = permItem.id,
                name = permItem.name,
                type = permItem.type,
                poster = permItem.poster,
                background = permItem.background,
                description = remote?.description?.takeIf { it.isNotBlank() } ?: permItem.description,
                year = remote?.year ?: permItem.year,
                imdbRating = remote?.imdbRating ?: permItem.imdbRating,
                genre = remote?.genre ?: permItem.genre
            )
            movieDetailsCache[id] = combined
            return Result.success(combined)
        }

        remote?.let { movieDetailsCache[id] = it }
        return result
    }

    suspend fun resolveTrailerInfo(meta: StremioCatalogMeta): com.example.data.model.TrailerResolutionInfo = withContext(Dispatchers.IO) {
        client.resolveTrailerDetailed(
            imdbId = meta.displayImdbId,
            title = meta.name,
            year = meta.year,
            type = meta.type,
            metaTrailers = meta.trailers ?: emptyList()
        )
    }

    suspend fun resolveTrailerId(meta: StremioCatalogMeta): String? = withContext(Dispatchers.IO) {
        val info = resolveTrailerInfo(meta)
        info.youtubeId
    }

    fun getStreamsFlow(type: String, id: String): Flow<List<StremioStream>> = flow {
        if (customStreamsMap.containsKey(id)) {
            emit(customStreamsMap[id]!!)
            return@flow
        }
        val profile = userPreferences.userProfile.value
        client.getStreamsProgressive(
            type = type,
            id = id,
            hdhubEnabled = profile.hdhubEnabled,
            penguEnabled = profile.penguEnabled
        ).collect { streams ->
            if (customStreamsMap.containsKey(id)) {
                emit(customStreamsMap[id]!!)
            } else {
                emit(streams)
            }
        }
    }

    fun getStreamsProgressive(type: String, id: String): Flow<List<StremioStream>> = getStreamsFlow(type, id)

    suspend fun search(query: String, type: String = "movie"): Result<List<StremioCatalogMeta>> = withContext(Dispatchers.IO) {
        if (type.equals("all", ignoreCase = true)) {
            coroutineScope {
                val moviesDeferred = async { client.searchCatalog(query, "movie").getOrDefault(emptyList()) }
                val seriesDeferred = async { client.searchCatalog(query, "series").getOrDefault(emptyList()) }
                val movies = moviesDeferred.await()
                val series = seriesDeferred.await()
                val merged = (movies + series).distinctBy { it.displayImdbId }
                Result.success(merged)
            }
        } else {
            client.searchCatalog(query, type)
        }
    }

    fun observeSavedMedia(sortOrder: String = "custom"): Flow<List<SavedMediaEntity>> {
        return when (sortOrder) {
            "date_desc" -> savedDao.getAllByDateDesc()
            "date_asc" -> savedDao.getAllByDateAsc()
            "title_asc" -> savedDao.getAllByTitleAsc()
            "rating_desc" -> savedDao.getAllByRatingDesc()
            "rating_asc" -> savedDao.getAllByRatingAsc()
            else -> savedDao.getAllByCustomOrder()
        }
    }

    fun isSaved(id: String): Flow<Boolean> = savedDao.isSavedFlow(id)

    suspend fun toggleSave(meta: StremioCatalogMeta): Boolean {
        val exists = savedDao.exists(meta.id)
        if (exists) {
            savedDao.deleteById(meta.id)
            return false
        } else {
            val count = savedDao.getCount()
            saveMedia(meta, count)
            return true
        }
    }

    suspend fun saveMedia(meta: StremioCatalogMeta, customOrder: Int = 0) {
        movieDetailsCache[meta.id] = meta
        movieDetailsCache[meta.displayImdbId] = meta
        val entity = SavedMediaEntity(
            id = meta.id,
            title = meta.name,
            type = meta.type,
            poster = meta.poster,
            backdrop = meta.background,
            year = meta.year,
            imdbRating = meta.imdbRating,
            description = meta.description,
            genres = meta.genre?.joinToString(", "),
            customOrderIndex = customOrder
        )
        savedDao.insert(entity)
    }

    suspend fun deleteSaved(id: String) {
        savedDao.deleteById(id)
    }

    suspend fun updateCustomOrder(items: List<SavedMediaEntity>) {
        val updated = items.mapIndexed { index, entity ->
            entity.copy(customOrderIndex = index)
        }
        savedDao.updateAll(updated)
    }
}

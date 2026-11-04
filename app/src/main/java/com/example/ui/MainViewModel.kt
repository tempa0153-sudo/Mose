package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SavedMediaEntity
import com.example.data.model.EpisodeItem
import com.example.data.model.StremioCatalogMeta
import com.example.data.model.StremioStream
import com.example.data.pref.UserPreferences
import com.example.data.pref.UserProfile
import com.example.data.remote.StremioClient
import com.example.data.repository.CategorySection
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object Search : Screen()
    object Library : Screen()
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val heroMovies: List<StremioCatalogMeta> = emptyList(),
    val currentHeroIndex: Int = 0,
    val categories: List<CategorySection> = emptyList(),
    val selectedPlatform: String = "All"
)

data class DetailUiState(
    val meta: StremioCatalogMeta? = null,
    val isLoadingDetails: Boolean = false,
    val streams: List<StremioStream> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val isSaved: Boolean = false,
    val isTrailerMuted: Boolean = false,
    val selectedQualityFilter: String = "All",
    val selectedProviderFilter: String = "All",
    val selectedSeason: Int = 1,
    val selectedEpisode: EpisodeItem? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = StremioClient()
    private val db = AppDatabase.getInstance(application)
    val preferences = UserPreferences(application)
    val repository = MediaRepository(client, db.savedMediaDao(), preferences)

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StremioCatalogMeta>>(emptyList())
    val searchResults: StateFlow<List<StremioCatalogMeta>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchType = MutableStateFlow("movie")
    val searchType: StateFlow<String> = _searchType.asStateFlow()

    private val _savedMedia = MutableStateFlow<List<SavedMediaEntity>>(emptyList())
    val savedMedia: StateFlow<List<SavedMediaEntity>> = _savedMedia.asStateFlow()

    private val _detailState = MutableStateFlow(DetailUiState())
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _isProfileOpen = MutableStateFlow(false)
    val isProfileOpen: StateFlow<Boolean> = _isProfileOpen.asStateFlow()

    val userProfile: StateFlow<UserProfile> = preferences.userProfile

    private var heroShuffleJob: Job? = null
    private var searchJob: Job? = null
    private var savedObserverJob: Job? = null
    private var streamJob: Job? = null

    init {
        loadCatalogData()
        observeSavedMedia()
        startHeroAutoShuffle()
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun openProfile() {
        _isProfileOpen.value = true
    }

    fun closeProfile() {
        _isProfileOpen.value = false
    }

    fun loadCatalogData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_homeState.value.categories.isEmpty() || forceRefresh) {
                _homeState.value = _homeState.value.copy(
                    isLoading = _homeState.value.categories.isEmpty(),
                    error = null
                )
            }
            try {
                repository.getCategoriesFlow(forceRefresh = forceRefresh).collect { categories ->
                    val heroCandidates = repository.build20HeroList(categories)

                    _homeState.value = _homeState.value.copy(
                        isLoading = false,
                        heroMovies = heroCandidates,
                        categories = categories
                    )
                    viewModelScope.launch {
                        repository.prefetchMovieDetails(heroCandidates)
                    }
                }

            } catch (e: Exception) {
                if (_homeState.value.categories.isEmpty()) {
                    _homeState.value = _homeState.value.copy(
                        isLoading = false,
                        error = "Failed to connect to catalog. Please retry."
                    )
                }
            }
        }
    }

    private fun startHeroAutoShuffle() {
        heroShuffleJob?.cancel()
        heroShuffleJob = viewModelScope.launch {
            while (true) {
                delay(6000)
                val movies = _homeState.value.heroMovies
                if (movies.isNotEmpty()) {
                    val nextIdx = (_homeState.value.currentHeroIndex + 1) % movies.size
                    _homeState.value = _homeState.value.copy(currentHeroIndex = nextIdx)
                }
            }
        }
    }

    fun setHeroIndex(index: Int) {
        val movies = _homeState.value.heroMovies
        if (index in movies.indices) {
            _homeState.value = _homeState.value.copy(currentHeroIndex = index)
        }
    }

    fun filterPlatform(platform: String) {
        _homeState.value = _homeState.value.copy(selectedPlatform = platform)
    }

    fun setSearchType(type: String) {
        _searchType.value = type
        if (_searchQuery.value.isNotBlank()) {
            executeSearch(_searchQuery.value, type)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        executeSearch(query, _searchType.value)
    }

    private fun executeSearch(query: String, type: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            val results = repository.search(query, type)
            _searchResults.value = results.getOrDefault(emptyList())
            _isSearching.value = false
        }
    }

    private fun observeSavedMedia() {
        savedObserverJob?.cancel()
        savedObserverJob = viewModelScope.launch {
            userProfile.collectLatest { profile ->
                repository.observeSavedMedia(profile.librarySortOrder).collectLatest { list ->
                    _savedMedia.value = list
                }
            }
        }
    }

    fun setLibrarySortOrder(sortOrder: String) {
        preferences.updateLibrarySortOrder(sortOrder)
    }

    fun setLibraryTileSize(size: String) {
        preferences.updateLibraryTileSize(size)
    }

    fun reorderSavedItems(newList: List<SavedMediaEntity>) {
        _savedMedia.value = newList
        viewModelScope.launch {
            repository.updateCustomOrder(newList)
            if (userProfile.value.librarySortOrder != "custom") {
                preferences.updateLibrarySortOrder("custom")
            }
        }
    }

    fun saveLibraryLayoutAndOrder() {
        val current = _savedMedia.value
        viewModelScope.launch {
            preferences.updateLibrarySortOrder("custom")
            repository.updateCustomOrder(current)
        }
    }

    fun moveSavedItem(fromIndex: Int, toIndex: Int) {
        val current = _savedMedia.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _savedMedia.value = current
            viewModelScope.launch {
                repository.updateCustomOrder(current)
            }
        }
    }

    fun deleteSavedItem(id: String) {
        viewModelScope.launch {
            repository.deleteSaved(id)
        }
    }

    fun openMovieDetail(meta: StremioCatalogMeta) {
        val cached = repository.getCachedMovieDetails(meta.displayImdbId)
        val initialMeta = cached ?: meta
        val isSeries = initialMeta.type.equals("series", ignoreCase = true)
        val initialSeason = if (isSeries) initialMeta.seasons.firstOrNull() ?: 1 else 1
        val initialEpisode = if (isSeries) initialMeta.getEpisodesForSeason(initialSeason).firstOrNull() else null

        _detailState.value = DetailUiState(
            meta = initialMeta,
            isLoadingDetails = cached == null,
            isLoadingStreams = !isSeries || initialEpisode != null,
            isTrailerMuted = userProfile.value.trailerMuted,
            selectedSeason = initialSeason,
            selectedEpisode = initialEpisode
        )

        viewModelScope.launch {
            repository.isSaved(meta.displayImdbId).collectLatest { saved ->
                _detailState.value = _detailState.value.copy(isSaved = saved)
            }
        }

        if (initialMeta.allTrailerIds.isEmpty()) {
            viewModelScope.launch {
                val resolvedId = repository.resolveTrailerId(initialMeta)
                if (!resolvedId.isNullOrBlank() && _detailState.value.meta?.id == initialMeta.id) {
                    val current = _detailState.value.meta
                    if (current != null && current.allTrailerIds.isEmpty()) {
                        val withTrailer = current.copy(
                            trailers = listOf(com.example.data.model.TrailerItem(source = resolvedId, type = "Trailer"))
                        )
                        _detailState.value = _detailState.value.copy(meta = withTrailer)
                    }
                }
            }
        }

        viewModelScope.launch {
            val enriched = repository.getMovieDetails(meta.type, meta.displayImdbId)
            var updatedMeta = enriched.getOrNull() ?: meta
            if (updatedMeta.allTrailerIds.isEmpty()) {
                val resolvedId = repository.resolveTrailerId(updatedMeta)
                if (!resolvedId.isNullOrBlank()) {
                    updatedMeta = updatedMeta.copy(
                        trailers = listOf(com.example.data.model.TrailerItem(source = resolvedId, type = "Trailer"))
                    )
                }
            }

            if (isSeries) {
                val availableSeasons = updatedMeta.seasons
                val initialSeason = availableSeasons.firstOrNull() ?: 1
                val episodes = updatedMeta.getEpisodesForSeason(initialSeason)
                val initialEpisode = episodes.firstOrNull()

                _detailState.value = _detailState.value.copy(
                    meta = updatedMeta,
                    isLoadingDetails = false,
                    selectedSeason = initialSeason,
                    selectedEpisode = initialEpisode
                )

                if (initialEpisode != null) {
                    fetchStreamsForEpisode(initialEpisode)
                } else {
                    _detailState.value = _detailState.value.copy(isLoadingStreams = false)
                }
            } else {
                _detailState.value = _detailState.value.copy(
                    meta = updatedMeta,
                    isLoadingDetails = false
                )
                fetchStreamsForMovie(updatedMeta.displayImdbId)
            }
        }
    }

    private fun fetchStreamsForMovie(id: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _detailState.value = _detailState.value.copy(
                isLoadingStreams = true,
                streams = emptyList()
            )
            repository.getStreamsProgressive("movie", id).collect { progressiveStreams ->
                _detailState.value = _detailState.value.copy(
                    streams = progressiveStreams,
                    isLoadingStreams = false
                )
            }
        }
    }

    private fun fetchStreamsForEpisode(episode: EpisodeItem) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _detailState.value = _detailState.value.copy(
                isLoadingStreams = true,
                streams = emptyList()
            )
            repository.getStreamsProgressive("series", episode.id).collect { progressiveStreams ->
                _detailState.value = _detailState.value.copy(
                    streams = progressiveStreams,
                    isLoadingStreams = false
                )
            }
        }
    }

    fun setProviderFilter(provider: String) {
        _detailState.value = _detailState.value.copy(selectedProviderFilter = provider)
    }

    fun selectSeason(season: Int) {
        val meta = _detailState.value.meta ?: return
        val episodes = meta.getEpisodesForSeason(season)
        val firstEp = episodes.firstOrNull()

        _detailState.value = _detailState.value.copy(
            selectedSeason = season,
            selectedEpisode = firstEp
        )

        if (firstEp != null) {
            fetchStreamsForEpisode(firstEp)
        }
    }

    fun selectEpisode(episode: EpisodeItem) {
        _detailState.value = _detailState.value.copy(
            selectedEpisode = episode
        )
        fetchStreamsForEpisode(episode)
    }

    fun closeMovieDetail() {
        streamJob?.cancel()
        _detailState.value = DetailUiState(meta = null)
    }

    fun toggleSaveMovie(meta: StremioCatalogMeta) {
        viewModelScope.launch {
            val isNowSaved = repository.toggleSave(meta)
            _detailState.value = _detailState.value.copy(isSaved = isNowSaved)
        }
    }

    fun toggleTrailerMute() {
        val newMute = !_detailState.value.isTrailerMuted
        _detailState.value = _detailState.value.copy(isTrailerMuted = newMute)
        preferences.updateTrailerMuted(newMute)
    }

    fun addCustomStream(stream: StremioStream) {
        val current = _detailState.value.streams.toMutableList()
        current.add(0, stream)
        _detailState.value = _detailState.value.copy(streams = current)
    }

    fun removeStream(stream: StremioStream) {
        val current = _detailState.value.streams.toMutableList()
        current.remove(stream)
        _detailState.value = _detailState.value.copy(streams = current)
    }

    fun saveCustomMedia(
        title: String,
        type: String,
        poster: String?,
        backdrop: String?,
        year: String?,
        rating: String?,
        description: String?,
        genres: String?,
        seasonsCount: Int = 1,
        perSeasonMap: Map<Int, Int> = emptyMap()
    ) {
        viewModelScope.launch {
            val customId = "custom_${System.currentTimeMillis()}"
            val effectivePoster = poster?.ifBlank { backdrop?.ifBlank { null } }
                ?: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1200"
            val effectiveBackdrop = backdrop?.ifBlank { effectivePoster } ?: effectivePoster

            val generatedVideos = if (type.equals("series", ignoreCase = true)) {
                val epList = mutableListOf<EpisodeItem>()
                val actualSeasons = seasonsCount.coerceIn(1, 100)
                for (s in 1..actualSeasons) {
                    val epsInSeason = perSeasonMap[s] ?: 12
                    val actualEps = epsInSeason.coerceIn(1, 500)
                    for (ep in 1..actualEps) {
                        epList.add(
                            EpisodeItem(
                                id = "${customId}:$s:$ep",
                                name = "Episode $ep: Chapter $ep",
                                season = s,
                                number = ep,
                                overview = "An exciting episode of $title in Season $s featuring intense plot developments.",
                                thumbnail = effectivePoster,
                                released = year?.ifBlank { "2024" }
                            )
                        )
                    }
                }
                epList
            } else null

            val meta = StremioCatalogMeta(
                id = customId,
                name = title,
                type = type,
                poster = effectivePoster,
                background = effectiveBackdrop,
                year = year?.ifBlank { "2024" },
                imdbRating = rating?.ifBlank { "8.0" },
                description = description?.ifBlank { "A custom added $type with rich storyline and exclusive episodes." },
                genre = genres?.split(",")?.map { it.trim() },
                videos = generatedVideos
            )
            repository.saveMedia(meta, 0)
        }
    }

    fun setQualityFilter(quality: String) {
        _detailState.value = _detailState.value.copy(selectedQualityFilter = quality)
    }
}

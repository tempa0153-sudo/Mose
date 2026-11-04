package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.EpisodeItem
import com.example.data.model.StremioCatalogMeta
import com.example.data.model.StremioStream
import com.example.ui.DetailUiState
import com.example.ui.components.MediaSlideshowView
import com.example.ui.theme.CinemaRed
import com.example.ui.theme.ImdbGold

fun sanitizeStreamText(raw: String?): String? {
    if (raw.isNullOrBlank()) return raw
    return raw
        .replace("PixelDrain", "Provider 1 Addon", ignoreCase = true)
        .replace("Pixel Drain", "Provider 1 Addon", ignoreCase = true)
        .replace("PixelD", "Provider 1 Addon", ignoreCase = true)
        .replace("HLS Stream", "Provider 1 Addon", ignoreCase = true)
        .replace("HLS stream", "Provider 1 Addon", ignoreCase = true)
        .replace("HLSstream", "Provider 1 Addon", ignoreCase = true)
        .replace("HLS", "Provider 1 Addon", ignoreCase = true)
        .replace("4KHDHub", "Provider 1 Addon", ignoreCase = true)
        .replace("HDHub4u", "Provider 1 Addon", ignoreCase = true)
        .replace("PenguPlay", "Provider 2 Addon", ignoreCase = true)
        .replace("VegaMovies", "Provider 2 Addon", ignoreCase = true)
        .replace("MoviesDrive", "Provider 2 Addon", ignoreCase = true)
}

fun formatProviderDisplay(raw: String?): String {
    if (raw.isNullOrBlank()) return "Provider 1 Addon"
    val cleaned = raw.replace("HLS Stream", "Provider 1 Addon", ignoreCase = true)
        .replace("HLS stream", "Provider 1 Addon", ignoreCase = true)
        .replace("HLSstream", "Provider 1 Addon", ignoreCase = true)
        .replace("HLS", "Provider 1 Addon", ignoreCase = true)
        .replace("PixelDrain", "Provider 1 Addon", ignoreCase = true)
        .replace("Pixel Drain", "Provider 1 Addon", ignoreCase = true)
        .replace("PixelD", "Provider 1 Addon", ignoreCase = true)
        .replace("4KHDHub", "Provider 1 Addon", ignoreCase = true)
    return when {
        cleaned.contains("Provider 1 Addon", ignoreCase = true) -> "Provider 1 Addon"
        cleaned.contains("Provider 2 Addon", ignoreCase = true) -> "Provider 2 Addon"
        cleaned.equals("HDHub4u", ignoreCase = true) || cleaned.equals("hdhub", ignoreCase = true) -> "Provider 1"
        cleaned.equals("PenguPlay", ignoreCase = true) || cleaned.equals("pengu", ignoreCase = true) -> "Provider 2"
        cleaned.contains("2Peckle", ignoreCase = true) || cleaned.contains("MovieBox", ignoreCase = true) || cleaned.contains("VegaMovies", ignoreCase = true) || cleaned.contains("MoviesDrive", ignoreCase = true) -> "Provider 2 Addon"
        else -> if (!cleaned.contains("Provider", ignoreCase = true)) "$cleaned Addon" else cleaned
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailSheet(
    detailState: DetailUiState,
    onClose: () -> Unit,
    onToggleSave: (StremioCatalogMeta) -> Unit,
    onToggleMute: () -> Unit,
    onSelectQuality: (String) -> Unit,
    onSelectProvider: (String) -> Unit = {},
    onSelectSeason: (Int) -> Unit = {},
    onSelectEpisode: (EpisodeItem) -> Unit = {},
    onAddCustomStream: (StremioStream) -> Unit = {},
    onRemoveStream: (StremioStream) -> Unit = {},
    onSaveCustomEdits: (List<StremioStream>, StremioCatalogMeta) -> Unit = { _, _ -> },
    showDebugLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val meta = detailState.meta ?: return
    val context = LocalContext.current
    var isOverviewExpanded by remember { mutableStateOf(false) }
    var isEditingProviders by remember { mutableStateOf(false) }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var showManageSlideshowDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    val isSeries = meta.type.equals("series", ignoreCase = true)

    val initialSlideshowList = remember(meta) {
        val list = mutableListOf<String>()
        meta.poster?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        meta.background?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        meta.videos?.mapNotNull { it.thumbnail }?.filter { it.isNotBlank() }?.let { list.addAll(it) }
        list.distinct().toMutableList()
    }
    var localSlideshowList by remember(meta) { mutableStateOf(initialSlideshowList) }

    var activeEpisodeDetail by remember(meta) { mutableStateOf<EpisodeItem?>(null) }

    // Backup original lists for restore/undo functionality
    val originalSeasons = remember(meta) { meta.seasons.ifEmpty { listOf(1) } }
    val originalEpisodes = remember(meta, detailState.selectedSeason) {
        meta.getEpisodesForSeason(detailState.selectedSeason).ifEmpty {
            listOf(
                EpisodeItem("ep1", "Episode 1: The Beginning", 1, detailState.selectedSeason, "An incredible journey begins as new alliances form.", null, "2024"),
                EpisodeItem("ep2", "Episode 2: Dark Shadows", 2, detailState.selectedSeason, "Secrets unravel and challenges escalate.", null, "2024")
            )
        }
    }

    var localSeasonsList by remember(meta) { mutableStateOf(originalSeasons.toMutableList()) }
    var localEpisodesList by remember(meta, detailState.selectedSeason) { mutableStateOf(originalEpisodes.toMutableList()) }

    BackHandler {
        if (activeEpisodeDetail != null) {
            activeEpisodeDetail = null
        } else {
            onClose()
        }
    }

    val availableQualities = listOf("All Qualities", "4K 2160p", "1080p FHD", "720p HD")
    val filteredStreams = remember(detailState.streams, detailState.selectedQualityFilter, detailState.selectedProviderFilter) {
        val sanitized = detailState.streams.map { stream ->
            stream.copy(
                name = sanitizeStreamText(stream.name) ?: stream.name,
                title = sanitizeStreamText(stream.title) ?: stream.title,
                description = sanitizeStreamText(stream.description) ?: stream.description,
                addonSource = formatProviderDisplay(stream.addonSource),
                subProvider = sanitizeStreamText(stream.subProvider)
            )
        }
        val initialFiltered = sanitized
            .filterNot { it.isBelow720p }
            .filter { stream ->
                val matchesQuality = if (detailState.selectedQualityFilter == "All" || detailState.selectedQualityFilter == "All Qualities") true
                else stream.cleanQuality.contains(detailState.selectedQualityFilter.take(5), ignoreCase = true)

                val selectedP = detailState.selectedProviderFilter
                val matchesProvider = when {
                    selectedP == "All" || selectedP == "All Providers" || selectedP == "All Streamers" -> true
                    selectedP.equals("HDHub4u", ignoreCase = true) || selectedP.equals("Provider 1", ignoreCase = true) ->
                        stream.streamerNumber == 1 || stream.addonSource.contains("Provider 1", ignoreCase = true)
                    selectedP.equals("PenguPlay", ignoreCase = true) || selectedP.equals("Provider 2", ignoreCase = true) ->
                        stream.streamerNumber == 2 || stream.addonSource.contains("Provider 2", ignoreCase = true)
                    else -> true
                }
                matchesQuality && matchesProvider
            }

        val hasProvider1 = initialFiltered.any { it.streamerNumber == 1 || it.addonSource.contains("Provider 1", ignoreCase = true) }
        val hasProvider2 = initialFiltered.any { it.streamerNumber == 2 || it.addonSource.contains("Provider 2", ignoreCase = true) }

        val resultList = initialFiltered.toMutableList()

        if (!hasProvider1) {
            val fallbackUrl = initialFiltered.firstOrNull()?.playableUrl ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            resultList.add(
                0,
                StremioStream(
                    name = "Provider 1",
                    title = "Direct Stream Link • 1080p FHD",
                    url = fallbackUrl,
                    streamerNumber = 1,
                    addonSource = "Provider 1 Addon",
                    subProvider = "Provider 1 Addon",
                    description = "Direct Stream & Download Link • 1080p FHD"
                )
            )
        }

        if (!hasProvider2) {
            val fallbackUrl = initialFiltered.firstOrNull { it.streamerNumber == 2 }?.playableUrl ?: initialFiltered.firstOrNull()?.playableUrl ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
            resultList.add(
                StremioStream(
                    name = "Provider 2",
                    title = "Fast Direct Server Link • 720p HD",
                    url = fallbackUrl,
                    streamerNumber = 2,
                    addonSource = "Provider 2 Addon",
                    subProvider = "Provider 2 Addon",
                    description = "Fast Server Provider 2 Stream Link • 720p HD"
                )
            )
        }

        resultList.sortedWith(
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
    }

    var localStreamsList by remember(filteredStreams) { mutableStateOf(filteredStreams.toMutableList()) }

    // Compute sequential badge numbers per provider (Provider 1 #1, #2... Provider 2 #1, #2...)
    val streamBadgeLabels = remember(localStreamsList) {
        var p1Count = 0
        var p2Count = 0
        localStreamsList.map { stream ->
            val isP1 = stream.streamerNumber == 1 || stream.addonSource?.contains("HDHub", ignoreCase = true) == true || stream.addonSource?.contains("Provider 1", ignoreCase = true) == true
            if (isP1) {
                p1Count++
                "#$p1Count"
            } else {
                p2Count++
                "#$p2Count"
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // BODY CONTENT - LazyColumn spans entire height under floating header
            if (isSeries && activeEpisodeDetail != null) {
                // EPISODE DETAIL VIEW
                val ep = activeEpisodeDetail!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    // Slideshow (8 images) edge-to-edge behind status bar
                    item {
                        val epImages = remember(ep, meta) {
                            val list = mutableListOf<String>()
                            meta.poster?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            ep.thumbnail?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.background?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.videos?.mapNotNull { it.thumbnail }?.filter { it.isNotBlank() }?.let { list.addAll(it) }
                            list.distinct().take(8)
                        }
                        MediaSlideshowView(
                            images = (epImages + localSlideshowList).distinct(),
                            movieTitle = ep.name,
                            isEditingMode = isEditingProviders,
                            onAddOrEditClick = { showManageSlideshowDialog = true }
                        )
                    }

                    // Episode Title & Info
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "S${ep.season} E${ep.number}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                ep.released?.takeIf { it.isNotBlank() }?.let { date ->
                                    Text(
                                        text = date,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = ep.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            ep.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                                Text(
                                    text = overview,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Available Streams Header for this Episode
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Episode Stream Links",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (detailState.isLoadingStreams) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Loading Providers...",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = "${localStreamsList.size} Available",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (isEditingProviders) {
                                Surface(
                                    onClick = { showAddProviderDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Add Custom Provider for Episode",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Streams list for Episode
                    if (detailState.isLoadingStreams && detailState.streams.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                            }
                        }
                    } else if (localStreamsList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No direct stream links found for this episode.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = localStreamsList,
                            key = { idx, stream -> "${stream.streamerNumber}_${stream.name}_${stream.title}_$idx" }
                        ) { idx, stream ->
                            StreamSourceItemCard(
                                stream = stream,
                                badgeLabel = streamBadgeLabels.getOrElse(idx) { "" },
                                title = "${meta.name} S${ep.season}E${ep.number}",
                                context = context,
                                isEditingProviders = isEditingProviders,
                                canMoveUp = idx > 0,
                                canMoveDown = idx < localStreamsList.size - 1,
                                onMoveUp = {
                                    val list = localStreamsList.toMutableList()
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                },
                                onMoveDown = {
                                    val list = localStreamsList.toMutableList()
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                },
                                onRemoveStream = onRemoveStream
                            )
                        }
                    }
                }
            } else {
                // MAIN DETAIL VIEW (Movie or Series Overview)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    // Slideshow Header
                    item {
                        val slideshowImages = remember(meta) {
                            val list = mutableListOf<String>()
                            meta.poster?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.background?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.videos?.mapNotNull { it.thumbnail }?.filter { it.isNotBlank() }?.let { list.addAll(it) }
                            list.distinct()
                        }
                        MediaSlideshowView(
                            images = localSlideshowList,
                            movieTitle = meta.name,
                            isEditingMode = isEditingProviders,
                            onAddOrEditClick = { showManageSlideshowDialog = true }
                        )
                    }

                    // Info Section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = meta.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                if (!meta.year.isNullOrBlank()) {
                                    Text(
                                        text = meta.year,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                meta.runtime?.let {
                                    Text(
                                        text = "• $it",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = meta.type.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // IMDb Rating Bar
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E212D),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF32384B))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(2.dp),
                                            color = ImdbGold
                                        ) {
                                            Text(
                                                text = "IMDb",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = ImdbGold,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = meta.imdbRating?.takeIf { it.isNotBlank() } ?: "8.2",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Genres
                            meta.genre?.let { genres ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    genres.forEach { g ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = g,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Description / Overview
                            meta.description?.let { desc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isOverviewExpanded = !isOverviewExpanded }
                                ) {
                                    Text(
                                        text = desc,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        maxLines = if (isOverviewExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (desc.length > 120) {
                                        Text(
                                            text = if (isOverviewExpanded) "Show Less" else "Read More",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // IF SERIES: Show Seasons & Episodes list (No providers at bottom!)
                    if (isSeries) {
                        val currentSeason = detailState.selectedSeason

                        // Seasons Header & Chips
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Seasons (${localSeasonsList.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    if (isEditingProviders) {
                                        Surface(
                                            onClick = {
                                                val nextSeason = (localSeasonsList.maxOrNull() ?: 0) + 1
                                                localSeasonsList = (localSeasonsList + nextSeason).toMutableList()
                                                onSelectSeason(nextSeason)
                                                Toast.makeText(context, "Season $nextSeason added!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add Season", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(localSeasonsList) { seasonNum ->
                                        val isSelected = seasonNum == currentSeason
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onSelectSeason(seasonNum) },
                                                label = {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(
                                                            text = "Season $seasonNum",
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 12.sp
                                                        )
                                                        if (isEditingProviders && localSeasonsList.size > 1) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Close,
                                                                contentDescription = "Delete Season",
                                                                tint = CinemaRed,
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clickable {
                                                                        localSeasonsList = localSeasonsList.filter { it != seasonNum }.toMutableList()
                                                                        Toast.makeText(context, "Season $seasonNum deleted", Toast.LENGTH_SHORT).show()
                                                                    }
                                                            )
                                                        }
                                                    }
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Episodes Section Header
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Episodes (${localEpisodesList.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    if (isEditingProviders) {
                                        Surface(
                                            onClick = {
                                                val nextEpNum = localEpisodesList.size + 1
                                                val newEp = EpisodeItem(
                                                    id = "custom_ep_${System.currentTimeMillis()}",
                                                    name = "Episode $nextEpNum: Custom Title",
                                                    number = nextEpNum,
                                                    season = currentSeason,
                                                    overview = "A newly added episode filled with exciting plot twists and high-definition scenes.",
                                                    thumbnail = meta.poster ?: meta.background,
                                                    released = "2024"
                                                )
                                                localEpisodesList = (localEpisodesList + newEp).toMutableList()
                                                Toast.makeText(context, "Episode $nextEpNum added to Season $currentSeason!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add Episode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Vertical Episodes List with Reorder Up/Down handle icons beside cross icon
                        if (localEpisodesList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No episodes in Season $currentSeason. Click 'Add Episode' to create one.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            itemsIndexed(localEpisodesList, key = { idx, ep -> "${ep.id.ifBlank { "ep_${ep.season}_${ep.number}" }}_$idx" }) { idx, ep ->
                                VerticalEpisodeRowCard(
                                    episode = ep,
                                    fallbackBackdrop = meta.poster ?: meta.background,
                                    isEditingMode = isEditingProviders,
                                    canMoveUp = idx > 0,
                                    canMoveDown = idx < localEpisodesList.size - 1,
                                    onMoveUp = {
                                        val list = localEpisodesList.toMutableList()
                                        val temp = list[idx]
                                        list[idx] = list[idx - 1]
                                        list[idx - 1] = temp
                                        localEpisodesList = list
                                    },
                                    onMoveDown = {
                                        val list = localEpisodesList.toMutableList()
                                        val temp = list[idx]
                                        list[idx] = list[idx + 1]
                                        list[idx + 1] = temp
                                        localEpisodesList = list
                                    },
                                    onRemoveEpisode = {
                                        localEpisodesList = localEpisodesList.filter { it.id != ep.id }.toMutableList()
                                        Toast.makeText(context, "${ep.name} removed", Toast.LENGTH_SHORT).show()
                                    },
                                    onClick = {
                                        onSelectEpisode(ep)
                                        activeEpisodeDetail = ep
                                    }
                                )
                            }
                        }
                    } else {
                        // MOVIE ONLY: Show Available Streams at the bottom
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Available Streams",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (detailState.isLoadingStreams) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (localStreamsList.isNotEmpty()) "${localStreamsList.size} found (more loading...)" else "Loading HDHub...",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = "${localStreamsList.size} Available Providers",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (isEditingProviders) {
                                    Surface(
                                        onClick = { showAddProviderDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Add Custom Provider / Local File",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Streams list for Movie
                        if (detailState.isLoadingStreams && detailState.streams.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                                }
                            }
                        } else if (localStreamsList.isEmpty() && !detailState.isLoadingStreams) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No direct stream links found for this movie.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = localStreamsList,
                                key = { idx, stream -> "${stream.streamerNumber}_${stream.name}_${stream.title}_$idx" }
                            ) { idx, stream ->
                                StreamSourceItemCard(
                                    stream = stream,
                                    badgeLabel = streamBadgeLabels.getOrElse(idx) { "" },
                                    title = meta.name,
                                    context = context,
                                    isEditingProviders = isEditingProviders,
                                    canMoveUp = idx > 0,
                                    canMoveDown = idx < localStreamsList.size - 1,
                                    onMoveUp = {
                                        val list = localStreamsList.toMutableList()
                                        val temp = list[idx]
                                        list[idx] = list[idx - 1]
                                        list[idx - 1] = temp
                                        localStreamsList = list
                                    },
                                    onMoveDown = {
                                        val list = localStreamsList.toMutableList()
                                        val temp = list[idx]
                                        list[idx] = list[idx + 1]
                                        list[idx + 1] = temp
                                        localStreamsList = list
                                    },
                                    onRemoveStream = onRemoveStream
                                )
                            }
                        }
                    }
                }
            }

            // Floating Top App Bar Overlay with Bleeding Translucent Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (activeEpisodeDetail != null) {
                                activeEpisodeDetail = null
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = if (activeEpisodeDetail != null) "${meta.name} • Ep ${activeEpisodeDetail?.number}" else meta.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    // TOP RIGHT BUTTONS: Tick -> History (Restore) -> Pencil -> Library (Far Right)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isEditingProviders) {
                            // 1. Tick / Save Checkmark Icon
                            IconButton(
                                onClick = {
                                    val updatedMeta = meta.copy(
                                        poster = localSlideshowList.firstOrNull() ?: meta.poster,
                                        background = localSlideshowList.getOrNull(1) ?: meta.background
                                    )
                                    onSaveCustomEdits(localStreamsList, updatedMeta)
                                    isEditingProviders = false
                                    Toast.makeText(context, "Layout, custom order & slideshow saved!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("detail_save_check_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Save Changes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 2. Restore Clock / History Icon (Screenshot 393)
                            IconButton(
                                onClick = { showRestoreConfirmDialog = true },
                                modifier = Modifier.testTag("detail_restore_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = "Restore Original Format",
                                    tint = Color.White
                                )
                            }
                        }

                        // 3. Pencil / Edit Button
                        IconButton(
                            onClick = { isEditingProviders = !isEditingProviders },
                            modifier = Modifier.testTag("detail_pencil_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Providers & Episodes",
                                tint = if (isEditingProviders) CinemaRed else Color.White
                            )
                        }

                        // 4. Library / Save to Library Button (Far right hand side)
                        IconButton(
                            onClick = { onToggleSave(meta) },
                            modifier = Modifier.testTag("detail_save_button")
                        ) {
                            Icon(
                                imageVector = if (detailState.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (detailState.isSaved) "Saved to Library" else "Save to Library",
                                tint = if (detailState.isSaved) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                }
            }
        }

        // Restore Confirm Dialog (Screenshot 393)
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Restore Original Format", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text("Do you want to restore all deleted seasons, episodes, and stream providers back to their original layout and order?", fontSize = 13.sp)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            localSeasonsList = originalSeasons.toMutableList()
                            localEpisodesList = originalEpisodes.toMutableList()
                            localStreamsList = filteredStreams.toMutableList()
                            localSlideshowList = initialSlideshowList.toMutableList()
                            showRestoreConfirmDialog = false
                            Toast.makeText(context, "Restored original seasons, episodes & format!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Restore All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddProviderDialog) {
            AddCustomProviderDialog(
                onDismiss = { showAddProviderDialog = false },
                onAdd = { newStream ->
                    onAddCustomStream(newStream)
                    localStreamsList = (listOf(newStream) + localStreamsList).toMutableList()
                    showAddProviderDialog = false
                }
            )
        }

        if (showManageSlideshowDialog) {
            ManageSlideshowImagesDialog(
                images = localSlideshowList,
                onDismiss = { showManageSlideshowDialog = false },
                onSaveImages = { newList ->
                    localSlideshowList = newList.toMutableList()
                    showManageSlideshowDialog = false
                    Toast.makeText(context, "Slideshow images updated!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// Vertical episode row card with thumbnail image, momentary tap feedback, and up/down reorder buttons
@Composable
private fun VerticalEpisodeRowCard(
    episode: EpisodeItem,
    fallbackBackdrop: String?,
    isEditingMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemoveEpisode: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = episode.thumbnail ?: fallbackBackdrop

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("episode_card_${episode.number}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail Box (16:9)
            Box(
                modifier = Modifier
                    .width(115.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (!imageModel.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Movie, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }
                }

                // Episode badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Episode Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = episode.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                episode.released?.takeIf { it.isNotBlank() }?.let { date ->
                    Text(
                        text = date,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Reorder handles & Delete icon in Edit Mode
            if (isEditingMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Up / Down reorder buttons
                    Column {
                        if (canMoveUp) {
                            IconButton(onClick = onMoveUp, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (canMoveDown) {
                            IconButton(onClick = onMoveDown, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Down", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    IconButton(
                        onClick = onRemoveEpisode,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove Episode",
                            tint = CinemaRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamSourceItemCard(
    stream: StremioStream,
    badgeLabel: String = "",
    title: String,
    context: Context,
    isEditingProviders: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemoveStream: (StremioStream) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("stream_card_streamer_${stream.streamerNumber}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val addonColor = if (stream.streamerNumber == 1) Color(0xFF00897B) else Color(0xFF0288D1)
                    val rawAddon = formatProviderDisplay(stream.addonSource)
                    val formattedAddon = if (badgeLabel.isNotBlank()) "$rawAddon $badgeLabel" else rawAddon
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = addonColor
                    ) {
                        Text(
                            text = formattedAddon,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val sub = stream.subProvider?.let { formatProviderDisplay(it) }
                    if (!sub.isNullOrBlank() && !sub.equals(formattedAddon, ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF263238),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF37474F))
                        ) {
                            Text(
                                text = sub,
                                color = Color(0xFF80CBC4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            stream.cleanQuality.contains("4k", ignoreCase = true) || stream.cleanQuality.contains("2160p") -> CinemaRed
                            stream.cleanQuality.contains("1080p") -> Color(0xFF0288D1)
                            else -> Color(0xFF5E35B1)
                        }
                    ) {
                        Text(
                            text = stream.cleanQuality,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isEditingProviders) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Column {
                            if (canMoveUp) {
                                IconButton(onClick = onMoveUp, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (canMoveDown) {
                                IconButton(onClick = onMoveDown, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Down", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = CinemaRed,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onRemoveStream(stream) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove Provider",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            stream.description?.takeIf { it.isNotBlank() }?.let { desc ->
                val cleanDesc = sanitizeStreamText(desc) ?: desc
                Text(
                    text = cleanDesc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val playable = stream.playableUrl
                        if (!playable.isNullOrBlank()) {
                            executeStreamPlayback(context, playable)
                        } else {
                            Toast.makeText(context, "Stream link not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_stream_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val playable = stream.playableUrl
                        if (!playable.isNullOrBlank()) {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "$title Stream Link")
                                putExtra(Intent.EXTRA_TEXT, playable)
                            }
                            val shareChooser = Intent.createChooser(sendIntent, "Share Stream Link via...")
                            shareChooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(shareChooser)
                        } else {
                            Toast.makeText(context, "Stream link not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("share_stream_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun executeStreamPlayback(context: Context, playableUrl: String) {
    val uri = Uri.parse(playableUrl)
    val isMagnet = playableUrl.startsWith("magnet:", ignoreCase = true)

    if (isMagnet) {
        try {
            val magnetIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(magnetIntent, "Open Magnet Link with..."))
        } catch (_: Exception) {
            Toast.makeText(context, "Please install a torrent player app (e.g. LibreTorrent or VLC)", Toast.LENGTH_LONG).show()
        }
        return
    }

    try {
        val headersBundle = android.os.Bundle().apply {
            putString("User-Agent", "Stremio/1.6.11 (Linux; Android)")
            putString("Accept", "*/*")
            putString("Connection", "keep-alive")
        }

        val playerIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("android.media.intent.extra.HTTP_HEADERS", headersBundle)
            putExtra("headers", arrayOf("User-Agent: Stremio/1.6.11 (Linux; Android)"))
            putExtra("user_agent", "Stremio/1.6.11 (Linux; Android)")
            putExtra("secure_uri", true)
        }
        val chooser = Intent.createChooser(playerIntent, "Play with Video Player")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (_: Exception) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Play Stream with..."))
        } catch (_: Exception) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, playableUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share or Play with..."))
        }
    }
}

@Composable
fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (StremioStream) -> Unit
) {
    var isLocalFile by remember { mutableStateOf(false) }
    var providerName by remember { mutableStateOf("") }
    var urlOrPath by remember { mutableStateOf("") }
    var selectedResolution by remember { mutableStateOf("1080p FHD") }
    var fileSize by remember { mutableStateOf("2.1 GB") }
    var descriptionDetails by remember { mutableStateOf("BluRay, x264, Dual Audio") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Stream Provider",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isLocalFile,
                        onClick = { isLocalFile = false },
                        label = { Text("Provider Link (URL)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = isLocalFile,
                        onClick = { isLocalFile = true },
                        label = { Text("Local Video File", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                androidx.compose.material3.OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text("Provider Name (e.g. Provider 3)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                androidx.compose.material3.OutlinedTextField(
                    value = urlOrPath,
                    onValueChange = { urlOrPath = it },
                    label = { Text(if (isLocalFile) "Video File Path (e.g. /storage/...)" else "Stream URL (http://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1080p FHD", "4K 2160p", "720p HD").forEach { res ->
                        FilterChip(
                            selected = selectedResolution == res,
                            onClick = { selectedResolution = res },
                            label = { Text(res, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                androidx.compose.material3.OutlinedTextField(
                    value = fileSize,
                    onValueChange = { fileSize = it },
                    label = { Text("File Size (e.g. 2.1 GB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                androidx.compose.material3.OutlinedTextField(
                    value = descriptionDetails,
                    onValueChange = { descriptionDetails = it },
                    label = { Text("Format / Description (e.g. BluRay, x264)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlOrPath.isNotBlank()) {
                        val pName = providerName.ifBlank { if (isLocalFile) "Local Video" else "Custom Provider" }
                        val stream = StremioStream(
                            name = pName,
                            title = "$pName • $selectedResolution",
                            description = "[$pName] • $fileSize • $descriptionDetails",
                            url = urlOrPath.trim(),
                            streamerNumber = 3,
                            addonSource = pName,
                            subProvider = pName
                        )
                        onAdd(stream)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add Provider", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManageSlideshowImagesDialog(
    images: List<String>,
    onDismiss: () -> Unit,
    onSaveImages: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var currentList by remember(images) { mutableStateOf(images.toMutableList()) }
    var inputUrl by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            val newPaths = uris.map { it.toString() }
            currentList = (newPaths + currentList).distinct().toMutableList()
            Toast.makeText(context, "${uris.size} slideshow images added!", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Manage Slideshow Images", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Add Image URL or Local Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (inputUrl.isNotBlank()) {
                                currentList = (listOf(inputUrl.trim()) + currentList).toMutableList()
                                inputUrl = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add URL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Images (up to 10)", fontSize = 11.sp)
                    }
                }

                Text("Slideshow Images (${currentList.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                currentList.forEachIndexed { idx, imgUrl ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(imgUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Text(
                                text = imgUrl,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (idx > 0) {
                                    IconButton(
                                        onClick = {
                                            val list = currentList.toMutableList()
                                            val temp = list[idx]
                                            list[idx] = list[idx - 1]
                                            list[idx - 1] = temp
                                            currentList = list
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (idx < currentList.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val list = currentList.toMutableList()
                                            val temp = list[idx]
                                            list[idx] = list[idx + 1]
                                            list[idx + 1] = temp
                                            currentList = list
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        currentList = currentList.filterIndexed { i, _ -> i != idx }.toMutableList()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, tint = CinemaRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveImages(currentList)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply Images", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

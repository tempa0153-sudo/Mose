package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.StremioCatalogMeta
import com.example.data.pref.UserProfile
import com.example.data.repository.CategorySection
import com.example.ui.HomeUiState
import com.example.ui.components.HeroBanner
import com.example.ui.components.MovieCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeState: HomeUiState,
    userProfile: UserProfile,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onSelectHeroIndex: (Int) -> Unit,
    onSelectPlatformFilter: (String) -> Unit,
    onClickMovie: (StremioCatalogMeta) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = homeState.categories

    // Pre-calculate display categories to avoid redundant recompositions and stutter
    val displayCategories = remember(categories, homeState.selectedPlatform) {
        if (homeState.selectedPlatform == "All") {
            categories
        } else {
            categories.filter { it.platformName.equals(homeState.selectedPlatform, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Top Hero Banner with translucent poster extending behind user profile and name
            item(key = "hero_banner", contentType = "hero") {
                HeroBanner(
                    movies = homeState.heroMovies,
                    currentIndex = homeState.currentHeroIndex,
                    onSelectIndex = onSelectHeroIndex,
                    onClickMovie = onClickMovie,
                    topContent = {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xEE1E1E22),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF4A4D55)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(28.dp))
                                .clickable(onClick = onOpenProfile)
                                .testTag("home_profile_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF181F2C),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (!userProfile.customAvatarPath.isNullOrBlank()) {
                                            AsyncImage(
                                                model = userProfile.customAvatarPath,
                                                contentDescription = "Custom User Avatar",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = "User Profile",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        text = "Heyo,\n${userProfile.name}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // All Categories catalog tabs with brand colors
            item(key = "platform_chips", contentType = "platform_chips") {
                val platforms = listOf("All Catalogs", "Netflix", "Apple TV+", "Disney+", "Prime Video", "HBO Max")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(platforms) { p ->
                        val selected = (p == "All Catalogs" && homeState.selectedPlatform == "All") || p.equals(homeState.selectedPlatform, ignoreCase = true)
                        val brandColor = when (p) {
                            "Netflix" -> Color(0xFFE50914)
                            "Apple TV+" -> Color(0xFFE2E8F0)
                            "Disney+" -> Color(0xFF0063E5)
                            "Prime Video" -> Color(0xFF00A8E1)
                            "HBO Max" -> Color(0xFF8B5CF6)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val containerColor = if (selected) brandColor else brandColor.copy(alpha = 0.20f)
                        val textColor = when {
                            p == "Apple TV+" && selected -> Color.Black
                            selected -> Color.White
                            else -> brandColor
                        }

                        FilterChip(
                            selected = selected,
                            onClick = { onSelectPlatformFilter(if (p == "All Catalogs") "All" else p) },
                            label = { Text(p, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = brandColor,
                                selectedLabelColor = textColor,
                                containerColor = containerColor,
                                labelColor = textColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color.Transparent,
                                selectedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // Categories horizontal carousels with stable keys and fast recycling
            items(
                displayCategories,
                key = { it.id },
                contentType = { "category_section" }
            ) { category ->
                CategorySectionView(
                    category = category,
                    onClickMovie = onClickMovie
                )
            }
        }

        // Top Bleeding Gradient Overlay (strictly above profile pill border)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom Bleeding Transparent Gradient Overlay (behind pill navbar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.60f),
                            Color.Black.copy(alpha = 0.90f),
                            Color.Black
                        )
                    )
                )
        )

        // Initial Splash Loading Overlay with Red Circular App Icon (No outer ring, no text)
        AnimatedVisibility(
            visible = homeState.isLoading || displayCategories.isEmpty(),
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE50914), // Pure App Icon Red
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = "App Icon Splash",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySectionView(
    category: CategorySection,
    onClickMovie: (StremioCatalogMeta) -> Unit
) {
    val platformColor = when (category.platformName) {
        "Netflix" -> Color(0xFFE50914)
        "Apple TV+" -> Color(0xFFE2E8F0)
        "HBO Max" -> Color(0xFF8B5CF6)
        "Disney+" -> Color(0xFF0063E5)
        "Prime Video" -> Color(0xFF00A8E1)
        "Cinemeta" -> Color(0xFF6366F1)
        else -> MaterialTheme.colorScheme.primary
    }

    val platformTextColor = when (category.platformName) {
        "Apple TV+" -> Color(0xFF0F172A)
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Section Header: clean compact box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE222226),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383A40)),
                shadowElevation = 2.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = platformColor
                    ) {
                        Text(
                            text = category.platformName.uppercase(),
                            color = platformTextColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = category.title.substringAfter(" — ", category.title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontal items row optimized for zero-stutter 120fps scrolling
        val sanitizedItems = remember(category.items) {
            category.items.distinctBy { it.id }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                sanitizedItems,
                key = { movie -> "${category.id}_${movie.id}" },
                contentType = { "movie_card" }
            ) { movie ->
                MovieCard(
                    meta = movie,
                    onClick = { onClickMovie(movie) }
                )
            }
        }
    }
}

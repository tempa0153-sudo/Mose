package com.example.ui.components

import android.content.Context
import coil.request.ImageRequest

fun resolveCoilData(context: Context, model: Any?): Any? {
    if (model == null) return null
    if (model is Int) return model
    val str = model.toString().trim()
    if (str.isBlank()) return null

    val cleanName = when {
        str.startsWith("poster_") -> str.substringBefore(".")
        str.contains("poster_") -> str.substringAfterLast("/").substringBefore(".")
        else -> null
    }

    if (cleanName != null) {
        val resId = context.resources.getIdentifier(cleanName, "drawable", context.packageName)
        if (resId != 0) return resId
        val fallbackId = context.resources.getIdentifier(cleanName, "drawable", "com.example")
        if (fallbackId != 0) return fallbackId
    }
    return str
}

fun buildOptimizedImageRequest(context: Context, data: Any?, width: Int = 360, height: Int = 540): ImageRequest {
    val resolved = resolveCoilData(context, data)
    return ImageRequest.Builder(context)
        .data(resolved)
        .size(width, height)
        .precision(coil.size.Precision.INEXACT)
        .allowRgb565(true)
        .crossfade(false)
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .build()
}

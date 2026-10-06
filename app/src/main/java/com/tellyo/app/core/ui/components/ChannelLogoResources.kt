package com.tellyo.app.core.ui.components

import androidx.annotation.DrawableRes
import com.tellyo.app.R

/** Bundled broadcaster artwork, available without a network request or catalogue update. */
@DrawableRes
internal fun bundledChannelLogo(logoKey: String): Int? = when (logoKey) {
    "stvr" -> R.drawable.channel_logo_stvr_jednotka
    "dvojka" -> R.drawable.channel_logo_stvr_dvojka
    "24" -> R.drawable.channel_logo_stvr_24
    "sport" -> R.drawable.channel_logo_stvr_sport
    "joj" -> R.drawable.channel_logo_joj
    "joj-plus" -> R.drawable.channel_logo_joj_plus
    "joj-krimi" -> R.drawable.channel_logo_joj_krimi
    "joj-24" -> R.drawable.channel_logo_joj_24
    "markiza" -> R.drawable.channel_logo_markiza
    "markiza-doma" -> R.drawable.channel_logo_markiza_doma
    "markiza-dajto" -> R.drawable.channel_logo_markiza_dajto
    "markiza-krimi" -> R.drawable.channel_logo_markiza_krimi
    "markiza-klasik" -> R.drawable.channel_logo_markiza_klasik
    "ta3" -> R.drawable.channel_logo_ta3
    else -> null
}

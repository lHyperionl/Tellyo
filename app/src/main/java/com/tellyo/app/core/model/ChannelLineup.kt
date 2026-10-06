package com.tellyo.app.core.model

internal object ChannelLineup {
    const val CHILDREN_ANCHOR_ID = "iptv-Sport2.cz"
    val CHILDREN_IDS = listOf(
        "iptv-DisneyChannel.cz", "iptv-DisneyJunior.cz", "iptv-MinimaxCEE.cz",
        "iptv-NickJr.cz", "iptv-Nickelodeon.cz", "iptv-Nicktoons.cz",
    )
    val CHILDREN_NUMBERS = CHILDREN_IDS.mapIndexed { index, id -> id to 25 + index }.toMap()
}

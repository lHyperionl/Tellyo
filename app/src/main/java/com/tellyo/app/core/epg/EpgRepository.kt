package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Program
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

interface EpgProvider {
    val id: String

    /** Higher-priority providers replace overlapping channel data last. */
    val priority: Int
        get() = 0

    /** Prevents large guide sources being downloaded/applied every UI refresh tick. */
    val minimumRefreshInterval: java.time.Duration
        get() = java.time.Duration.ZERO

    /** Returns an XMLTV document from a legal, configured source. */
    suspend fun fetchXml(): String

    /** Null windows mean a complete channel snapshot; empty windows mean incremental metadata. */
    suspend fun fetch(): EpgFetchResult = EpgFetchResult(fetchXml())
}

data class EpgReplacementWindow(val channelId: String, val from: Instant, val to: Instant)

data class EpgFetchResult(
    val xml: String,
    val replacementWindows: List<EpgReplacementWindow>? = null,
)

interface EpgRepository {
    fun observePrograms(channelId: String, from: Instant, to: Instant): Flow<List<Program>>

    fun observePrograms(channelIds: List<String>, from: Instant, to: Instant): Flow<Map<String, List<Program>>> =
        if (channelIds.isEmpty()) flowOf(emptyMap()) else combine(
            channelIds.map { id -> observePrograms(id, from, to).map { id to it } },
        ) { it.toMap() }

    suspend fun refresh()
}

class XmlTvParser @javax.inject.Inject constructor() {
    fun parse(xml: String): List<Program> {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val document = factory.newDocumentBuilder().parse(
            org.xml.sax.InputSource(java.io.StringReader(xml)),
        )
        val programmeNodes = document.getElementsByTagName("programme")
        return buildList(programmeNodes.length) {
            for (index in 0 until programmeNodes.length) {
                val element = programmeNodes.item(index) as? org.w3c.dom.Element ?: continue
                val channelId = element.getAttribute("channel").trim()
                val title = element.childText("title") ?: continue
                val start = parseTimestamp(element.getAttribute("start")) ?: continue
                val end = parseTimestamp(element.getAttribute("stop")) ?: continue
                if (channelId.isBlank() || !end.isAfter(start)) continue
                val artworkUrl = element.getElementsByTagName("icon")
                    .item(0)
                    ?.attributes
                    ?.getNamedItem("src")
                    ?.nodeValue
                    ?.trim()
                    ?.takeIf { it.startsWith("https://") }

                add(
                    Program(
                        id = "${channelId}_${start.toEpochMilli()}",
                        channelId = channelId,
                        title = title,
                        description = element.childText("desc"),
                        start = start,
                        end = end,
                        category = element.childText("category"),
                        artworkUrl = artworkUrl,
                    ),
                )
            }
        }.distinctBy { it.id }
    }

    private fun org.w3c.dom.Element.childText(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseTimestamp(raw: String): Instant? {
        val value = raw.trim()
        if (value.isBlank()) return null
        return runCatching {
            java.time.OffsetDateTime.parse(
                value,
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss xx"),
            ).toInstant()
        }.getOrElse {
            runCatching {
                java.time.LocalDateTime.parse(
                    value,
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                ).toInstant(java.time.ZoneOffset.UTC)
            }.getOrNull()
        }
    }
}

@javax.inject.Singleton
class RoomEpgRepository @javax.inject.Inject constructor(
    private val programDao: com.tellyo.app.core.database.ProgramDao,
    private val providers: dagger.Lazy<Set<@JvmSuppressWildcards EpgProvider>>,
    private val parser: XmlTvParser,
) : EpgRepository {
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    private val lastSuccessfulRefresh = mutableMapOf<String, Instant>()
    private val replacementCoverage = mutableMapOf<String, Coverage>()

    private data class Coverage(val completeChannels: Set<String>, val windows: List<EpgReplacementWindow>)

    override fun observePrograms(channelId: String, from: Instant, to: Instant): Flow<List<Program>> =
        programDao.observeWindow(channelId, from.toEpochMilli(), to.toEpochMilli())
            .distinctUntilChanged()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun observePrograms(
        channelIds: List<String>,
        from: Instant,
        to: Instant,
    ): Flow<Map<String, List<Program>>> =
        programDao.observeChannelsWindow(channelIds, from.toEpochMilli(), to.toEpochMilli())
            .distinctUntilChanged()
            .map { entities -> entities.map { it.toDomain() }.groupBy(Program::channelId) }
            .flowOn(Dispatchers.Default)

    override suspend fun refresh(): Unit = withContext(Dispatchers.IO) {
        refreshMutex.withLock { refreshProviders() }
    }

    private suspend fun refreshProviders() {
        val now = Instant.now()
        var attemptedProviders = 0
        var updatedProviders = 0
        val failures = mutableListOf<Throwable>()
        // Resolve higher-priority coverage first, including coverage retained while
        // its source is throttled or temporarily unavailable.
        val orderedProviders = providers.get().sortedByDescending(EpgProvider::priority)
        orderedProviders.forEach { provider ->
            val lastRefresh = lastSuccessfulRefresh[provider.id]
            if (lastRefresh != null &&
                java.time.Duration.between(lastRefresh, now) < provider.minimumRefreshInterval
            ) {
                return@forEach
            }
            attemptedProviders += 1
            try {
                val result = provider.fetch()
                val programs = parser.parse(result.xml)
                require(programs.isNotEmpty()) {
                    "EPG source ${provider.id} returned no valid programmes"
                }
                val protected = orderedProviders.filter { it.priority > provider.priority }
                    .mapNotNull { replacementCoverage[it.id] }
                val completeChannels = protected.flatMap { it.completeChannels }.toSet()
                val windows = protected.flatMap { it.windows }
                val accepted = programs.filter { program ->
                    program.channelId !in completeChannels && windows.none { window ->
                        window.channelId == program.channelId && program.end > window.from && program.start < window.to
                    }
                }
                if (accepted.isNotEmpty()) {
                    val replacementWindows = if (protected.isEmpty()) result.replacementWindows else {
                        val requested = result.replacementWindows ?: programs.map { it.channelId }.distinct().map {
                            EpgReplacementWindow(it, Instant.ofEpochMilli(Long.MIN_VALUE), Instant.ofEpochMilli(Long.MAX_VALUE))
                        }
                        requested.filter { it.channelId !in completeChannels }.flatMap { requestedWindow ->
                            windows.filter { it.channelId == requestedWindow.channelId }.fold(listOf(requestedWindow)) { remaining, mask ->
                                remaining.flatMap { window ->
                                    if (mask.to <= window.from || mask.from >= window.to) listOf(window) else buildList {
                                        if (mask.from > window.from) add(window.copy(to = mask.from))
                                        if (mask.to < window.to) add(window.copy(from = mask.to))
                                    }
                                }
                            }
                        }
                    }
                    programDao.applyUpdate(
                        programs = accepted.map { it.toEntity() },
                        replacementWindows = replacementWindows,
                        expiredBefore = now.minus(java.time.Duration.ofDays(2)).toEpochMilli(),
                        sourcePriority = provider.priority,
                    )
                }
                replacementCoverage[provider.id] = Coverage(
                    completeChannels = if (result.replacementWindows == null) programs.map { it.channelId }.toSet() else emptySet(),
                    windows = if (result.replacementWindows == null) emptyList() else result.replacementWindows + programs.map {
                        EpgReplacementWindow(it.channelId, it.start, it.end)
                    },
                )
                lastSuccessfulRefresh[provider.id] = now
                updatedProviders += 1
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error
            }
        }
        if (attemptedProviders > 0 && updatedProviders == 0) {
            throw IllegalStateException(
                "All EPG sources failed",
                failures.firstOrNull(),
            )
        }
    }

    private fun com.tellyo.app.core.database.ProgramEntity.toDomain(): Program = Program(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        start = Instant.ofEpochMilli(startEpochMillis),
        end = Instant.ofEpochMilli(endEpochMillis),
        category = category,
        artworkUrl = artworkUrl,
    )

    private fun Program.toEntity(): com.tellyo.app.core.database.ProgramEntity =
        com.tellyo.app.core.database.ProgramEntity(
            id = id,
            channelId = channelId,
            title = title,
            description = description,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
            category = category,
            artworkUrl = artworkUrl,
        )
}

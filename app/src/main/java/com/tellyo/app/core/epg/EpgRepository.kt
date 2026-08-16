package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Program
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface EpgProvider {
    val id: String

    /** Returns an XMLTV document from a legal, configured source. */
    suspend fun fetchXml(): String
}

interface EpgRepository {
    fun observePrograms(channelId: String, from: Instant, to: Instant): Flow<List<Program>>

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
    private val provider: EpgProvider,
    private val parser: XmlTvParser,
) : EpgRepository {
    override fun observePrograms(channelId: String, from: Instant, to: Instant): Flow<List<Program>> =
        programDao.observeWindow(channelId, from.toEpochMilli(), to.toEpochMilli())
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh() {
        val programs = parser.parse(provider.fetchXml())
        require(programs.isNotEmpty()) { "EPG source returned no valid programmes" }
        programDao.replaceAll(programs.map { it.toEntity() })
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

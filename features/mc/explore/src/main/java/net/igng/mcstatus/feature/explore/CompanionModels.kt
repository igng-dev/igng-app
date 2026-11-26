package net.igng.mcstatus.feature.explore

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import net.igng.mcstatus.core.data.HallFakePlayerDto
import net.igng.mcstatus.core.data.HallLandDto
import net.igng.mcstatus.core.data.PlayerFakePlayerDto
import net.igng.mcstatus.core.data.PlayerLandDto
import net.igng.mcstatus.core.data.PlayerTeleportDto
import net.igng.mcstatus.core.data.ServerFamilyDto
import net.igng.mcstatus.core.data.ServerSeasonDto
import net.igng.mcstatus.core.model.McTextParser
import net.igng.mcstatus.core.model.ServerThemeKey

const val ARCHIVE_TAB_KEY = "archive"
private const val MAX_FLATTENED_ACTIVE_SERVERS = 6

/**
 * Top-level page target inside the server-centric Play Companion workbench.
 */
sealed interface CompanionTabTarget {
    val key: String
    val shortTitle: String
    val themeKey: ServerThemeKey

    /**
     * An actively playable server page (either an individual alive season when total active
     * servers <= 6, or a family page with an inline alive-season switcher if > 6).
     */
    data class ActiveServer(
        override val key: String,
        val family: ServerFamilyDto,
        val season: ServerSeasonDto,
        override val shortTitle: String,
        override val themeKey: ServerThemeKey,
        val siblingAliveSeasons: List<ServerSeasonDto> = emptyList(),
    ) : CompanionTabTarget {
        val serverId: Int? get() = season.serverId

        val fullTitle: String
            get() {
                val label = season.label.trim()
                val famName = family.name.trim()
                return when {
                    label.isBlank() || label.equals(famName, ignoreCase = true) -> famName
                    family.kind.equals("single", ignoreCase = true) && label == "长期服" -> famName
                    else -> "$famName · $label"
                }
            }
    }

    /**
     * Dedicated Archive & Global Community Hub tab: hibernating wakeable seasons, retired seasons
     * with map snapshots, Perfect Roster, public tickets, and global status comparison.
     */
    data class ArchiveHub(
        override val key: String = ARCHIVE_TAB_KEY,
        override val shortTitle: String = "档案馆",
        override val themeKey: ServerThemeKey = ServerThemeKey.MCSERVERS,
        val archiveFamily: ServerFamilyDto?,
        val allFamilies: List<ServerFamilyDto>,
    ) : CompanionTabTarget {
        val hibernatingSeasons: List<Pair<ServerFamilyDto, ServerSeasonDto>>
            get() = allFamilies.flatMap { fam ->
                fam.allKnownSeasons
                    .filter { it.state.equals("hibernating", ignoreCase = true) }
                    .map { fam to it }
            }

        val retiredGroups: List<Pair<ServerFamilyDto, List<ServerSeasonDto>>>
            get() = allFamilies.mapNotNull { fam ->
                val retired = fam.allKnownSeasons.filter { it.state.equals("retired", ignoreCase = true) }
                if (retired.isEmpty()) null else fam to retired
            }
    }
}

fun isArchiveFamily(family: ServerFamilyDto): Boolean =
    family.kind.equals("archive", ignoreCase = true) ||
        family.slug.equals("archive", ignoreCase = true) ||
        family.slug.equals("mcservers", ignoreCase = true)

/**
 * Builds the top server tab list from the catalog response:
 * - Active playable seasons are flattened directly into top tabs when count <= 6 so players
 *   can reach and swipe directly to servers like "纯净 · 四周目" and "纯净 · X周目".
 * - Gracefully falls back to family-level tabs with an inline season switcher if > 6 active
 *   seasons exist in the future.
 * - Always appends the Archive & Community Hub tab at the end.
 */
fun buildCompanionTabs(families: List<ServerFamilyDto>): List<CompanionTabTarget> {
    if (families.isEmpty()) return emptyList()
    val sorted = families.sortedBy { it.sortOrder }
    val archiveFamily = sorted.firstOrNull(::isArchiveFamily)
    val playableFamilies = sorted.filterNot(::isArchiveFamily)

    val aliveEntries = playableFamilies.flatMap { fam ->
        val alive = fam.aliveSeasons.ifEmpty {
            listOfNotNull(
                fam.primarySeason?.takeUnless {
                    it.state.equals("retired", ignoreCase = true) ||
                        it.state.equals("hibernating", ignoreCase = true)
                },
            )
        }
        alive.map { season -> fam to season }
    }

    val activeTabs = if (aliveEntries.size <= MAX_FLATTENED_ACTIVE_SERVERS) {
        aliveEntries.map { (fam, season) ->
            val themeKey = ServerThemeKey.resolve(season.backgroundKey ?: fam.themeKey ?: fam.slug)
            val key = "${fam.slug}:${season.slug}"
            CompanionTabTarget.ActiveServer(
                key = key,
                family = fam,
                season = season,
                shortTitle = formatCompactServerTabTitle(fam, season, isMultiAlive = fam.aliveSeasons.size > 1),
                themeKey = themeKey,
                siblingAliveSeasons = emptyList(),
            )
        }
    } else {
        playableFamilies.mapNotNull { fam ->
            val primary = fam.primarySeason ?: fam.aliveSeasons.firstOrNull() ?: return@mapNotNull null
            val themeKey = ServerThemeKey.resolve(primary.backgroundKey ?: fam.themeKey ?: fam.slug)
            CompanionTabTarget.ActiveServer(
                key = fam.slug,
                family = fam,
                season = primary,
                shortTitle = fam.name.ifBlank { fam.slug },
                themeKey = themeKey,
                siblingAliveSeasons = fam.aliveSeasons,
            )
        }
    }

    return activeTabs + CompanionTabTarget.ArchiveHub(
        archiveFamily = archiveFamily,
        allFamilies = sorted,
    )
}

/**
 * Formats a concise, readable Chinese tab title for narrow phone widths while keeping multi-season
 * siblings clearly distinguishable (e.g. "纯净 · 四周目", "纯净 · X周目", "MOD · 十三周目", "作弊服").
 */
fun formatCompactServerTabTitle(
    family: ServerFamilyDto,
    season: ServerSeasonDto,
    isMultiAlive: Boolean = false,
): String {
    val famName = family.name.trim().ifBlank { family.slug }
    val seasonLabel = season.label.trim()
    val isSingleKind = family.kind.equals("single", ignoreCase = true)
    if (seasonLabel.isBlank() || (!isMultiAlive && isSingleKind) || seasonLabel.equals(famName, ignoreCase = true)) {
        return famName
    }
    val shortFam = famName
        .removePrefix("IGNG")
        .trim()
        .removeSuffix("周目服")
        .removeSuffix("服")
        .trim()
        .ifBlank { famName }
    val shortSeason = seasonLabel
        .removePrefix("第")
        .trim()
        .ifBlank { seasonLabel }
    return "$shortFam · $shortSeason"
}

/**
 * Resolves the initial tab index from persisted last-visited server key, falling back to index 0.
 */
fun resolveInitialTabIndex(tabs: List<CompanionTabTarget>, persistedKey: String?): Int {
    if (tabs.isEmpty()) return 0
    if (persistedKey.isNullOrBlank()) return 0
    val exact = tabs.indexOfFirst { it.key == persistedKey }
    if (exact >= 0) return exact
    // Graceful fallback if flattened <-> family mode changed: match by family slug prefix
    val familyPrefix = persistedKey.substringBefore(':')
    val byFamily = tabs.indexOfFirst { tab ->
        tab is CompanionTabTarget.ActiveServer && tab.family.slug == familyPrefix
    }
    return if (byFamily >= 0) byFamily else 0
}

enum class CompanionMyToolTab(val label: String) {
    FAKES("假人"),
    TELEPORTS("传送点"),
    LANDS("领地"),
}

/**
 * Prioritizes active AFK fake players whenever the player has any running on the current server,
 * otherwise defaults to teleports -> lands.
 */
fun resolveDefaultMyToolTab(
    myFakesCount: Int,
    myTeleportsCount: Int,
    myLandsCount: Int,
): CompanionMyToolTab = when {
    myFakesCount > 0 -> CompanionMyToolTab.FAKES
    myTeleportsCount > 0 -> CompanionMyToolTab.TELEPORTS
    myLandsCount > 0 -> CompanionMyToolTab.LANDS
    else -> CompanionMyToolTab.TELEPORTS
}

enum class FakeHealthTone {
    NORMAL,
    WARNING,
    CRITICAL,
}

fun evaluateFakeHealthTone(health: Double?): FakeHealthTone = when {
    health == null -> FakeHealthTone.NORMAL
    health < 10.0 -> FakeHealthTone.CRITICAL
    health < 16.0 -> FakeHealthTone.WARNING
    else -> FakeHealthTone.NORMAL
}

enum class CompanionEcologyTab(val label: String) {
    LANDS("公开领地"),
    FAKES("在线假人"),
}

enum class RadarMarkerKind {
    MY_TELEPORT,
    MY_LAND,
    FAKE_PLAYER,
    PUBLIC_LAND,
}

data class RadarMarker(
    val id: String,
    val label: String,
    val world: String,
    val rawX: Double,
    val rawZ: Double,
    val normX: Float,
    val normZ: Float,
    val kind: RadarMarkerKind,
)

data class ServerRadarProjection(
    val worldKey: String,
    val markers: List<RadarMarker>,
    val spanBlocks: Int,
)

private data class RawRadarPoint(
    val id: String,
    val label: String,
    val world: String,
    val x: Double,
    val z: Double,
    val kind: RadarMarkerKind,
)

private fun normalizeWorldBucket(world: String?): String = when (world?.trim()?.lowercase()) {
    "world", "overworld", "minecraft:overworld", "", null -> "world"
    "world_nether", "nether", "minecraft:the_nether" -> "world_nether"
    "world_the_end", "the_end", "minecraft:the_end" -> "world_the_end"
    else -> world.trim().lowercase()
}

/**
 * Projects real in-game (x, z) coordinates of the active server's teleports, lands, and fake
 * players onto a bounded [0.12f, 0.88f] normalized canvas for a zero-WebView, 60fps map preview.
 */
fun buildServerRadarProjection(
    myTeleports: List<PlayerTeleportDto>,
    myLands: List<PlayerLandDto>,
    myFakes: List<PlayerFakePlayerDto>,
    publicLands: List<HallLandDto>,
    publicFakes: List<HallFakePlayerDto>,
): ServerRadarProjection? {
    val raw = buildList {
        myTeleports.forEach { tp ->
            val x = tp.x ?: return@forEach
            val z = tp.z ?: return@forEach
            add(RawRadarPoint("tp:${tp.id}", tp.point_name.orEmpty(), normalizeWorldBucket(tp.world), x, z, RadarMarkerKind.MY_TELEPORT))
        }
        myLands.forEach { land ->
            val x = land.x ?: return@forEach
            val z = land.z ?: return@forEach
            add(RawRadarPoint("ml:${land.id}", land.land_name.orEmpty(), normalizeWorldBucket(land.world), x, z, RadarMarkerKind.MY_LAND))
        }
        val myFakeNames = HashSet<String>()
        myFakes.forEach { fp ->
            val x = fp.x ?: return@forEach
            val z = fp.z ?: return@forEach
            val name = fp.fake_name.orEmpty()
            myFakeNames.add(name.lowercase())
            add(RawRadarPoint("mf:${fp.id}", name, normalizeWorldBucket(fp.world), x, z, RadarMarkerKind.FAKE_PLAYER))
        }
        publicFakes.forEach { fp ->
            val x = fp.x ?: return@forEach
            val z = fp.z ?: return@forEach
            if (fp.name.lowercase() in myFakeNames) return@forEach
            add(RawRadarPoint("pf:${fp.name}", fp.name, normalizeWorldBucket(fp.world), x, z, RadarMarkerKind.FAKE_PLAYER))
        }
        publicLands.take(24).forEach { land ->
            val x = land.center.x ?: return@forEach
            val z = land.center.z ?: return@forEach
            add(RawRadarPoint("pl:${land.id}", land.name, normalizeWorldBucket(land.world), x, z, RadarMarkerKind.PUBLIC_LAND))
        }
    }

    if (raw.isEmpty()) return null

    // Pick Overworld ("world") whenever available, otherwise the most populated dimension.
    val chosenWorld = if (raw.any { it.world == "world" }) {
        "world"
    } else {
        raw.groupingBy { it.world }.eachCount().maxByOrNull { it.value }?.key ?: raw.first().world
    }
    val worldPoints = raw.filter { it.world == chosenWorld }
    if (worldPoints.isEmpty()) return null

    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var minZ = Double.POSITIVE_INFINITY
    var maxZ = Double.NEGATIVE_INFINITY
    worldPoints.forEach { pt ->
        minX = min(minX, pt.x)
        maxX = max(maxX, pt.x)
        minZ = min(minZ, pt.z)
        maxZ = max(maxZ, pt.z)
    }

    val spanX = maxX - minX
    val spanZ = maxZ - minZ
    val span = max(max(spanX, spanZ), 120.0)
    val centerX = (minX + maxX) * 0.5
    val centerZ = (minZ + maxZ) * 0.5

    val projected = worldPoints.map { pt ->
        val nx = if (worldPoints.size == 1 || (abs(spanX) < 1.0 && abs(spanZ) < 1.0)) {
            0.5f
        } else {
            (0.5 + ((pt.x - centerX) / span) * 0.74).toFloat().coerceIn(0.12f, 0.88f)
        }
        val nz = if (worldPoints.size == 1 || (abs(spanX) < 1.0 && abs(spanZ) < 1.0)) {
            0.5f
        } else {
            (0.5 + ((pt.z - centerZ) / span) * 0.70).toFloat().coerceIn(0.14f, 0.86f)
        }
        RadarMarker(
            id = pt.id,
            label = pt.label,
            world = pt.world,
            rawX = pt.x,
            rawZ = pt.z,
            normX = nx,
            normZ = nz,
            kind = pt.kind,
        )
    }

    return ServerRadarProjection(
        worldKey = chosenWorld,
        markers = projected,
        spanBlocks = span.toInt(),
    )
}

/**
 * Strips Minecraft color codes and normalizes whitespace for compact single-line chat preview rows.
 */
fun cleanMcChatPreview(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return ""
    return McTextParser.stripFormatting(text)
        .replace(Regex("\\s+"), " ")
        .trim()
}

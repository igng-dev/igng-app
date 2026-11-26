package net.igng.mcstatus.feature.wiki

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import net.igng.core.markdown.TocEntry
import net.igng.mcstatus.core.data.WikiCatalogResponse
import net.igng.mcstatus.core.data.WikiNavNodeDto
import net.igng.mcstatus.core.data.WikiPageSummary

data class WikiBreadcrumb(
    val label: String,
    val folderNodeId: Int? = null,
    val pageId: Int? = null,
) {
    val isFolder: Boolean get() = folderNodeId != null && pageId == null
}

data class WikiTreeNode(
    val id: Int,
    val title: String,
    val path: String,
    val isFolder: Boolean,
    val pageId: Int?,
    val depth: Int,
    val pageCount: Int,
    val children: List<WikiTreeNode>,
)

data class WikiLinearPage(
    val page: WikiPageSummary,
    val breadcrumbs: List<WikiBreadcrumb>,
    val ancestorFolderIds: Set<Int>,
    val fullPath: String,
) {
    val folderLabel: String
        get() = breadcrumbs.drop(1).dropLast(1).joinToString(" / ") { it.label }
            .ifBlank { breadcrumbs.firstOrNull()?.label.orEmpty() }
}

/**
 * Flattened mobile-friendly tutorial row inside a top-level [WikiCategorySection].
 * Keeps nested sub-folder context in [subGroupLabel] so narrow mobile screens never crush
 * deep titles with multi-level horizontal indentation.
 */
data class WikiCategoryRow(
    val pageId: Int,
    val title: String,
    val updatedAt: String?,
    val subGroupLabel: String?,
    val isOverview: Boolean,
)

data class WikiCategorySection(
    val id: String,
    val folderNodeId: Int?,
    val title: String,
    val items: List<WikiCategoryRow>,
)

/**
 * Hierarchical & linear navigation index built from [WikiCatalogResponse]:
 * - Preserves the real multi-level folder/page tree for the full-width bottom directory sheet
 * - Builds flattened mobile-friendly [sections] for the catalog hub horizontal category filter
 * - Computes DFS linear order for standard Wiki Prev / Next page navigation
 * - Computes Breadcrumb trails and ancestor folder sets for active-node auto-expansion
 * - Resolves internal `docs.igng.net/<kb>/...` / `wiki.igng.net/<kb>/...` / relative Wiki links in-app
 */
class WikiCatalogIndex(val catalog: WikiCatalogResponse) {
    val kbSlug: String = catalog.knowledgeBase?.slug?.trim()?.ifBlank { "IGNGmc" } ?: "IGNGmc"
    val kbName: String = catalog.knowledgeBase?.name?.trim()?.ifBlank { "IGNGmc 百科" } ?: "IGNGmc 百科"
    val kbDescription: String = catalog.knowledgeBase?.description?.trim().orEmpty()
    val rootPage: WikiPageSummary? = catalog.knowledgeBase?.rootPage?.takeIf { it.id > 0 }

    val tree: List<WikiTreeNode>
    val linearPages: List<WikiLinearPage>
    val sections: List<WikiCategorySection>
    private val entryByPageId: Map<Int, WikiLinearPage>
    private val indexByPageId: Map<Int, Int>
    private val pageIdByNormalizedPath: Map<String, Int>
    private val pageIdByNormalizedSlug: Map<String, Int>

    init {
        val linear = mutableListOf<WikiLinearPage>()
        val seenIds = HashSet<Int>()
        val rootCrumb = WikiBreadcrumb(label = kbName, folderNodeId = null, pageId = rootPage?.id)

        rootPage?.let { rp ->
            seenIds += rp.id
            linear += WikiLinearPage(
                page = rp.copy(
                    title = rp.title.ifBlank { kbName },
                    groupTitle = kbName,
                ),
                breadcrumbs = listOf(WikiBreadcrumb(label = kbName, folderNodeId = null, pageId = rp.id)),
                ancestorFolderIds = emptySet(),
                fullPath = normalizePathSegments(rp.path.ifBlank { rp.slug }),
            )
        }

        fun buildNode(
            node: WikiNavNodeDto,
            depth: Int,
            parentCrumbs: List<WikiBreadcrumb>,
            ancestorFolders: Set<Int>,
            topGroupTitle: String?,
        ): WikiTreeNode? {
            val nodePage = node.page
            val isFolder = node.nodeType.equals("folder", ignoreCase = true) || node.children.isNotEmpty()
            val nodeTitle = node.title.ifBlank { nodePage?.title.orEmpty() }.ifBlank { "未命名文档" }
            val effectiveGroup = topGroupTitle ?: if (isFolder) nodeTitle else kbName
            val nextAncestors = if (isFolder) ancestorFolders + node.id else ancestorFolders
            val selfCrumb = WikiBreadcrumb(
                label = nodeTitle,
                folderNodeId = if (isFolder) node.id else null,
                pageId = nodePage?.id,
            )
            val currentCrumbs = parentCrumbs + selfCrumb

            // Avoid duplicating the root page if the server also emits it as a standalone leaf
            if (!isFolder && nodePage != null && nodePage.id == rootPage?.id) {
                return null
            }

            nodePage?.takeIf { it.id > 0 }?.let { p ->
                if (seenIds.add(p.id)) {
                    val pagePath = normalizePathSegments(node.path.ifBlank { p.path }.ifBlank { p.slug })
                    linear += WikiLinearPage(
                        page = p.copy(
                            title = p.title.ifBlank { nodeTitle },
                            path = pagePath,
                            groupTitle = effectiveGroup,
                        ),
                        breadcrumbs = currentCrumbs,
                        ancestorFolderIds = ancestorFolders,
                        fullPath = pagePath,
                    )
                }
            }

            val builtChildren = node.children
                .sortedWith(compareBy<WikiNavNodeDto> { it.sortOrder }.thenBy { it.id })
                .mapNotNull { child ->
                    buildNode(
                        node = child,
                        depth = depth + 1,
                        parentCrumbs = currentCrumbs,
                        ancestorFolders = nextAncestors,
                        topGroupTitle = effectiveGroup,
                    )
                }

            val leafCount = (if (nodePage != null && nodePage.id > 0) 1 else 0) +
                builtChildren.sumOf { it.pageCount }

            if (isFolder && leafCount == 0 && nodePage == null) return null

            return WikiTreeNode(
                id = node.id,
                title = nodeTitle,
                path = normalizePathSegments(node.path.ifBlank { node.slug }),
                isFolder = isFolder,
                pageId = nodePage?.id?.takeIf { it > 0 },
                depth = depth,
                pageCount = leafCount,
                children = builtChildren,
            )
        }

        tree = catalog.nodes
            .sortedWith(compareBy<WikiNavNodeDto> { it.sortOrder }.thenBy { it.id })
            .mapNotNull { node ->
                buildNode(
                    node = node,
                    depth = 0,
                    parentCrumbs = listOf(rootCrumb),
                    ancestorFolders = emptySet(),
                    topGroupTitle = null,
                )
            }

        linearPages = linear
        entryByPageId = linear.associateBy { it.page.id }
        indexByPageId = linear.mapIndexed { idx, entry -> entry.page.id to idx }.toMap()

        val builtSections = mutableListOf<WikiCategorySection>()
        val looseRows = mutableListOf<WikiCategoryRow>()
        val sectionedPageIds = HashSet<Int>()

        fun collectRows(node: WikiTreeNode, subGroupParts: List<String>, out: MutableList<WikiCategoryRow>) {
            val subLabel = subGroupParts.joinToString(" / ").ifBlank { null }
            node.pageId?.let { pid ->
                if (sectionedPageIds.add(pid)) {
                    val entry = entryByPageId[pid]
                    out += WikiCategoryRow(
                        pageId = pid,
                        title = entry?.page?.title?.ifBlank { node.title } ?: node.title,
                        updatedAt = entry?.page?.updatedAt,
                        subGroupLabel = subLabel,
                        isOverview = node.isFolder,
                    )
                }
            }
            node.children.forEach { child ->
                val nextSub = if (child.isFolder) subGroupParts + child.title else subGroupParts
                collectRows(child, nextSub, out)
            }
        }

        tree.forEach { topNode ->
            if (topNode.isFolder) {
                val rows = mutableListOf<WikiCategoryRow>()
                collectRows(topNode, emptyList(), rows)
                if (rows.isNotEmpty()) {
                    builtSections += WikiCategorySection(
                        id = "folder_${topNode.id}",
                        folderNodeId = topNode.id,
                        title = topNode.title,
                        items = rows,
                    )
                }
            } else {
                topNode.pageId?.let { pid ->
                    if (sectionedPageIds.add(pid)) {
                        val entry = entryByPageId[pid]
                        looseRows += WikiCategoryRow(
                            pageId = pid,
                            title = entry?.page?.title?.ifBlank { topNode.title } ?: topNode.title,
                            updatedAt = entry?.page?.updatedAt,
                            subGroupLabel = null,
                            isOverview = false,
                        )
                    }
                }
            }
        }

        if (looseRows.isNotEmpty()) {
            builtSections.add(
                0,
                WikiCategorySection(
                    id = "basic_loose",
                    folderNodeId = null,
                    title = "基础指南",
                    items = looseRows,
                ),
            )
        } else if (builtSections.isEmpty() && rootPage != null) {
            builtSections += WikiCategorySection(
                id = "kb_root_only",
                folderNodeId = null,
                title = kbName,
                items = listOf(
                    WikiCategoryRow(
                        pageId = rootPage.id,
                        title = rootPage.title.ifBlank { kbName },
                        updatedAt = rootPage.updatedAt,
                        subGroupLabel = null,
                        isOverview = true,
                    ),
                ),
            )
        }
        sections = builtSections

        val pathMap = LinkedHashMap<String, Int>()
        val slugMap = LinkedHashMap<String, Int>()
        linear.forEach { entry ->
            val pid = entry.page.id
            val normPath = normalizePathSegments(entry.fullPath)
            if (normPath.isNotEmpty()) {
                pathMap.putIfAbsent(normPath.lowercase(), pid)
            }
            val normSlug = normalizePathSegments(entry.page.slug)
            if (normSlug.isNotEmpty()) {
                slugMap.putIfAbsent(normSlug.lowercase(), pid)
            }
            val normTitle = normalizePathSegments(entry.page.title)
            if (normTitle.isNotEmpty()) {
                slugMap.putIfAbsent(normTitle.lowercase(), pid)
            }
        }
        pageIdByNormalizedPath = pathMap
        pageIdByNormalizedSlug = slugMap
    }

    val defaultPageId: Int?
        get() = rootPage?.id ?: linearPages.firstOrNull()?.page?.id

    val totalPages: Int
        get() = linearPages.size

    fun entryOf(pageId: Int): WikiLinearPage? = entryByPageId[pageId]

    fun prevOf(pageId: Int): WikiLinearPage? {
        val idx = indexByPageId[pageId] ?: return null
        return if (idx > 0) linearPages[idx - 1] else null
    }

    fun nextOf(pageId: Int): WikiLinearPage? {
        val idx = indexByPageId[pageId] ?: return null
        return if (idx + 1 < linearPages.size) linearPages[idx + 1] else null
    }

    fun breadcrumbsOf(pageId: Int): List<WikiBreadcrumb> =
        entryByPageId[pageId]?.breadcrumbs ?: listOf(WikiBreadcrumb(kbName, null, rootPage?.id))

    fun ancestorFoldersOf(pageId: Int): Set<Int> =
        entryByPageId[pageId]?.ancestorFolderIds.orEmpty()

    fun firstTopFolderIdOf(pageId: Int): Int? {
        val ancestors = ancestorFoldersOf(pageId)
        return tree.firstOrNull { it.isFolder && (it.id in ancestors || it.pageId == pageId) }?.id
    }

    fun publicWebUrlOf(pageId: Int, baseUrl: String = "https://docs.igng.net"): String {
        val cleanBase = baseUrl.trimEnd('/')
        val encodedKb = encodeSegment(kbSlug)
        if (rootPage != null && pageId == rootPage.id) {
            return "$cleanBase/$encodedKb"
        }
        val rawPath = entryByPageId[pageId]?.fullPath.orEmpty()
        val segments = decodePathSegments(rawPath)
        if (segments.isEmpty()) return "$cleanBase/$encodedKb"
        val withoutKb = if (segments.first().equals(kbSlug, ignoreCase = true)) {
            segments.drop(1)
        } else {
            segments
        }
        if (withoutKb.isEmpty()) return "$cleanBase/$encodedKb"
        return "$cleanBase/$encodedKb/${withoutKb.joinToString("/") { encodeSegment(it) }}"
    }

    fun search(query: String): List<WikiLinearPage> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return linearPages
        return linearPages.filter { entry ->
            entry.page.title.lowercase().contains(q) ||
                entry.folderLabel.lowercase().contains(q) ||
                entry.fullPath.lowercase().contains(q) ||
                (entry.page.summary?.lowercase()?.contains(q) == true)
        }
    }

    /**
     * Resolves an internal Wiki link (absolute `docs.igng.net/<kb>/...`, `wiki.igng.net/<kb>/...`,
     * root-relative `/<kb>/...`, or page-relative `./...` / `../...`) to a catalog `pageId`,
     * or returns `null` if the URL is an external link.
     */
    fun resolveWikiLink(rawUrl: String, currentPagePath: String? = null): Int? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val stripped = trimmed.substringBefore('#').substringBefore('?').trim()
        if (stripped.isEmpty()) return null

        val relativeWithinKb: String = when {
            stripped.startsWith("http://", ignoreCase = true) || stripped.startsWith("https://", ignoreCase = true) -> {
                val uri = runCatching { URI(stripped) }.getOrNull() ?: return null
                val host = uri.host?.lowercase().orEmpty()
                if (host !in WIKI_HOSTS) return null
                extractKbSubPath(uri.rawPath.orEmpty())
            }
            stripped.startsWith("/") -> {
                extractKbSubPath(stripped)
            }
            else -> {
                resolveRelativePath(currentPagePath.orEmpty(), stripped)
            }
        } ?: return null

        val normalized = normalizePathSegments(relativeWithinKb).lowercase()
        if (normalized.isEmpty()) {
            return rootPage?.id
        }

        pageIdByNormalizedPath[normalized]?.let { return it }
        pageIdByNormalizedSlug[normalized]?.let { return it }
        val lastSegment = normalized.substringAfterLast('/')
        if (lastSegment.isNotEmpty()) {
            pageIdByNormalizedSlug[lastSegment]?.let { return it }
        }
        return null
    }

    private fun extractKbSubPath(rawPath: String): String? {
        val decoded = decodePathSegments(rawPath)
        if (decoded.isEmpty()) return null
        val first = decoded.first()
        if (first.equals(kbSlug, ignoreCase = true)) {
            return decoded.drop(1).joinToString("/")
        }
        // Some docs omit the KB prefix and link directly like `/插件教程/IGNGEconomy`
        val joined = decoded.joinToString("/").lowercase()
        if (pageIdByNormalizedPath.containsKey(joined) || pageIdByNormalizedSlug.containsKey(joined)) {
            return decoded.joinToString("/")
        }
        return null
    }

    private fun resolveRelativePath(currentPath: String, relative: String): String {
        val baseSegments = decodePathSegments(currentPath).dropLast(1).toMutableList()
        val relSegments = decodePathSegments(relative)
        for (seg in relSegments) {
            when (seg) {
                ".", "" -> Unit
                ".." -> if (baseSegments.isNotEmpty()) baseSegments.removeAt(baseSegments.lastIndex)
                else -> baseSegments += seg
            }
        }
        return baseSegments.joinToString("/")
    }

    companion object {
        private val WIKI_HOSTS = setOf("docs.igng.net", "wiki.igng.net")

        private fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

        private fun decodePathSegments(raw: String): List<String> =
            raw.trim()
                .trim('/')
                .split('/')
                .filter { it.isNotBlank() }
                .map { seg ->
                    runCatching { URLDecoder.decode(seg.replace("+", "%2B"), "UTF-8") }
                        .getOrDefault(seg)
                        .trim()
                }
                .filter { it.isNotEmpty() }

        private fun normalizePathSegments(raw: String): String =
            decodePathSegments(raw).joinToString("/")
    }
}

fun estimateWikiReadingMinutes(content: String): Int {
    if (content.isBlank()) return 1
    val cjkCount = content.count { ch -> ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF }
    val wordCount = content.split(Regex("\\s+")).count { token ->
        token.any { it.isLetterOrDigit() } && token.none { it.code in 0x4E00..0x9FFF }
    }
    val effectiveUnits = cjkCount + wordCount * 2
    return ((effectiveUnits + 379) / 380).coerceAtLeast(1)
}

fun findActiveTocIndex(toc: List<TocEntry>, currentBlockIndex: Int): Int {
    if (toc.isEmpty()) return -1
    var active = 0
    for (i in toc.indices) {
        if (toc[i].blockIndex <= currentBlockIndex) {
            active = i
        } else {
            break
        }
    }
    return active
}

package net.igng.mcstatus.feature.wiki

import net.igng.core.markdown.TocEntry
import net.igng.mcstatus.core.data.WikiCatalogResponse
import net.igng.mcstatus.core.data.WikiKnowledgeBaseDto
import net.igng.mcstatus.core.data.WikiNavNodeDto
import net.igng.mcstatus.core.data.WikiPageSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiNavigationLogicTest {

    private fun sampleCatalog(): WikiCatalogResponse = WikiCatalogResponse(
        knowledgeBase = WikiKnowledgeBaseDto(
            id = 1,
            slug = "IGNGmc",
            name = "IGNGmc",
            rootPage = WikiPageSummary(id = 7, slug = "", title = "欢迎来到 IGNGmc", path = ""),
        ),
        nodes = listOf(
            WikiNavNodeDto(
                id = 100,
                nodeType = "folder",
                slug = "服务器基础",
                title = "服务器基础",
                sortOrder = 1,
                path = "服务器基础",
                children = listOf(
                    WikiNavNodeDto(
                        id = 101,
                        parentId = 100,
                        nodeType = "page",
                        slug = "服务器规则",
                        title = "服务器规则",
                        sortOrder = 1,
                        path = "服务器基础/服务器规则",
                        page = WikiPageSummary(id = 11, slug = "服务器规则", title = "服务器规则", path = "服务器基础/服务器规则"),
                    ),
                    WikiNavNodeDto(
                        id = 102,
                        parentId = 100,
                        nodeType = "page",
                        slug = "新手指南",
                        title = "新手指南",
                        sortOrder = 2,
                        path = "服务器基础/新手指南",
                        page = WikiPageSummary(id = 12, slug = "新手指南", title = "新手指南", path = "服务器基础/新手指南"),
                    ),
                ),
            ),
            WikiNavNodeDto(
                id = 200,
                nodeType = "folder",
                slug = "插件教程",
                title = "插件教程",
                sortOrder = 2,
                path = "插件教程",
                children = listOf(
                    WikiNavNodeDto(
                        id = 201,
                        parentId = 200,
                        nodeType = "page",
                        slug = "Residence领地插件",
                        title = "Residence领地插件",
                        sortOrder = 1,
                        path = "插件教程/Residence领地插件",
                        page = WikiPageSummary(id = 21, slug = "Residence领地插件", title = "Residence领地插件", path = "插件教程/Residence领地插件"),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun buildsTreeLinearPrevNextBreadcrumbsAndAncestors() {
        val index = WikiCatalogIndex(sampleCatalog())
        assertEquals(7, index.defaultPageId)
        assertEquals(4, index.totalPages)
        assertEquals(2, index.tree.size)
        assertEquals(2, index.tree[0].pageCount)
        assertEquals(1, index.tree[1].pageCount)

        // Linear DFS order: 7 (Home) -> 11 (规则) -> 12 (新手) -> 21 (Residence)
        assertNull(index.prevOf(7))
        assertEquals(11, index.nextOf(7)?.page?.id)
        assertEquals(7, index.prevOf(11)?.page?.id)
        assertEquals(12, index.nextOf(11)?.page?.id)
        assertEquals(12, index.prevOf(21)?.page?.id)
        assertNull(index.nextOf(21))

        val crumbs = index.breadcrumbsOf(21)
        assertEquals(listOf("IGNGmc", "插件教程", "Residence领地插件"), crumbs.map { it.label })
        assertEquals(setOf(200), index.ancestorFoldersOf(21))
    }

    @Test
    fun resolvesInternalDocsWikiAndRelativeLinksInApp() {
        val index = WikiCatalogIndex(sampleCatalog())

        // Encoded UTF-8 URL from live docs.igng.net
        assertEquals(
            11,
            index.resolveWikiLink("https://docs.igng.net/IGNGmc/%E6%9C%8D%E5%8A%A1%E5%99%A8%E5%9F%BA%E7%A1%80/%E6%9C%8D%E5%8A%A1%E5%99%A8%E8%A7%84%E5%88%99#section"),
        )
        assertEquals(
            21,
            index.resolveWikiLink("https://wiki.igng.net/IGNGmc/插件教程/Residence领地插件"),
        )
        assertEquals(7, index.resolveWikiLink("https://docs.igng.net/IGNGmc"))
        assertEquals(
            12,
            index.resolveWikiLink("./新手指南", currentPagePath = "服务器基础/服务器规则"),
        )
        assertEquals(
            21,
            index.resolveWikiLink("../插件教程/Residence领地插件", currentPagePath = "服务器基础/服务器规则"),
        )
        // External links must remain external
        assertNull(index.resolveWikiLink("https://www.igng.net/mc"))
        assertNull(index.resolveWikiLink("https://github.com/igng-dev/igng-app"))
    }

    @Test
    fun findsActiveTocHeadingFromVisibleBlockIndexAndSearchesPages() {
        val toc = listOf(
            TocEntry(level = 2, title = "总则", blockIndex = 1),
            TocEntry(level = 2, title = "建筑规范", blockIndex = 6),
            TocEntry(level = 3, title = "红石限制", blockIndex = 10),
        )
        assertEquals(0, findActiveTocIndex(toc, 0))
        assertEquals(0, findActiveTocIndex(toc, 5))
        assertEquals(1, findActiveTocIndex(toc, 6))
        assertEquals(1, findActiveTocIndex(toc, 9))
        assertEquals(2, findActiveTocIndex(toc, 14))

        val index = WikiCatalogIndex(sampleCatalog())
        val hits = index.search("领地")
        assertEquals(1, hits.size)
        assertEquals(21, hits.first().page.id)
        assertTrue(estimateWikiReadingMinutes("测试".repeat(400)) >= 2)
    }
}

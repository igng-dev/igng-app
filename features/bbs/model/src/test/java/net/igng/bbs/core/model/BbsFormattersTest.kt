package net.igng.bbs.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BbsFormattersTest {

    @Test
    fun essayCategoryAndTagFormattingRemovesHashAndSpaces() {
        val essayCat = CategoryDto(id = "cat_13", numericId = 13, name = "Ink 随笔", slug = "essay")
        val techCat = CategoryDto(id = "cat_1", numericId = 1, name = "技术讨论", slug = "tech")

        assertTrue(BbsFormatters.isEssayCategory(essayCat))
        assertFalse(BbsFormatters.isEssayCategory(techCat))

        assertEquals("Ink随笔", BbsFormatters.categoryDisplayName(essayCat))
        assertEquals("技术讨论", BbsFormatters.categoryDisplayName(techCat))
        assertEquals("Ink随笔", BbsFormatters.categoryDisplayName("essay", "Ink 随笔"))
        assertEquals("Ink随笔", BbsFormatters.categoryDisplayName(null, "ink随笔"))

        assertEquals("Ink随笔", BbsFormatters.tagDisplayLabel("ink随笔"))
        assertEquals("Ink随笔", BbsFormatters.tagDisplayLabel("#Ink 随笔"))
        assertEquals("#compose", BbsFormatters.tagDisplayLabel("compose"))
        assertEquals("#compose", BbsFormatters.tagDisplayLabel("#compose"))
    }

    @Test
    fun visiblePostTagsDeduplicatesEssayTagWhenCategoryIsEssay() {
        val essayCat = CategoryDto(id = "cat_13", numericId = 13, name = "Ink 随笔", slug = "essay")
        val otherCat = CategoryDto(id = "cat_11", numericId = 11, name = "其他", slug = "other")

        assertEquals(
            listOf("创作记录"),
            BbsFormatters.visiblePostTags(listOf("ink随笔", "创作记录"), essayCat),
        )
        assertEquals(
            emptyList<String>(),
            BbsFormatters.visiblePostTags(listOf("ink随笔"), essayCat),
        )
        assertEquals(
            listOf("ink随笔", "杂谈"),
            BbsFormatters.visiblePostTags(listOf("ink随笔", "杂谈"), otherCat),
        )
    }
}

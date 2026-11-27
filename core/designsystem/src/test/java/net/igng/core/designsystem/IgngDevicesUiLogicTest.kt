package net.igng.core.designsystem

import net.igng.core.model.CoreDeviceSessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngDevicesUiLogicTest {

    private fun device(
        id: String,
        name: String,
        client: String? = "IGNG Android",
        ip: String? = "198.51.100.12",
        isCurrent: Boolean = false,
    ): CoreDeviceSessionDto = CoreDeviceSessionDto(
        id = id,
        sessionId = "sess_$id",
        deviceName = name,
        clientLabel = client,
        ipAddress = ip,
        isCurrent = isCurrent,
    )

    @Test
    fun entrySummaryAndBadgeCoverLoadingErrorEmptySingleAndMultiDeviceStates() {
        assertEquals(
            "正在同步活跃会话…",
            IgngDevicesUiLogic.buildEntrySummary(emptyList(), totalCount = 0, loading = true, error = null),
        )
        assertNull(
            IgngDevicesUiLogic.buildEntryBadgeText(emptyList(), totalCount = 0, loading = true, error = null),
        )

        assertEquals(
            "未能读取登录设备，点击进入重试",
            IgngDevicesUiLogic.buildEntrySummary(emptyList(), totalCount = 0, loading = false, error = "network error"),
        )
        assertEquals(
            "重试",
            IgngDevicesUiLogic.buildEntryBadgeText(emptyList(), totalCount = 0, loading = false, error = "network error"),
        )

        assertEquals(
            "暂无可展示的活跃设备会话",
            IgngDevicesUiLogic.buildEntrySummary(emptyList(), totalCount = 0, loading = false, error = null),
        )
        assertEquals(
            "0 台",
            IgngDevicesUiLogic.buildEntryBadgeText(emptyList(), totalCount = 0, loading = false, error = null),
        )

        val single = listOf(device("1", "主力手机", isCurrent = true))
        assertEquals(
            "本机：主力手机 · 暂无其他活跃设备",
            IgngDevicesUiLogic.buildEntrySummary(single, totalCount = 1, loading = false, error = null),
        )
        assertEquals(
            "1 台",
            IgngDevicesUiLogic.buildEntryBadgeText(single, totalCount = 1, loading = false, error = null),
        )

        val multiWithServerTotal = listOf(
            device("1", "主力手机", isCurrent = true),
            device("2", "备用平板"),
        )
        assertEquals(
            "本机：主力手机 · 另有 11 台设备",
            IgngDevicesUiLogic.buildEntrySummary(multiWithServerTotal, totalCount = 12, loading = false, error = null),
        )
        assertEquals(
            "12 台",
            IgngDevicesUiLogic.buildEntryBadgeText(multiWithServerTotal, totalCount = 12, loading = false, error = null),
        )
    }

    @Test
    fun partitionAndFilterPlacesCurrentFirstPreservesOrderDeduplicatesAndSearchesFields() {
        val raw = listOf(
            device("2", "MacBook Pro", client = "Web Chrome", ip = "203.0.113.20"),
            device("1", "Pixel 9", client = "IGNGmc Android", ip = "198.51.100.10", isCurrent = true),
            device("2", "MacBook Pro Duplicate", client = "Web Chrome", ip = "203.0.113.20"),
            device("3", "iPad Mini", client = "IGNGbbs iOS", ip = "198.51.100.88"),
        )

        val all = IgngDevicesUiLogic.partitionAndFilterDevices(raw)
        assertEquals(listOf("1"), all.currentDevices.map { it.id })
        assertEquals(listOf("2", "3"), all.otherDevices.map { it.id })

        val byName = IgngDevicesUiLogic.partitionAndFilterDevices(raw, "macbook")
        assertTrue(byName.currentDevices.isEmpty())
        assertEquals(listOf("2"), byName.otherDevices.map { it.id })

        val byIp = IgngDevicesUiLogic.partitionAndFilterDevices(raw, "198.51.100.88")
        assertTrue(byIp.currentDevices.isEmpty())
        assertEquals(listOf("3"), byIp.otherDevices.map { it.id })

        val byClient = IgngDevicesUiLogic.partitionAndFilterDevices(raw, "igngmc")
        assertEquals(listOf("1"), byClient.currentDevices.map { it.id })
        assertTrue(byClient.otherDevices.isEmpty())
    }

    @Test
    fun searchBarAppearsOnlyForLargeDeviceListsOrPaginatedResults() {
        val fiveDevices = (1..5).map { device(it.toString(), "Device $it") }
        val sixDevices = (1..6).map { device(it.toString(), "Device $it") }

        assertFalse(IgngDevicesUiLogic.shouldShowSearchBar(fiveDevices, hasMore = false))
        assertTrue(IgngDevicesUiLogic.shouldShowSearchBar(fiveDevices, hasMore = true))
        assertTrue(IgngDevicesUiLogic.shouldShowSearchBar(sixDevices, hasMore = false))
    }
}

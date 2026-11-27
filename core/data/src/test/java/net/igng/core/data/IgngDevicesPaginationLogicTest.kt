package net.igng.core.data

import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.model.CorePaginatedResponseDto
import net.igng.core.model.CorePaginationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngDevicesPaginationLogicTest {

    private fun sampleDevice(
        id: String,
        name: String = "Device $id",
        isCurrent: Boolean = false,
    ): CoreDeviceSessionDto = CoreDeviceSessionDto(
        id = id,
        sessionId = "sess_$id",
        deviceName = name,
        clientLabel = "IGNG Android",
        ipAddress = "198.51.100.12",
        isCurrent = isCurrent,
    )

    @Test
    fun mergeNextPageDeduplicatesOverlappingDevicesAndAdvancesPagination() {
        val page1 = CorePaginatedResponseDto(
            data = listOf(
                sampleDevice("dev_1", "Phone A", isCurrent = true),
                sampleDevice("dev_2", "Tablet B"),
            ),
            pagination = CorePaginationDto(page = 1, pageSize = 2, total = 3, totalPages = 2),
        )
        val page2 = CorePaginatedResponseDto(
            data = listOf(
                sampleDevice("dev_2", "Tablet B"),
                sampleDevice("dev_3", "Desktop C"),
            ),
            pagination = CorePaginationDto(page = 2, pageSize = 2, total = 3, totalPages = 2),
        )

        val merged = IgngDevicesPaginationLogic.mergeNextPage(page1, page2)

        assertEquals(listOf("dev_1", "dev_2", "dev_3"), merged.data.map { it.id })
        assertEquals(2, merged.pagination.page)
        assertEquals(3, merged.pagination.total)
        assertEquals(2, merged.pagination.totalPages)
        assertFalse(IgngDevicesPaginationLogic.hasMorePages(merged))
    }

    @Test
    fun emptyNextPageStopsHasMoreToPreventInfinitePaging() {
        val page1 = CorePaginatedResponseDto(
            data = listOf(sampleDevice("dev_1", isCurrent = true)),
            pagination = CorePaginationDto(page = 1, pageSize = 30, total = 5, totalPages = 2),
        )
        val emptyPage2 = CorePaginatedResponseDto<CoreDeviceSessionDto>(
            data = emptyList(),
            pagination = CorePaginationDto(page = 2, pageSize = 30, total = 5, totalPages = 2),
        )

        assertTrue(IgngDevicesPaginationLogic.hasMorePages(page1))
        val merged = IgngDevicesPaginationLogic.mergeNextPage(page1, emptyPage2)
        assertEquals(1, merged.data.size)
        assertEquals(1, merged.pagination.total)
        assertFalse(IgngDevicesPaginationLogic.hasMorePages(merged))
    }

    @Test
    fun applyRenamedDeviceUpdatesMatchingEntryInPlace() {
        val initial = CorePaginatedResponseDto(
            data = listOf(
                sampleDevice("dev_1", "Old Name", isCurrent = true),
                sampleDevice("dev_2", "Other Device"),
            ),
            pagination = CorePaginationDto(page = 1, pageSize = 30, total = 2, totalPages = 1),
        )

        val updated = IgngDevicesPaginationLogic.applyRenamedDevice(
            current = initial,
            deviceId = "dev_1",
            updated = sampleDevice("dev_1", "主力手机", isCurrent = true),
        )

        assertEquals("主力手机", updated.data[0].deviceName)
        assertEquals("Other Device", updated.data[1].deviceName)
        assertEquals(2, updated.pagination.total)
    }

    @Test
    fun applyRevokedDeviceRemovesEntryAndDecrementsTotal() {
        val initial = CorePaginatedResponseDto(
            data = listOf(
                sampleDevice("dev_1", "Current", isCurrent = true),
                sampleDevice("dev_2", "Old Laptop"),
                sampleDevice("dev_3", "Tablet"),
            ),
            pagination = CorePaginationDto(page = 1, pageSize = 30, total = 4, totalPages = 1),
        )

        val afterRevoke = IgngDevicesPaginationLogic.applyRevokedDevice(initial, "dev_2")

        assertEquals(listOf("dev_1", "dev_3"), afterRevoke.data.map { it.id })
        assertEquals(3, afterRevoke.pagination.total)
        assertEquals(3, IgngDevicesPaginationLogic.resolvedTotalCount(afterRevoke))
    }
}

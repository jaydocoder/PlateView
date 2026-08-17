package com.jaydocoder.plateview.server.imports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImportPublishLifecycleTest {
    @Test
    fun `已验证批次按首次发布处理`() {
        assertEquals(ImportPublishMode.INITIAL, prepareImportPublish("VALIDATED"))
    }

    @Test
    fun `已回滚批次按重新发布处理`() {
        assertEquals(ImportPublishMode.REPUBLISH, prepareImportPublish("ROLLED_BACK"))
    }

    @Test
    fun `已发布批次不能再次发布`() {
        val exception = assertFailsWith<ImportWorkflowConflictException> {
            prepareImportPublish("PUBLISHED")
        }

        assertEquals("IMPORT_BATCH_NOT_PUBLISHABLE", exception.errorCode)
    }

    @Test
    fun `存在待确认差异时不能发布`() {
        val exception = assertFailsWith<ImportWorkflowConflictException> {
            ensureImportReadyToPublish(1)
        }

        assertEquals("IMPORT_PENDING_REVIEW", exception.errorCode)
    }

    @Test
    fun `所有差异已处置后允许发布`() {
        ensureImportReadyToPublish(0)
    }

    @Test
    fun `多条系统失效差异使用不同的来源唯一键`() {
        val identities = List(2, ::systemDiffSourceIdentity)

        assertEquals(2, identities.distinct().size)
        assertEquals("系统差异检测", identities[0].sheetName)
        assertEquals(0, identities[0].rowNumber)
        assertEquals(listOf(0, 1), identities.map(ImportSourceIdentity::itemIndex))
    }
}

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
}

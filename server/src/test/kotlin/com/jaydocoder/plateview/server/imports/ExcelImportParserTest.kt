package com.jaydocoder.plateview.server.imports

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class ExcelImportParserTest {
    private val parser = ExcelImportParser()

    @Test
    fun `村民工作表解析车牌告警与异常行`() {
        val rows = parser.parse(
            workbookBytes { workbook ->
                val sheet = workbook.createSheet("Sheet1")
                sheet.createRow(0).createCell(0).setCellValue("测试标题")
                writeRow(sheet, 2, "序号", "姓名", "身份证号", "车牌号", "联系方式", "备注")
                writeRow(sheet, 3, "1", "测试甲", "12345678901234567X", "新A 12345", "13800000000", "")
                writeRow(sheet, 4, "2", "测试乙", "123", "新A12346，新A12347", "无效电话", "")
                writeRow(sheet, 5, "3", "测试丙", "12345678901234567X", "无效车牌", "", "")
            },
        )

        assertEquals(4, rows.size)
        assertEquals(ImportCategory.RESIDENT, rows[0].vehicle.category)
        assertEquals("新A12345", rows[0].vehicle.normalizedPlate)
        assertEquals(ImportResolution.PUBLISH, rows[0].resolution)
        assertEquals(ImportResolution.PENDING, rows[1].resolution)
        assertEquals(ImportResolution.PENDING, rows[2].resolution)
        assertTrue(rows[1].warningMessage.orEmpty().contains("身份证号格式不规范"))
        assertTrue(rows[1].warningMessage.orEmpty().contains("拆分为2个车牌"))
        assertEquals(ImportResultStatus.ERROR, rows[3].resultStatus)
        assertTrue(rows[3].errorMessage.orEmpty().contains("车牌号格式不规范"))
    }

    @Test
    fun `警车和应急车后缀作为完整车牌解析`() {
        val rows = parser.parse(
            workbookBytes { workbook ->
                val sheet = workbook.createSheet("村民车辆")
                writeRow(sheet, 0, "姓名", "身份证号", "车牌号")
                writeRow(sheet, 1, "测试甲", "12345678901234567X", "新H0123警；新X4567应急")
            },
        )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.resultStatus == ImportResultStatus.VALID })
        assertEquals(listOf("新H0123警", "新X4567应急"), rows.map { it.vehicle.normalizedPlate })
        assertEquals(ImportResolution.PENDING, rows[0].resolution)
        assertTrue(rows[0].warningMessage.orEmpty().contains("拆分为2个车牌"))
    }

    @Test
    fun `村民车辆缺少姓名和身份证号但车牌有效时正常解析`() {
        val rows = parser.parse(
            workbookBytes { workbook ->
                val sheet = workbook.createSheet("村民车辆")
                writeRow(sheet, 0, "姓名", "身份证号", "车牌号")
                writeRow(sheet, 1, "", "", "新A12345")
            },
        )

        val row = assertNotNull(rows.singleOrNull())
        assertEquals(ImportResultStatus.VALID, row.resultStatus)
        assertEquals(ImportResolution.PUBLISH, row.resolution)
        assertEquals("新A12345", row.vehicle.normalizedPlate)
    }

    @Test
    fun `长期车辆工作表继承单位并拆分多车牌`() {
        val rows = parser.parse(
            workbookBytes { workbook ->
                val sheet = workbook.createSheet("驻景区单位")
                sheet.createRow(0).createCell(0).setCellValue("测试标题")
                writeRow(sheet, 1, "单位名称", "序号", "车牌号", "车辆类型", "通行人员", "车辆用途", "通行区域", "备注")
                writeRow(sheet, 2, "测试单位", "1", "新B12345；新B12346", "测试车辆", "测试人员", "工作", "区域", "")
                writeRow(sheet, 3, "", "2", "新B12347", "测试车辆", "测试人员", "工作", "区域", "")
            },
        )

        assertEquals(3, rows.size)
        assertTrue(rows.all { it.vehicle.category == ImportCategory.SCENIC_UNIT })
        assertTrue(rows.all { it.vehicle.organizationName == "测试单位" })
        assertEquals(ImportResolution.PENDING, rows[0].resolution)
        assertEquals(ImportResolution.PENDING, rows[1].resolution)
        assertEquals(ImportResolution.PUBLISH, rows[2].resolution)
        assertEquals("工作", rows[2].vehicle.attributes["vehicleUse"]?.toString()?.trim('"'))
    }

    @Test
    fun `首条长期车辆缺少单位时标记异常`() {
        val rows = parser.parse(
            workbookBytes { workbook ->
                val sheet = workbook.createSheet("干部车辆")
                sheet.createRow(0).createCell(0).setCellValue("测试标题")
                writeRow(sheet, 1, "单位名称", "车牌号", "车辆类型", "通行人员", "职务")
                writeRow(sheet, 2, "", "新C12345", "测试车辆", "测试人员", "测试职务")
            },
        )

        val row = assertNotNull(rows.singleOrNull())
        assertEquals(ImportResultStatus.ERROR, row.resultStatus)
        assertTrue(row.errorMessage.orEmpty().contains("单位名称为空"))
    }

    @Test
    fun `超长通行人员可正常解析并完整保留`() {
        val rows = parser.parse(
            workbookBytes { workbook ->
                val sheet = workbook.createSheet("驻景区企业")
                sheet.createRow(0).createCell(0).setCellValue("测试标题")
                writeRow(sheet, 1, "单位名称", "车牌号", "车辆类型", "通行人员")
                writeRow(sheet, 2, "测试单位", "新D12345", "测试车辆", "甲".repeat(256))
            },
        )

        val row = assertNotNull(rows.singleOrNull())
        assertEquals(ImportResultStatus.VALID, row.resultStatus)
        assertEquals("甲".repeat(256), row.vehicle.passHolder)
    }

    @Test
    fun `无效工作簿返回可处理的导入文件错误`() {
        assertFailsWith<ImportFileInvalidException> {
            parser.parse(byteArrayOf(1, 2, 3, 4))
        }
    }

    private fun workbookBytes(block: (XSSFWorkbook) -> Unit): ByteArray = XSSFWorkbook().use { workbook ->
        block(workbook)
        ByteArrayOutputStream().use { output ->
            workbook.write(output)
            output.toByteArray()
        }
    }

    private fun writeRow(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int, vararg values: String) {
        sheet.createRow(rowIndex).also { row ->
            values.forEachIndexed { index, value -> row.createCell(index).setCellValue(value) }
        }
    }
}

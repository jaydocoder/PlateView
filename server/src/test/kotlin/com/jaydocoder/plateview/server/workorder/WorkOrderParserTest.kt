package com.jaydocoder.plateview.server.workorder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkOrderParserTest {
    @Test
    fun `标准车单保留前导零车牌原文和完整身份证`() {
        val parsed = WorkOrderParser.parse(
            """
            【车号】新 H27274(轻型多用途货车)
            【人数】2人，
            张卫华65432119760417201X
            刘忠玉652622196203082511
            【时间】9.20-9.23
            【地点】禾木
            【方式】核实免门票
            【单号】0919011
            【事由】兹有2人为我公司调试设备。
            【备注】早八晚九
            """.trimIndent(),
        )

        assertEquals("0919011", parsed.orderNumber)
        assertEquals("新H27274", parsed.rawPlate)
        assertEquals("新H27274", parsed.normalizedPlate)
        assertEquals("轻型多用途货车", parsed.vehicleType)
        assertEquals("张卫华", parsed.people.first().name)
        assertEquals("65432119760417201X", parsed.people.first().identityNumber)
        assertEquals("COMPLETE", parsed.parseQuality)
    }

    @Test
    fun `人员格式不规范时原文兜底且不生成虚假身份证`() {
        val parsed = WorkOrderParser.parse("【单号】0012\n【人数】人员信息待现场核对\n【备注】测试")

        assertEquals("人员信息待现场核对", parsed.people.single().rawLine)
        assertNull(parsed.people.single().identityNumber)
        assertEquals("PARTIAL", parsed.parseQuality)
    }

    @Test
    fun `只有明确作废且识别出单号才标记作废`() {
        assertEquals("VOID", WorkOrderParser.parse("【单号】0919011\n该单号作废").status)
        assertEquals("ACTIVE", WorkOrderParser.parse("这条通知已经作废但没有单号").status)
        assertEquals("ACTIVE", WorkOrderParser.parse("【单号】0919011\n取消原行程").status)
    }

    @Test
    fun `无括号车型不会混入结构化车牌`() {
        val parsed = WorkOrderParser.parse("【车号】新A080R6四驱皮卡\n【单号】0913009")

        assertEquals("新A080R6", parsed.rawPlate)
        assertEquals("新A080R6", parsed.normalizedPlate)
    }

    @Test
    fun `连续多个车牌会分别提取并去重`() {
        val parsed = WorkOrderParser.parse("【车号】新H B8V00新HE6Q96新HC5J11、新HB8V00\n【单号】0913001")

        assertEquals("新HB8V00、新HE6Q96、新HC5J11", parsed.rawPlate)
        assertEquals(listOf("新HB8V00", "新HE6Q96", "新HC5J11"), WorkOrderParser.extractPlateNumbers(parsed.rawPlate.orEmpty()))
    }

    @Test
    fun `全角字符和车型尾巴不会污染结构化车牌`() {
        val parsed = WorkOrderParser.parse("【车号】新Ｈ－６８３２０新Ｃ３５８６２轻型多用途车\n【单号】0913002")

        assertEquals("新H68320、新C35862", parsed.rawPlate)
        assertEquals("新H68320新C35862", parsed.normalizedPlate)
    }

    @Test
    fun `多车型车单按原始顺序生成车辆明细`() {
        val parsed = WorkOrderParser.parse(
            """
            【车号】1、工程保障车辆1 车牌号:新H9078E;
            2、工程保障车辆2:车牌号:新AK8F44;
            3、工程保障车辆3:车牌号:新H8931B;
            4、重型半挂牵引车:车牌号:新H30765
            【人数】6人
            【地点】喀纳斯
            【时间】9.20-9.26
            【单号】0920028
            【备注】轻型早八晚九，重型早八晚十二
            """.trimIndent(),
        )

        assertEquals(listOf("新H9078E", "新AK8F44", "新H8931B", "新H30765"), parsed.vehicles.map { it.rawPlate })
        assertTrue(parsed.vehicles.last().vehicleType.orEmpty().contains("重型半挂牵引车"))
    }

    @Test
    fun `简短单号消息归类为附件车单`() {
        val parsed = WorkOrderParser.parse("0920019核实免门票")

        assertEquals("0920019", parsed.orderNumber)
        assertEquals("ATTACHMENT_WORK_ORDER", WorkOrderParser.classify(parsed, "0920019核实免门票", false))
    }

    @Test
    fun `普通文本消息归类为聊天记录`() {
        val parsed = WorkOrderParser.parse("今天下班后请关闭设备")

        assertEquals("GENERAL_MESSAGE", WorkOrderParser.classify(parsed, "今天下班后请关闭设备", false))
        assertEquals("PASSAGE_MESSAGE", WorkOrderParser.classify(WorkOrderParser.parse("新AFP3867，贾登峪车道口予以通行"), "新AFP3867，贾登峪车道口予以通行", true))
    }
}

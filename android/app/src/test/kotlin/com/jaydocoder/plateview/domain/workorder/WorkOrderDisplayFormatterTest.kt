package com.jaydocoder.plateview.domain.workorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WorkOrderDisplayFormatterTest {
    @Test
    fun `聊天发送者忽略空昵称并回退到原始昵称或稳定账号`() {
        val message = sampleMessage("测试", "2026-09-21T01:00:00Z")

        assertEquals("三叔", message.copy(displayName = "", senderGroupNickname = "", senderDisplay = "三叔").resolvedSenderName())
        assertEquals(
            "wxid_test",
            message.copy(displayName = "未知发送者", senderGroupNickname = "", senderDisplay = "", senderUsername = "wxid_test").resolvedSenderName(),
        )
    }

    @Test
    fun `纯图片和PDF占位正文由附件缩略图替代`() {
        val image = WorkOrderAttachment(1, "IMAGE", null, null, "image/jpeg", 100, true, true, "AVAILABLE", null)
        val pdf = WorkOrderAttachment(2, "PDF", "车辆申请.pdf", null, "application/pdf", 200, true, true, "AVAILABLE", 2)
        val message = sampleMessage("测试", "2026-09-21T01:00:00Z")

        assertTrue(message.copy(rawContent = "[图片] local_id=1188", attachments = listOf(image)).hasAttachmentPlaceholderContent())
        assertTrue(message.copy(rawContent = "[文件] 车辆申请.pdf (612.8 KB, pdf)", attachments = listOf(pdf)).hasAttachmentPlaceholderContent())
        assertFalse(message.copy(rawContent = "[图片] local_id=1188\n请核实", attachments = listOf(image)).hasAttachmentPlaceholderContent())
        assertFalse(message.copy(rawContent = "[图片] local_id=1188", attachments = emptyList()).hasAttachmentPlaceholderContent())
    }

    @Test
    fun `非通行时段使用适合窄屏单行展示的文案`() {
        assertEquals("不在通行时间", WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS.displayLabel())
    }

    @Test
    fun `无括号车型不会混入车牌显示`() {
        assertEquals(listOf("新A080R6"), extractWorkOrderPlateNumbers("新A080R6四驱皮卡"))
    }

    @Test
    fun `连续多个车牌会拆分并去重`() {
        assertEquals(
            listOf("新HB8V00", "新HE6Q96", "新HC5J11"),
            extractWorkOrderPlateNumbers("新H B8V00新HE6Q96新HC5J11、新HB8V00"),
        )
    }

    @Test
    fun `多个车牌后的车型文字不会混入车牌`() {
        assertEquals(
            listOf("新H68320", "新C35862"),
            extractWorkOrderPlateNumbers("新H-68320新C35862轻型多用途车"),
        )
    }

    @Test
    fun `全角车牌字符会归一化后提取`() {
        assertEquals(
            listOf("新H68320", "新C35862"),
            extractWorkOrderPlateNumbers("新Ｈ－６８３２０新Ｃ３５８６２轻型多用途车"),
        )
    }

    @Test
    fun `结构化字段不完整时从微信原文补齐全部车牌`() {
        assertEquals(
            listOf("新H68320", "新C35862"),
            extractWorkOrderPlateNumbers(
                rawPlate = "新H68320",
                rawContent = "【车号】新H.68320、新C.35862 轻型多用途货车、轻型自卸货车",
            ),
        )
    }

    @Test
    fun `按单号搜索时候选只选择第一个车牌`() {
        assertEquals(
            "新AJW763",
            selectWorkOrderCandidatePlate("新AJW763、新ATY872", "0828011", "0828"),
        )
    }

    @Test
    fun `按部分车牌搜索时选择实际匹配的车牌`() {
        assertEquals(
            "新ATY872",
            selectWorkOrderCandidatePlate("新AJW763、新ATY872", "0828011", "TY872"),
        )
    }

    @Test
    fun `带空格和中点的车牌查询仍能匹配`() {
        assertEquals(
            "新ATY872",
            selectWorkOrderCandidatePlate("新AJW763、新ATY872", "0828011", "新 A·TY872"),
        )
    }

    @Test
    fun `查询未命中车牌时回退第一个车牌`() {
        assertEquals(
            "新AJW763",
            selectWorkOrderCandidatePlate("新AJW763、新ATY872", "0828011", "禾木"),
        )
        assertEquals(null, selectWorkOrderCandidatePlate(null, "0828011", "0828"))
    }

    @Test
    fun `按单号进入详情时顶部显示全部车牌`() {
        assertEquals(
            listOf("新H68320", "新C35862"),
            selectWorkOrderDetailHeaderPlates(
                rawPlate = "新H68320",
                rawContent = "【车号】新H68320、新C35862",
                orderNumber = "0913006",
                query = "0913",
            ),
        )
    }

    @Test
    fun `按车牌进入详情时顶部只显示命中车牌`() {
        assertEquals(
            listOf("新C35862"),
            selectWorkOrderDetailHeaderPlates(
                rawPlate = "新H68320",
                rawContent = "【车号】新H68320、新C35862",
                orderNumber = "0913006",
                query = "C35862",
            ),
        )
    }

    @Test
    fun `备注会提取全部通行时间并排除其他限制`() {
        assertEquals(
            "早八晚九，中午两点到四点",
            extractWorkOrderPassageTimeRemark("早八晚九，中午两点到四点，车辆不得停靠三湾"),
        )
    }

    @Test
    fun `数字时刻和上午下午时段可以作为通行时间摘要`() {
        assertEquals(
            "上午10:30-下午4:00，夜间禁止通行",
            extractWorkOrderPassageTimeRemark("上午10:30-下午4:00；夜间禁止通行；服从现场管理"),
        )
        assertEquals(null, extractWorkOrderPassageTimeRemark("车辆不得停靠三湾"))
    }

    @Test
    fun `单日通行日期在次日判定为过期`() {
        assertEquals(
            WorkOrderPassageValidity.EXPIRED,
            evaluateWorkOrderPassageValidity("9.19", null, "2026-09-19T01:00:00Z", beijingTime(2026, 9, 20, 8, 0)),
        )
    }

    @Test
    fun `日期范围在结束日期前保持有效`() {
        assertEquals(
            WorkOrderPassageValidity.VALID,
            evaluateWorkOrderPassageValidity("9.20-9.23", null, "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 8, 0)),
        )
    }

    @Test
    fun `早八晚九表示早八点前或晚九点后允许通行`() {
        assertEquals(
            WorkOrderPassageValidity.VALID,
            evaluateWorkOrderPassageValidity("9.20-9.21", "早八晚九", "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 7, 59)),
        )
        assertEquals(
            WorkOrderPassageValidity.OUTSIDE_ALLOWED_HOURS,
            evaluateWorkOrderPassageValidity("9.20-9.21", "早八晚九", "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 12, 0)),
        )
        assertEquals(
            WorkOrderPassageValidity.VALID,
            evaluateWorkOrderPassageValidity("9.20-9.21", "早八晚九", "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 21, 1)),
        )
    }

    @Test
    fun `中午两点到四点按下午四点判断结束时间`() {
        assertEquals(
            WorkOrderPassageValidity.VALID,
            evaluateWorkOrderPassageValidity("9.20", "中午两点到四点", "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 15, 59)),
        )
        assertEquals(
            WorkOrderPassageValidity.EXPIRED,
            evaluateWorkOrderPassageValidity("9.20", "中午两点到四点", "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 16, 1)),
        )
    }

    @Test
    fun `多个备注时段命中任意一个即可通行`() {
        val remarks = "早八晚九，中午两点到四点，车辆不得停靠三湾"
        assertEquals(
            WorkOrderPassageValidity.VALID,
            evaluateWorkOrderPassageValidity("9.20-9.21", remarks, "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 14, 30)),
        )
        assertEquals(
            WorkOrderPassageValidity.OUTSIDE_ALLOWED_HOURS,
            evaluateWorkOrderPassageValidity("9.20-9.21", remarks, "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 18, 0)),
        )
    }

    @Test
    fun `多车型车单按首页展示车牌选择对应时段`() {
        val workOrder = sampleWorkOrder(status = "ACTIVE", location = "喀纳斯").copy(
            orderNumber = "0920028",
            rawPlate = "新H9078E、新AK8F44、新H8931B、新H30765",
            rawValidTime = "9.20-9.26",
            remarks = "轻型早八晚九，重型早八晚十二，不得停靠三湾",
            vehicles = listOf(
                WorkOrderVehicle("工程保障车辆1 新H9078E", "新H9078E", "新H9078E", "工程保障车辆1"),
                WorkOrderVehicle("重型半挂牵引车 新H30765", "新H30765", "新H30765", "重型半挂牵引车"),
            ),
        )

        assertEquals(
            WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS,
            resolveWorkOrderPassageState(workOrder, beijingTime(2026, 9, 21, 15, 28), "新H9078E"),
        )
        assertEquals(
            WorkOrderPassageState.VALID,
            resolveWorkOrderPassageState(workOrder, beijingTime(2026, 9, 21, 21, 1), "新H9078E"),
        )
        assertEquals(
            WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS,
            resolveWorkOrderPassageState(workOrder, beijingTime(2026, 9, 21, 15, 28), "新H30765"),
        )
    }

    @Test
    fun `没有备注时间时日期范围内全天允许通行`() {
        assertEquals(
            WorkOrderPassageValidity.VALID,
            evaluateWorkOrderPassageValidity("9.20-9.26", "车辆不得停靠三湾", "2026-09-20T01:00:00Z", beijingTime(2026, 9, 23, 12, 0)),
        )
    }

    @Test
    fun `数字时段取结束时间且跨年范围使用下一年`() {
        assertEquals(
            WorkOrderPassageValidity.EXPIRED,
            evaluateWorkOrderPassageValidity("12.31-1.2", "上午10:30-下午4:00", "2026-12-31T01:00:00Z", beijingTime(2027, 1, 2, 16, 1)),
        )
        assertEquals(
            WorkOrderPassageValidity.VALID,
            evaluateWorkOrderPassageValidity("12.31-1.2", "上午10:30-下午4:00", "2026-12-31T01:00:00Z", beijingTime(2027, 1, 2, 15, 59)),
        )
    }

    @Test
    fun `无法识别日期时要求人工核实且作废状态优先`() {
        assertEquals(
            WorkOrderPassageValidity.UNKNOWN,
            evaluateWorkOrderPassageValidity("待通知", "早八晚九", "2026-09-20T01:00:00Z", beijingTime(2026, 9, 20, 8, 0)),
        )
        assertEquals(
            WorkOrderPassageState.VOID,
            resolveWorkOrderPassageState(sampleWorkOrder(status = "VOID"), beijingTime(2026, 9, 20, 8, 0)),
        )
    }

    @Test
    fun `通行区域不符优先于有效日期`() {
        assertEquals(
            WorkOrderPassageState.AREA_MISMATCH,
            resolveWorkOrderPassageState(sampleWorkOrder(status = "ACTIVE", location = "禾木"), beijingTime(2026, 9, 20, 7, 0)),
        )
        assertEquals(
            WorkOrderPassageState.VALID,
            resolveWorkOrderPassageState(sampleWorkOrder(status = "ACTIVE", location = "喀纳斯"), beijingTime(2026, 9, 20, 7, 0)),
        )
        assertEquals(
            WorkOrderPassageState.VALID,
            resolveWorkOrderPassageState(sampleWorkOrder(status = "ACTIVE", location = null), beijingTime(2026, 9, 20, 7, 0)),
        )
    }

    @Test
    fun `白名单放行消息按贾登峪和相对日期判定`() {
        val sentAt = "2026-09-20T03:00:00Z"
        assertEquals(
            WorkOrderPassageState.AREA_MISMATCH,
            resolveWechatMessagePassageState(sampleMessage("新AFP3867，禾木敖包车道口予以通行", sentAt), beijingTime(2026, 9, 20, 12, 0)),
        )
        assertEquals(
            WorkOrderPassageState.VALID,
            resolveWechatMessagePassageState(sampleMessage("新AS50B2，明天贾登峪车道口予以通行", sentAt), beijingTime(2026, 9, 21, 12, 0)),
        )
        assertEquals(
            WorkOrderPassageState.EXPIRED,
            resolveWechatMessagePassageState(sampleMessage("新AS50B2，贾登峪车道口予以通行", sentAt), beijingTime(2026, 9, 21, 0, 1)),
        )
    }

    @Test
    fun `姓名行和下一条身份证行会合并并删除重复身份证`() {
        val people = listOf(
            WorkOrderPerson("1. 孙佐伟", null, null),
            WorkOrderPerson("371325199109204218\n371325199109204218", null, "371325199109204218"),
            WorkOrderPerson("2. 秦文琦370827200510040015", "2. 秦文琦", "370827200510040015"),
        )

        assertEquals(
            listOf("1. 孙佐伟  371325199109204218", "2. 秦文琦  370827200510040015"),
            formatWorkOrderPeople(people),
        )
    }

    private fun beijingTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Shanghai"))

    private fun sampleWorkOrder(status: String, location: String? = null) = WorkOrder(
        id = 1,
        orderNumber = "0920014",
        rawPlate = "新A54X27",
        normalizedPlate = "新A54X27",
        vehicleType = null,
        declaredPeople = null,
        rawValidTime = "9.20",
        location = location,
        verificationMethod = null,
        reason = null,
        remarks = "早八晚九",
        status = status,
        parseQuality = "COMPLETE",
        catalogRevision = 1,
        rawContent = "【单号】0920014",
        sentAt = "2026-09-20T01:00:00Z",
        sourceKey = "test",
        sourceName = "测试群",
        senderUsername = null,
        senderDisplay = null,
        senderGroupNickname = null,
        people = emptyList(),
        images = emptyList(),
        vehicles = emptyList(),
    )

    private fun sampleMessage(content: String, sentAt: String) = WechatMessage(
        id = 1,
        businessType = "PASSAGE_MESSAGE",
        rawContent = content,
        matchedSnippet = content,
        sentAt = sentAt,
        sourceKey = "test",
        sourceName = "测试群",
        senderUsername = "wxid_test",
        senderDisplay = "孙阿鑫",
        senderGroupNickname = null,
        displayName = "孙主任",
        plateNumbers = listOf("新AFP3867"),
        attachments = emptyList(),
    )
}

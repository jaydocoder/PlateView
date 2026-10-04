import { describe, expect, it } from "vitest";
import { adaptImportBatch, adaptImportDetail, adaptImportList, adaptImportRow, canPublish, canResolve, confirmLabel, detailLabel, importStatus, rowAction, skipLabel } from "./imports";

const raw = { id: 7, status: "VALIDATED", sourceFileName: "车辆.xlsx", stats: { pendingReviewRows: 0, publishableRows: 1 }, rowTotal: 1, rows: [] };
const row = adaptImportRow({ id: 3, plannedAction: "UPDATE", resolution: "PENDING", resultStatus: "VALID" });
describe("导入中心协议和核对规则", () => {
  it("空值和内部字段不进入显示模型", () => {
    expect(adaptImportList({ items: [{ id: 7, sourceFileName: null, createdAt: null, secret: "内部" }] })).toEqual([{ id: 7, sourceFileName: "未命名 Excel", createdAt: "", status: "", totalRows: 0, errorRows: 0 }]);
  });
  it("缺少标识或列表结构时明确失败", () => {
    expect(() => adaptImportList(null)).toThrow("格式异常");
    expect(() => adaptImportRow({ id: 0 })).toThrow("有效标识");
    expect(() => adaptImportBatch({ ...raw, rows: null })).toThrow("格式异常");
  });
  it("批次统计来自统计对象且缺失确认数不能发布", () => {
    const batch = adaptImportBatch({ ...raw, totalRows: 999, stats: { totalRows: 20, publishableRows: 3 } });
    expect(batch.totalRows).toBe(20);
    expect(batch.stats.pendingReviewRows).toBe(-1);
    expect(canPublish(batch)).toBe(false);
  });
  it.each(["VALIDATED", "ROLLED_BACK"])("%s 批次已核对后可发布", status => {
    expect(canPublish(adaptImportBatch({ ...raw, status }))).toBe(true);
  });
  it.each(["PUBLISHED", "FAILED", "UNKNOWN"])("%s 批次不能发布", status => {
    expect(canPublish(adaptImportBatch({ ...raw, status }))).toBe(false);
  });
  it("有未确认记录或无可发布变更时不能发布", () => {
    expect(canPublish(adaptImportBatch({ ...raw, stats: { pendingReviewRows: 1, publishableRows: 2 } }))).toBe(false);
    expect(canPublish(adaptImportBatch({ ...raw, stats: { pendingReviewRows: 0, publishableRows: 0 } }))).toBe(false);
  });
  it("仅待发布的有效待确认变更可处理", () => {
    const batch = adaptImportBatch(raw);
    expect(canResolve(batch, row)).toBe(true);
    for (const resolution of ["PUBLISH", "SKIP", "ERROR"]) expect(canResolve(batch, { ...row, resolution })).toBe(false);
    expect(canResolve(batch, { ...row, resultStatus: "ERROR" })).toBe(false);
    expect(canResolve(batch, { ...row, plannedAction: "NONE" })).toBe(false);
    expect(canResolve({ ...batch, status: "PUBLISHED" }, row)).toBe(false);
  });
  it.each([
    ["CREATE", "新增档案", "查看新增详情", "确认新增", "跳过"],
    ["UPDATE", "字段更新", "查看更新详情", "确认更新", "跳过"],
    ["REACTIVATE", "恢复有效", "查看更新详情", "确认恢复", "保持失效"],
    ["DEACTIVATE", "待失效", "查看失效详情", "确认失效", "保留有效"],
  ])("%s 使用与安卓一致的处置标签", (plannedAction, action, detail, confirm, skip) => {
    const value = { ...row, plannedAction };
    expect([rowAction(value), detailLabel(value), confirmLabel(value), skipLabel(value)]).toEqual([action, detail, confirm, skip]);
  });
  it("原新值空值与源字段可适配", () => {
    const detail = adaptImportDetail({ row, sections: [{ title: "档案", fields: [{ label: "备注", before: null, after: "核对完成" }] }], sourceValues: [{ label: "备注", value: "核对完成" }] });
    expect(detail.sections[0].fields[0]).toEqual({ label: "备注", before: "", after: "核对完成" });
    expect(detail.sourceValues[0].value).toBe("核对完成");
    expect(() => adaptImportDetail({ row })).toThrow("格式异常");
  });
  it("状态及异常行均显示中文", () => {
    expect(["VALIDATED", "PUBLISHED", "ROLLED_BACK"].map(importStatus)).toEqual(["待发布", "已发布", "已撤销"]);
    expect(rowAction({ ...row, plannedAction: "NONE", resultStatus: "ERROR" })).toBe("解析异常");
  });
});

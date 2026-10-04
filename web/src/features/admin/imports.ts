type RecordValue = Record<string, unknown>;
const object = (value: unknown): RecordValue => value !== null && typeof value === "object" && !Array.isArray(value) ? value as RecordValue : {};
const text = (value: unknown) => typeof value === "string" ? value : "";
const count = (value: unknown, fallback = 0) => typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : fallback;
const id = (value: unknown) => { const result = count(value); if (!result) throw new Error("导入响应缺少有效标识，请刷新后重试"); return result; };
export const importFilters = [["REVIEW", "全部待核对"], ["CREATE", "新增"], ["UPDATE", "更新"], ["REACTIVATE", "恢复"], ["DEACTIVATE", "待失效"], ["ERROR", "异常"]] as const;
export type ImportFilter = typeof importFilters[number][0];
export type ImportSummary = { id: number; sourceFileName: string; status: string; totalRows: number; errorRows: number; createdAt: string };
export type ImportRow = { id: number; plateNumber: string; sourceSheetName: string; sourceRowNumber: number; primarySubject: string; plannedAction: string; resultStatus: string; resolution: string; warningMessage: string; errorMessage: string };
export type ImportStats = { totalRows: number; newRows: number; updateRows: number; reactivateRows: number; deactivateRows: number; duplicateRows: number; errorRows: number; pendingReviewRows: number; publishableRows: number };
export type ImportBatch = ImportSummary & { stats: ImportStats; rowTotal: number; rows: ImportRow[] };
export type ImportDetail = { row: ImportRow; sections: Array<{ title: string; fields: Array<{ label: string; before: string; after: string }> }>; sourceValues: Array<{ label: string; value: string }> };

export function adaptImportSummary(value: unknown): ImportSummary {
  const data = object(value);
  return { id: id(data.id), sourceFileName: text(data.sourceFileName) || "未命名 Excel", status: text(data.status), totalRows: count(data.totalRows), errorRows: count(data.errorRows), createdAt: text(data.createdAt) };
}
export function adaptImportList(value: unknown): ImportSummary[] {
  const items = object(value).items;
  if (!Array.isArray(items)) throw new Error("导入批次列表格式异常，请重试");
  return items.map(adaptImportSummary);
}
export function adaptImportRow(value: unknown): ImportRow {
  const data = object(value);
  return { id: id(data.id), plateNumber: text(data.plateNumber), sourceSheetName: text(data.sourceSheetName), sourceRowNumber: count(data.sourceRowNumber), primarySubject: text(data.primarySubject), plannedAction: text(data.plannedAction), resultStatus: text(data.resultStatus), resolution: text(data.resolution), warningMessage: text(data.warningMessage), errorMessage: text(data.errorMessage) };
}
export function adaptImportBatch(value: unknown): ImportBatch {
  const data = object(value), raw = object(data.stats);
  const stats: ImportStats = { totalRows: count(raw.totalRows), newRows: count(raw.newRows), updateRows: count(raw.updateRows), reactivateRows: count(raw.reactivateRows), deactivateRows: count(raw.deactivateRows), duplicateRows: count(raw.duplicateRows), errorRows: count(raw.errorRows), pendingReviewRows: count(raw.pendingReviewRows, -1), publishableRows: count(raw.publishableRows) };
  if (!Array.isArray(data.rows)) throw new Error("导入核对记录格式异常，请重试");
  return { ...adaptImportSummary(data), totalRows: stats.totalRows, errorRows: stats.errorRows, stats, rowTotal: count(data.rowTotal), rows: data.rows.map(adaptImportRow) };
}
export function adaptImportDetail(value: unknown): ImportDetail {
  const data = object(value);
  if (!Array.isArray(data.sections) || !Array.isArray(data.sourceValues)) throw new Error("导入差异详情格式异常，请重试");
  return { row: adaptImportRow(data.row), sections: data.sections.map(value => { const section = object(value); return { title: text(section.title), fields: (Array.isArray(section.fields) ? section.fields : []).map(value => { const field = object(value); return { label: text(field.label), before: text(field.before), after: text(field.after) }; }) }; }), sourceValues: data.sourceValues.map(value => { const field = object(value); return { label: text(field.label), value: text(field.value) }; }) };
}
export function importStatus(status: string) { return ({ VALIDATED: "待发布", PUBLISHED: "已发布", ROLLED_BACK: "已撤销", FAILED: "失败" } as Record<string, string>)[status] || "状态未知"; }
export function rowAction(row: ImportRow) { return ({ CREATE: "新增档案", UPDATE: "字段更新", REACTIVATE: "恢复有效", DEACTIVATE: "待失效" } as Record<string, string>)[row.plannedAction] || (row.resultStatus === "ERROR" ? "解析异常" : "待核对"); }
export function detailLabel(row: ImportRow) { return ({ CREATE: "查看新增详情", UPDATE: "查看更新详情", REACTIVATE: "查看更新详情", DEACTIVATE: "查看失效详情" } as Record<string, string>)[row.plannedAction] || "查看问题详情"; }
export function confirmLabel(row: ImportRow) { return ({ CREATE: "确认新增", UPDATE: "确认更新", REACTIVATE: "确认恢复", DEACTIVATE: "确认失效" } as Record<string, string>)[row.plannedAction] || "确认"; }
export function skipLabel(row: ImportRow) { return row.plannedAction === "DEACTIVATE" ? "保留有效" : row.plannedAction === "REACTIVATE" ? "保持失效" : "跳过"; }
export function canResolve(batch: ImportBatch, row: ImportRow) { return batch.status === "VALIDATED" && row.resolution === "PENDING" && row.resultStatus !== "ERROR" && ["CREATE", "UPDATE", "REACTIVATE", "DEACTIVATE"].includes(row.plannedAction); }
export function canPublish(batch: ImportBatch) { return ["VALIDATED", "ROLLED_BACK"].includes(batch.status) && batch.stats.pendingReviewRows === 0 && batch.stats.publishableRows > 0; }

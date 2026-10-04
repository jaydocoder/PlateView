/** 将服务端不稳定的管理响应收敛为页面可消费的结构。 */
export type AdminItem = Record<string, unknown>;

export type AdminList = {
  items: AdminItem[];
  total?: number;
  summary?: Record<string, unknown>;
  [key: string]: unknown;
};

export type AttachmentInfo = {
  id?: number;
  fileName: string;
  kind: "IMAGE" | "PDF";
  status?: string;
  availability?: string;
};

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" ? value as Record<string, unknown> : {};
}

export function adaptAdminList(value: unknown): AdminList {
  const record = asRecord(value);
  const items = Array.isArray(record.items) ? record.items.map(asRecord) : [];
  return { ...record, items, total: typeof record.total === "number" ? record.total : undefined };
}

export function adaptAttachments(value: unknown): AttachmentInfo[] {
  if (!Array.isArray(value)) return [];
  return value.map((item, index) => {
    const record = asRecord(item);
    const kind = String(record.kind || record.attachmentKind || "IMAGE").toUpperCase() === "PDF" ? "PDF" : "IMAGE";
    return {
      id: typeof record.id === "number" ? record.id : undefined,
      fileName: String(record.fileName || record.name || `${kind === "PDF" ? "PDF" : "图片"}附件 ${index + 1}`),
      kind,
      status: typeof record.status === "string" ? record.status : undefined,
      availability: typeof record.availability === "string" ? record.availability : undefined,
    };
  });
}

export function errorMessage(value: unknown, fallback: string): string {
  return value instanceof Error && value.message ? value.message : fallback;
}

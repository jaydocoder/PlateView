export type CandidateAccess = { detailAccessible?: boolean };

/** 与服务端主管理员规则一致，单独的特权标识不能扩大模块可见范围。 */
export function canAccessPrimaryAdminModules(profile: { username: string; role: string }): boolean {
  return profile.role === "ADMIN" && profile.username === "admin";
}

/** 受限候选保持可见，但点击行为必须静默失效。 */
export function canOpenCandidate(candidate: CandidateAccess): boolean {
  return candidate.detailAccessible !== false;
}

/** 按当前搜索词选择聊天消息中的车牌；无匹配时回退第一辆车。 */
export function selectWechatMessageCandidatePlate(plateNumbers: string[] | undefined, query: string): string | undefined {
  if (!plateNumbers?.length) return undefined;
  const normalizedQuery = normalizePlateSearch(query);
  if (!normalizedQuery) return plateNumbers[0];
  return plateNumbers.find((plate) => normalizePlateSearch(plate).includes(normalizedQuery)) || plateNumbers[0];
}

export function selectWechatCandidatePlate(plateNumbers: string[] | undefined, query: string): string | undefined {
  return selectWechatMessageCandidatePlate(plateNumbers, query);
}

export function normalizePlateSearch(value: string): string {
  return value.toUpperCase().replace(/[\s·•．.\-]/g, "");
}

export function rebuildStateLabel(state: string | undefined): string {
  const labels: Record<string, string> = {
    PREVIEW: "待确认预览",
    BACKUP_VERIFYING: "备份校验中",
    LOCKED: "已暂停上传",
    CLEANING: "清理中",
    REBUILDING: "等待全量同步",
    VERIFYING: "验证中",
    COMPLETED: "已完成",
    FAILED: "失败",
    CANCELLED: "已取消",
  };
  return labels[state || ""] || "未开始";
}

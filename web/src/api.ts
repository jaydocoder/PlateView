import { notify, operationLabel } from "./state/feedback";

export type User = {
  id: number;
  username: string;
  role: string;
  isPrimaryAdministrator?: boolean;
  avatarVersion?: number;
  scheduleEnabled?: boolean;
  updatePolicy?: string;
  wechatWorkOrderAccessEnabled?: boolean;
};

export type Profile = User & {
  hasAvatar?: boolean;
  runtimePolicy?: { vehicleResultLimit: number; workOrderResultLimit: number; wechatMessageResultLimit: number };
  wechatSyncHealth?: { state: string; lastSuccessfulSyncAt: string | null; lastHeartbeatAt: string | null };
};

export type VehicleCandidate = {
  id: number;
  plateNumber: string;
  plateColor?: string;
  normalizedPlate?: string;
  category: string;
  status?: string;
  detailAccessible?: boolean;
  primarySubject?: string | null;
};

export type VehicleDetail = VehicleCandidate & Record<string, unknown>;
export type WorkOrder = Record<string, unknown> & { id: number; orderNumber?: string; orderYear?: number; plateNumbers?: string[]; title?: string; rawContent?: string; sentAt?: string; sourceName?: string };
export type WechatMessage = Record<string, unknown> & { id: number; sentAt?: string; sourceName?: string; rawContent?: string; plateNumbers?: string[]; businessType?: string };
export type ScheduleWeek = { weekStart: string; weekNumber: number; shifts: Array<{ date: string; shiftType: string; persons: Array<{ id: number; username: string; realName: string }> }> };
export type ScheduleMonth = { month: string; days: Array<{ date: string; hasShift: boolean }> };
export type StatisticsData = { range: string; summary: { totalQueries: number; distinctPlates: number; activeUsers: number }; trend: Array<{ bucket: string; queryCount: number }>; categories: Array<{ category: string; queryCount: number }>; topPlates: Array<{ plateNumber: string; queryCount: number }> };

type ApiError = Error & { code?: string; status?: number };

let accessToken: string | null = null;
let refreshInFlight: Promise<boolean> | null = null;

export function setAccessToken(token: string | null) { accessToken = token; }

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const label = operationLabel(path, (init.method || "GET").toUpperCase());
  try {
    const result = await timedRequest<T>(path, init, retry);
    if (label) {
      const value = result as { success?: boolean; status?: string; message?: string } | undefined;
      const failed = value?.success === false || value?.status === "FAILED";
      notify(failed ? "error" : "success", failed ? value?.message || `${label}未成功，请检查当前状态后重试` : `${label}成功`);
    }
    return result;
  } catch (error) {
    if (label && !init.signal?.aborted) notify("error", `${label}失败：${error instanceof Error ? error.message : "请检查网络后重试"}`);
    throw error;
  }
}

async function timedRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const method = (init.method || "GET").toUpperCase();
  const timeout = method === "GET" ? 15_000 : ["/auth/web-refresh", "/auth/web-login"].includes(path) ? 10_000 : 0;
  if (!timeout) return performRequest<T>(path, init, retry);
  const controller = new AbortController();
  let timedOut = false;
  const abort = () => controller.abort();
  if (init.signal?.aborted) abort();
  else init.signal?.addEventListener("abort", abort, { once: true });
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeout);
  try {
    return await performRequest<T>(path, { ...init, signal: controller.signal }, retry);
  } catch (error) {
    if (timedOut) throw Object.assign(new Error("请求超时，请检查网络后重试"), { code: "REQUEST_TIMEOUT" });
    throw error;
  } finally { clearTimeout(timer); init.signal?.removeEventListener("abort", abort); }
}

async function performRequest<T>(path: string, init: RequestInit, retry: boolean): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  const response = await fetch(path, { ...init, headers, credentials: "include" });
  if (response.status === 401 && retry && !path.startsWith("/auth/")) {
    if (!refreshInFlight) refreshInFlight = refreshWebSession().finally(() => { refreshInFlight = null; });
    if (await refreshInFlight) return performRequest<T>(path, init, false);
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string; code?: string };
    const error = new Error(body.message || `请求失败（${response.status}）`) as ApiError;
    error.code = body.code; error.status = response.status;
    throw error;
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

async function requestBlob(path: string, retry = true): Promise<Blob> {
  const headers = new Headers({ Accept: "image/*,application/pdf" });
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  const response = await fetch(path, { headers, credentials: "include" });
  if (response.status === 401 && retry) {
    if (!refreshInFlight) refreshInFlight = refreshWebSession().finally(() => { refreshInFlight = null; });
    if (await refreshInFlight) return requestBlob(path, false);
  }
  if (!response.ok) throw new Error(`附件加载失败（${response.status}）`);
  return response.blob();
}

export async function login(username: string, password: string) {
  const result = await request<{ accessToken: string; user: User }>("/auth/web-login", { method: "POST", body: JSON.stringify({ username, password }) }, false);
  setAccessToken(result.accessToken);
  return result;
}

export async function refreshWebSession() {
  try {
    const result = await request<{ accessToken: string }>("/auth/web-refresh", { method: "POST" }, false);
    setAccessToken(result.accessToken);
    return true;
  } catch { setAccessToken(null); return false; }
}

export async function logout() { await request<void>("/auth/web-logout", { method: "POST" }, false); setAccessToken(null); }
export const getProfile = () => request<Profile>("/auth/profile");
export const getUserAvatar = () => requestBlob("/auth/profile/avatar");
export const updateOwnProfile = (payload: { username?: string; currentPassword?: string; password?: string }) => request<void>("/auth/profile", { method: "POST", body: JSON.stringify(payload) });
export const uploadOwnAvatar = (file: File) => { const body = new FormData(); body.append("avatar", file); return request<Profile>("/auth/profile/avatar", { method: "POST", body }); };
export const removeOwnAvatar = () => request<Profile>("/auth/profile/avatar/delete", { method: "POST" });
export const searchVehicles = (keyword: string) => request<{ candidates?: VehicleCandidate[]; items?: VehicleCandidate[] }>(`/vehicles/search?keyword=${encodeURIComponent(keyword)}`);
export const getVehicle = (id: number) => request<VehicleDetail>(`/vehicles/${id}`);
export async function searchHome(keyword: string): Promise<{ workOrderCandidates?: WorkOrder[]; wechatMessages?: WechatMessage[]; workOrderFailed?: boolean; wechatMessageFailed?: boolean }> {
  try {
    return await request<{ workOrderCandidates?: WorkOrder[]; wechatMessages?: WechatMessage[]; workOrderFailed?: boolean; wechatMessageFailed?: boolean }>(`/work-orders/home-search?keyword=${encodeURIComponent(keyword)}`);
  } catch (error) {
    // 当前服务端未启用微信模块时，车辆查询仍应正常显示；模块恢复后无需改前端即可继续使用。
    const apiError = error as ApiError;
    // 微信权限拒绝只影响微信分组，不阻断已经允许查询的车辆档案。
    if (apiError.status === 403 && apiError.code === "WORK_ORDER_PERMISSION_DENIED") {
      return { workOrderCandidates: [], wechatMessages: [] };
    }
    if (apiError.status === 404) {
      return { workOrderCandidates: [], wechatMessages: [], workOrderFailed: true, wechatMessageFailed: true };
    }
    throw error;
  }
}
export const getWorkOrder = (id: number) => request<WorkOrder>(`/work-orders/${id}`);
export const getWechatMessage = (id: number) => request<WechatMessage>(`/work-orders/messages/${id}`);
export const downloadAttachment = (id: number, variant = "preview") => requestBlob(`/work-orders/attachments/${encodeURIComponent(id)}?variant=${encodeURIComponent(variant)}`);
export const getWechatStatus = async () => {
  const result = await request<{ sources?: Array<{ sourceKey: string; displayName: string; status: string; lastUploadedAt?: string; lastHeartbeatAt?: string }> }>("/work-orders/status");
  return result.sources || [];
};
export const getWeek = (date: string) => request<ScheduleWeek>(`/schedule/week?date=${encodeURIComponent(date)}`);
export const getMonth = (date: string) => request<ScheduleMonth>(`/schedule/month?month=${encodeURIComponent(date.slice(0, 7))}`);
export type StatisticsFilters = { range: "TODAY" | "SEVEN_DAYS" | "THIRTY_DAYS" | "ALL_TIME"; category: string; scope: "ME" | "ALL" };
export type StatisticsHistoryItem = { vehicleId: number; plateNumber: string; category: string; occurredAtEpochMillis: number };
export type StatisticsHistory = { items: StatisticsHistoryItem[]; total: number };
export type QueryEvent = { eventId: string; vehicleId: number; occurredAtEpochMillis: number };
function statisticsParams(filters: StatisticsFilters) {
  const params = new URLSearchParams({ range: filters.range, scope: filters.scope });
  if (filters.category) params.set("category", filters.category);
  return params;
}
export const getStatistics = (filters: StatisticsFilters, signal?: AbortSignal) => request<StatisticsData>(`/statistics?${statisticsParams(filters)}`, { signal });
export const getStatisticsHistory = (filters: StatisticsFilters, query = "", offset = 0, signal?: AbortSignal) => {
  const params = statisticsParams(filters);
  params.set("limit", "20"); params.set("offset", String(offset));
  const normalized = query.replace(/[\s·]/g, "");
  if (normalized) params.set("query", normalized);
  return request<StatisticsHistory>(`/statistics/events?${params}`, { signal });
};
export const synchronizeQueryEvents = (events: QueryEvent[]) => request<{ acceptedEventIds: string[] }>("/statistics/events", { method: "POST", body: JSON.stringify({ events }) });
export const getAdminSummary = (signal?: AbortSignal) => request<unknown>("/admin/dashboard-summary", { signal });
const adminVehicleStatuses = new Set(["ACTIVE", "INACTIVE", "BLACKLISTED", "STRICT_CHECK", "DELETED"]);
export function normalizeAdminVehicleStatus(status: string) {
  const normalized = status.trim().toUpperCase();
  if (!adminVehicleStatuses.has(normalized)) throw new Error("车辆状态值无效，请重新选择车辆状态");
  return normalized;
}
export const getAdminVehicles = (keyword = "", status = "", limit = 50, offset = 0, signal?: AbortSignal) => {
  const normalizedStatus = status.trim().toUpperCase();
  const filterStatus = normalizedStatus === "ALL" || !normalizedStatus ? "" : normalizeAdminVehicleStatus(normalizedStatus);
  const params = new URLSearchParams({ keyword, limit: String(limit), offset: String(offset) });
  if (filterStatus) params.set("status", filterStatus);
  return request<unknown>(`/admin/vehicles?${params}`, { signal });
};
export type AdminVehicleCapabilities = { creatableCategories: string[]; canChangeVehicleCategory: boolean };
export const getAdminVehicleCapabilities = (signal?: AbortSignal) => request<AdminVehicleCapabilities>("/admin/vehicles/creation-capabilities", { signal });
export const getAdminVehicle = (id: number) => request<Record<string, unknown>>(`/admin/vehicles/${id}`);
export const getAdminUsers = (signal?: AbortSignal) => request<unknown>("/admin/users?limit=50&offset=0", { signal });
export const getAdminUserAvatar = (id: number) => requestBlob(`/admin/users/${id}/avatar`);
export const getImports = (signal?: AbortSignal, limit = 30, offset = 0) => request<unknown>(`/admin/imports?limit=${limit}&offset=${offset}`, { signal });
export type AuditQuery = { range?: string; actorId?: number | null; actionType?: string | null; result?: string | null; limit?: number; offset?: number };
export const getAudit = (query: AuditQuery = {}, signal?: AbortSignal) => { const params = new URLSearchParams({ range: query.range || "24h", limit: String(query.limit ?? 50), offset: String(query.offset ?? 0) }); if (query.actorId) params.set("actorId", String(query.actorId)); if (query.actionType) params.set("actionType", query.actionType); if (query.result && query.result !== "ALL") params.set("result", query.result); return request<unknown>(`/admin/audit?${params}`, { signal }); };
export type ClientPolicy = { revision: number; vehicleResultLimit: number; workOrderResultLimit: number; wechatMessageResultLimit: number; apiBaseUrl: string; updateBaseUrl: string; clientCount: number; appliedClientCount: number };
export type ScheduleParticipant = { id: number; username: string; realName: string; status: string };
export type ScheduleConfiguration = { cycleDays: number; participants: ScheduleParticipant[]; candidates: ScheduleParticipant[] };
export type ScheduleTemplate = { id: number; name: string; cycleDays: number; versionNumber: number; status: string; effectiveFrom: string | null; effectiveUntil: string | null };
export const getClientPolicy = (signal?: AbortSignal) => request<ClientPolicy>("/admin/client-policy", { signal });
export const updateClientLimits = (payload: Pick<ClientPolicy, "vehicleResultLimit" | "workOrderResultLimit" | "wechatMessageResultLimit">) => request<ClientPolicy>("/admin/client-policy/limits", { method: "PUT", body: JSON.stringify(payload) });
export const updateClientEndpoint = (kind: "api" | "update", baseUrl: string) => request<ClientPolicy>(`/admin/client-policy/${kind}-endpoint`, { method: "PUT", body: JSON.stringify({ baseUrl }) });
export const testClientEndpoint = (kind: "api" | "update", baseUrl: string) => request<{ success: boolean; message: string }>(`/admin/client-policy/${kind}-endpoint/test`, { method: "POST", body: JSON.stringify({ baseUrl }) });
export const getScheduleConfiguration = (signal?: AbortSignal) => request<ScheduleConfiguration>("/admin/schedules/configuration", { signal });
export const updateScheduleConfiguration = (cycleDays: number, participantIds: number[]) => request<ScheduleConfiguration>("/admin/schedules/configuration", { method: "PUT", body: JSON.stringify({ cycleDays, participantIds }) });
export const getScheduleTemplates = (signal?: AbortSignal) => request<{ items: ScheduleTemplate[] }>("/admin/schedules/templates", { signal });
export const previewScheduleTemplate = (id: number, date: string) => request<ScheduleWeek>(`/admin/schedules/templates/${id}/preview?effectiveFrom=${encodeURIComponent(date)}`);
export const applyScheduleTemplate = (templateId: number, effectiveFrom: string, effectiveUntil: string | null) => request<unknown>("/admin/schedules/applications", { method: "POST", body: JSON.stringify({ templateId, effectiveFrom, effectiveUntil }) });
export const createAdminUser = (payload: unknown) => request<unknown>("/admin/users", { method: "POST", body: JSON.stringify(payload) });
export const updateAdminUser = (id: number, payload: unknown, version: number) => request<unknown>(`/admin/users/${id}`, { method: "PUT", headers: { "If-Match-Version": String(version) }, body: JSON.stringify(payload) });
function normalizeVehiclePayload(payload: unknown) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return payload;
  const value = payload as Record<string, unknown>;
  if (typeof value.status !== "string") return payload;
  return { ...value, status: normalizeAdminVehicleStatus(value.status) };
}
export const createAdminVehicle = (payload: unknown) => request<unknown>("/admin/vehicles", { method: "POST", body: JSON.stringify(normalizeVehiclePayload(payload)) });
export const updateAdminVehicle = (id: number, payload: unknown, version: number) => request<unknown>(`/admin/vehicles/${id}`, { method: "PUT", headers: { "If-Match-Version": String(version) }, body: JSON.stringify(normalizeVehiclePayload(payload)) });
export const updateAdminVehicleStatus = (id: number, status: string, version: number) => request<unknown>(`/admin/vehicles/${id}/status`, { method: "POST", headers: { "If-Match-Version": String(version) }, body: JSON.stringify({ status: normalizeAdminVehicleStatus(status) }) });
export const previewImport = (file: File) => { const body = new FormData(); body.append("file", file); return request<unknown>("/admin/imports/preview", { method: "POST", body }); };
export const getImportBatch = (id: number, filter = "REVIEW", limit = 50, offset = 0, signal?: AbortSignal) => request<unknown>(`/admin/imports/${id}?filter=${encodeURIComponent(filter)}&limit=${limit}&offset=${offset}`, { signal });
export const getImportRowDetail = (batchId: number, rowId: number, signal?: AbortSignal) => request<unknown>(`/admin/imports/${batchId}/rows/${rowId}`, { signal });
export const resolveImportRows = (id: number, rows: unknown[]) => request<unknown>(`/admin/imports/${id}/rows/resolutions`, { method: "POST", body: JSON.stringify({ rows }) });
export const publishImport = (id: number) => request<unknown>(`/admin/imports/${id}/publish`, { method: "POST" });
export const rollbackImport = (id: number) => request<unknown>(`/admin/imports/${id}/rollback`, { method: "POST" });
export const getRebuild = (signal?: AbortSignal) => request<unknown>("/admin/wechat-sync/rebuild/current", { signal });
export const getBackups = (signal?: AbortSignal) => request<unknown[]>("/admin/wechat-sync/backups", { signal });
export const previewRebuild = () => request<unknown>("/admin/wechat-sync/rebuild/preview", { method: "POST" });
export const lockRebuild = (runId: string, confirmation: string) => request<unknown>(`/admin/wechat-sync/rebuild/${encodeURIComponent(runId)}/lock?confirmation=${encodeURIComponent(confirmation)}`, { method: "POST" });
export const verifyRebuildBackup = (runId: string) => request<unknown>(`/admin/wechat-sync/rebuild/${encodeURIComponent(runId)}/backup-verify`, { method: "POST" });
export const cleanRebuild = (runId: string, confirmation: string) => request<unknown>(`/admin/wechat-sync/rebuild/${encodeURIComponent(runId)}/clean?confirmation=${encodeURIComponent(confirmation)}`, { method: "POST" });
export const verifyRebuild = (runId: string) => request<unknown>(`/admin/wechat-sync/rebuild/${encodeURIComponent(runId)}/verify`, { method: "POST" });
export const unlockRebuild = (runId: string, success: boolean) => request<unknown>(`/admin/wechat-sync/rebuild/${encodeURIComponent(runId)}/unlock?success=${success}`, { method: "POST" });
export const restoreBackup = (backupId: string) => request<unknown>(`/admin/wechat-sync/backups/${encodeURIComponent(backupId)}/restore`, { method: "POST" });

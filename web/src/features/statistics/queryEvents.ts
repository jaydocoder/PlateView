import { synchronizeQueryEvents, type QueryEvent } from "../../api";

const inFlight = new Map<number, Promise<void>>();
const key = (accountId: number) => `plateview-query-events:${accountId}`;
function eventId() {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  // 局域网手机以普通 HTTP 访问时没有 randomUUID，仍需符合服务端 UUID 协议。
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
export function pendingQueryEvents(accountId: number): QueryEvent[] {
  try {
    const data: unknown = JSON.parse(localStorage.getItem(key(accountId)) || "[]");
    return Array.isArray(data) ? data.filter((item): item is QueryEvent => typeof item?.eventId === "string" && Number.isFinite(item.vehicleId) && Number.isFinite(item.occurredAtEpochMillis)) : [];
  } catch { return []; }
}

export function flushQueryEvents(accountId: number): Promise<void> {
  const current = inFlight.get(accountId);
  if (current) return current;
  const job = (async () => {
    while (pendingQueryEvents(accountId).length) {
      const events = pendingQueryEvents(accountId).slice(0, 200);
      const response = await synchronizeQueryEvents(events);
      const accepted = new Set(response.acceptedEventIds);
      if (!accepted.size) throw new Error("查询记录尚未同步，请重试");
      // 只移除已确认的事件，保留请求期间新增的记录。
      localStorage.setItem(key(accountId), JSON.stringify(pendingQueryEvents(accountId).filter(item => !accepted.has(item.eventId))));
    }
  })().finally(() => inFlight.delete(accountId));
  inFlight.set(accountId, job);
  return job;
}

export async function recordVehicleQuery(accountId: number, vehicleId: number) {
  const events = pendingQueryEvents(accountId);
  events.push({ eventId: eventId(), vehicleId, occurredAtEpochMillis: Date.now() });
  localStorage.setItem(key(accountId), JSON.stringify(events));
  await flushQueryEvents(accountId);
}

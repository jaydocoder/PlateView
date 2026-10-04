import { useEffect, useRef, useState } from "react";
import { getStatistics, getStatisticsHistory, type Profile, type StatisticsData, type StatisticsFilters, type StatisticsHistoryItem } from "../../api";
import { canAccessPrimaryAdminModules } from "../../domain";
import { GlassInput } from "../../components/glass";
import { Search, X, RefreshCw, ChevronDown } from "../../components/icons";
import { flushQueryEvents, pendingQueryEvents } from "./queryEvents";
import "./statistics.css";

export const statisticsCategories = [
  ["RESIDENT", "村民车辆", "村民", "#087b8a"],
  ["SCENIC_UNIT", "驻景区单位车辆", "单位", "#1c604b"],
  ["SCENIC_ENTERPRISE", "驻景区企业车辆", "企业", "#d59a36"],
  ["CADRE", "干部车辆", "干部", "#5c7e9e"],
  ["KANAS_TOURISM_DEVELOPMENT", "喀旅公司车辆", "喀旅", "#b83e4a"],
  ["OTHER_LONG_TERM", "其他长期通行车辆", "其他", "#737d5c"],
] as const;
const ranges: Array<[StatisticsFilters["range"], string]> = [["TODAY", "今天"], ["SEVEN_DAYS", "近7天"], ["THIRTY_DAYS", "近30天"], ["ALL_TIME", "全部时间"]];
const message = (error: unknown) => error instanceof Error ? error.message : "统计加载失败";
const time = (value: number) => new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(new Date(value));
function Plate({ value }: { value: string }) { return <span className="plate-badge plate-blue">{value}</span>; }

export function StatisticsPage({ profile, onOpenVehicle }: { profile: Profile; onOpenVehicle: (id: number) => Promise<void> }) {
  const [filters, setFilters] = useState<StatisticsFilters>({ range: "TODAY", category: "", scope: "ME" });
  const [query, setQuery] = useState("");
  const [settledQuery, setSettledQuery] = useState("");
  const [data, setData] = useState<StatisticsData | null>(null);
  const [history, setHistory] = useState<StatisticsHistoryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [moreLoading, setMoreLoading] = useState(false);
  const [error, setError] = useState("");
  const [moreError, setMoreError] = useState("");
  const [revision, setRevision] = useState(0);
  const [pending, setPending] = useState(0);
  const sentinel = useRef<HTMLDivElement>(null);
  const controller = useRef<AbortController | null>(null);
  const pageLock = useRef(false);
  const historyOffset = useRef(0);
  const generation = useRef(0);
  const [opening, setOpening] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setSettledQuery(query), 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const current = ++generation.current;
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    pageLock.current = false;
    historyOffset.current = 0;
    setLoading(true); setError(""); setMoreError(""); setMoreLoading(false); setHistory([]); setTotal(0); setData(null);
    const load = async () => {
      await flushQueryEvents(profile.id).catch(() => undefined);
      if (request.signal.aborted) return;
      setPending(pendingQueryEvents(profile.id).length);
      const [statistics, page] = await Promise.all([getStatistics(filters, request.signal), getStatisticsHistory(filters, settledQuery, 0, request.signal)]);
      if (current !== generation.current || request.signal.aborted) return;
      historyOffset.current = page.items.length;
      setData(statistics); setHistory(page.items); setTotal(page.total);
    };
    void load().catch(err => { if (!request.signal.aborted) setError(message(err)); }).finally(() => { if (!request.signal.aborted) setLoading(false); });
    return () => request.abort();
  }, [filters, settledQuery, revision, profile.id]);

  const loadMore = async () => {
    if (loading || pageLock.current || historyOffset.current >= total) return;
    pageLock.current = true; setMoreLoading(true); setMoreError("");
    const current = generation.current;
    try {
      const page = await getStatisticsHistory(filters, settledQuery, historyOffset.current, controller.current?.signal);
      if (current !== generation.current) return;
      historyOffset.current += page.items.length;
      if (!page.items.length && historyOffset.current < page.total) throw new Error("查询记录未返回下一页，请重试");
      setHistory(items => [...items, ...page.items]); setTotal(page.total);
    } catch (err) { if (current === generation.current && !controller.current?.signal.aborted) setMoreError(message(err)); }
    finally { if (current === generation.current) { pageLock.current = false; setMoreLoading(false); } }
  };

  useEffect(() => {
    if (!sentinel.current || loading || moreError || !history.length || history.length >= total) return;
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) void loadMore(); }, { rootMargin: "0px 0px 180px 0px" });
    observer.observe(sentinel.current);
    return () => observer.disconnect();
  }, [loading, history, total, moreError, moreLoading]);

  const open = async (plate: string, id?: number) => {
    if (opening) return;
    setOpening(true); setError("");
    try {
      const vehicleId = id || history.find(item => item.plateNumber === plate)?.vehicleId || (await getStatisticsHistory(filters, plate, 0, controller.current?.signal)).items.find(item => item.plateNumber === plate)?.vehicleId;
      if (!vehicleId) throw new Error("未找到对应车辆档案");
      await onOpenVehicle(vehicleId);
    } catch (err) { if (!controller.current?.signal.aborted) setError(message(err)); }
    finally { setOpening(false); }
  };
  const overview = !filters.category && !query.trim();
  const max = Math.max(1, ...(data?.categories || []).map(item => item.queryCount));
  return <section className="page statistics-page">
    <header className="statistics-heading"><h2>查询统计</h2><div className="statistics-search"><Search size={21} aria-hidden="true" /><GlassInput aria-label="搜索历史车牌" placeholder="搜索历史车牌" value={query} onChange={event => setQuery(event.target.value)} />{query && <button aria-label="清空历史车牌搜索" onClick={() => setQuery("")}><X size={18} /></button>}</div></header>
<div className="statistics-filters"><h3>统计条件</h3><div className="statistics-ranges" role="group" aria-label="时间范围">{ranges.map(([value, label]) => <button key={value} aria-pressed={filters.range === value} onClick={() => setFilters(previous => ({ ...previous, range: value }))}>{label}</button>)}</div><span className="statistics-category"><select aria-label="车辆类别" value={filters.category} onChange={event => setFilters(previous => ({ ...previous, category: event.target.value }))}><option value="">全部类别</option>{statisticsCategories.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><ChevronDown size={16} aria-hidden="true" /></span>{profile.role === "ADMIN" && <div className="statistics-scopes" role="group" aria-label="统计范围"><button aria-pressed={filters.scope === "ME"} onClick={() => setFilters(previous => ({ ...previous, scope: "ME" }))}>我的统计</button>{canAccessPrimaryAdminModules(profile) && <button aria-pressed={filters.scope === "ALL"} onClick={() => setFilters(previous => ({ ...previous, scope: "ALL" }))}>全员统计</button>}</div>}{pending > 0 && <button className="statistics-sync" onClick={() => setRevision(value => value + 1)}><RefreshCw size={16} />{pending} 条记录待同步</button>}</div>
    {error && <div className="statistics-error" role="alert"><span>{error}</span><button onClick={() => setRevision(value => value + 1)}>重新加载</button></div>}
    {loading ? <div className="statistics-state" role="status"><span className="spinner" />正在读取统计数据</div> : data && <>
      {data.summary.totalQueries === 0 ? <div className="statistics-state">当前条件下还没有查询记录</div> : <>
        {overview && <div className="statistics-overview"><section className="statistics-ranking"><h3>查询最多的车牌</h3>{data.topPlates.slice(0, 5).map((item, index) => <button key={item.plateNumber} disabled={opening} onClick={() => void open(item.plateNumber)}><span className="statistics-rank">{index + 1}</span><Plate value={item.plateNumber} /><span>{item.queryCount} 次</span></button>)}</section>
          <section className="statistics-chart"><h3>类别查询数量</h3><div className="statistics-chart-area"><div className="statistics-axis"><span>{max}</span><span>{Math.floor(max / 2)}</span><span>0</span></div><div className="statistics-columns">{statisticsCategories.map(([category, label, short, color]) => { const count = data.categories.find(item => item.category === category)?.queryCount || 0; return <div className="statistics-column" key={category} aria-label={`${label}：${count}次`}><div className="statistics-bar-space"><div className="statistics-bar" style={{ height: `${Math.max(2, count / max * 86)}%`, backgroundColor: color }}><span>{count}</span></div></div><span>{short}</span></div>; })}</div></div></section></div>}
        <section className="statistics-history"><h3>查询记录</h3>{history.map((item, index) => <button className="statistics-history-row" key={`${item.vehicleId}-${item.occurredAtEpochMillis}-${index}`} disabled={opening} onClick={() => void open(item.plateNumber, item.vehicleId)}><Plate value={item.plateNumber} /><time dateTime={new Date(item.occurredAtEpochMillis).toISOString()}>{time(item.occurredAtEpochMillis)}</time></button>)}{!history.length && <div className="statistics-state">{query.trim() ? "未找到匹配的查询记录" : "当前条件下还没有查询记录"}</div>}<div ref={sentinel} className="statistics-sentinel">{moreLoading && <span role="status">正在加载查询记录</span>}{moreError && <div role="alert">{moreError}<button onClick={() => void loadMore()}>重试</button></div>}</div></section>
      </>}
    </>}
  </section>;
}

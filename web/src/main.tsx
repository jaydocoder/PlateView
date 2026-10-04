import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, useLocation, useNavigate } from "react-router-dom";
import {
  getAdminSummary, getAdminUsers, getAdminVehicles, getAdminVehicle, getAdminVehicleCapabilities, getMonth, getProfile, getRebuild,
  downloadAttachment, getVehicle, getWechatMessage, getWechatStatus, getWeek, getWorkOrder, login, logout, refreshWebSession,
  searchHome, searchVehicles, type Profile, type ScheduleMonth, type ScheduleWeek, type VehicleCandidate, type VehicleDetail, type WechatMessage, type WorkOrder,
  cleanRebuild, createAdminUser, createAdminVehicle, getBackups, lockRebuild, previewRebuild, restoreBackup, unlockRebuild, updateAdminUser, updateAdminVehicle, updateAdminVehicleStatus, verifyRebuild, verifyRebuildBackup,
} from "./api";
import { canAccessPrimaryAdminModules, canOpenCandidate, rebuildStateLabel, selectWechatCandidatePlate, selectWechatMessageCandidatePlate } from "./domain";
import { adaptAdminList, adaptAttachments, type AdminList } from "./adapters";
import { Activity, ArrowLeft, ArrowRight, CalendarDays, CarFront, ChevronLeft, ChevronRight, CircleUserRound, ClipboardList, FileText, Image as ImageIcon, LayoutDashboard, LogOut, MessageCircle, RefreshCw, Search, ShieldCheck, Trash2, UserRound, Users, X } from "./components/icons";
import { GlassButton, GlassCard, GlassDialog, GlassInput } from "./components/glass";
import { ImportCenter } from "./features/admin/ImportCenter";
import { AppShell } from "./components/AppShell";
import { AdminUserAvatar, CurrentUserAvatar } from "./components/AdminUserAvatar";
import { VehicleDetailPage, WechatMessageDetailPage, WorkOrderDetailPage } from "./features/detail/DetailViews";
import { StatisticsPage } from "./features/statistics/StatisticsPage";
import { ProfilePage } from "./features/profile/ProfilePage";
import { buildVehicleCommand } from "./features/admin/vehicleEditor";
import { visibleAdminModules } from "./features/admin/modules";
import { AuditPage } from "./features/admin/AuditPage";
import { ClientPolicyPage, SchedulePlanningPage } from "./features/admin/ConfigurationPages";
import { recordVehicleQuery, flushQueryEvents } from "./features/statistics/queryEvents";
import "./styles.css";
import "./glass.css";
import { OperationFeedback } from "./components/OperationFeedback";
import { notify } from "./state/feedback";

type Screen = "search" | "schedule" | "statistics" | "profile" | "admin";
type AsyncState<T> = { loading: boolean; error: string | null; data: T | null };

const labels: Record<string, string> = {
  RESIDENT: "村民车辆", SCENIC_UNIT: "驻景区单位", SCENIC_ENTERPRISE: "驻景区企业", CADRE: "干部车辆",
  KANAS_TOURISM_DEVELOPMENT: "喀旅公司", OTHER_LONG_TERM: "其他长期车辆",
};

function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [booting, setBooting] = useState(true);
  const [loginError, setLoginError] = useState("");
  const [detail, setDetail] = useState<VehicleDetail | null>(null);
  const [workOrderDetail, setWorkOrderDetail] = useState<WorkOrder | null>(null);
  const [messageDetail, setMessageDetail] = useState<WechatMessage | null>(null);
  const screen = screenFromPath(location.pathname);
  const recordedDetail = useRef<VehicleDetail | null>(null);

  useEffect(() => {
    if (profile) void flushQueryEvents(profile.id).catch(() => undefined);
  }, [profile?.id]);

  useEffect(() => {
    if (detail && profile && recordedDetail.current !== detail) {
      recordedDetail.current = detail;
      void recordVehicleQuery(profile.id, detail.id).catch(() => undefined);
    }
    if (!detail) recordedDetail.current = null;
  }, [detail, profile?.id]);

  useEffect(() => {
    refreshWebSession().then(ok => ok ? getProfile().then(setProfile).catch(() => setProfile(null)) : undefined).finally(() => setBooting(false));
  }, []);

  useEffect(() => {
    const match = location.pathname.match(/^\/(vehicle|work-order|wechat-message)\/(\d+)$/);
    if (!match || !profile) return;
    const id = Number(match[2]);
    if (match[1] === "vehicle" && !detail) void getVehicle(id).then(setDetail).catch(() => setDetail(null));
    if (match[1] === "work-order" && !workOrderDetail) void getWorkOrder(id).then(setWorkOrderDetail).catch(() => setWorkOrderDetail(null));
    if (match[1] === "wechat-message" && !messageDetail) void getWechatMessage(id).then(setMessageDetail).catch(() => setMessageDetail(null));
  }, [location.pathname, profile, detail, workOrderDetail, messageDetail]);

  if (booting) return <div className="boot"><div className="brand-mark">PV</div><p>正在连接车辆数据服务</p></div>;
  if (!profile) return <LoginView onSuccess={(user) => setProfile(user)} error={loginError} setError={setLoginError} />;

  const openVehicle = async (candidate: VehicleCandidate) => {
    if (!canOpenCandidate(candidate)) return;
    try { setDetail(await getVehicle(candidate.id)); navigate(`/vehicle/${candidate.id}`); } catch { setDetail(null); }
  };

  const goTo = (next: Screen) => {
    setDetail(null);
    setWorkOrderDetail(null);
    setMessageDetail(null);
    navigate(`/${next}`);
  };

  const detailShell = (content: React.ReactNode) => <AppShell profile={profile} screen="search" onNavigate={goTo} onLogout={async () => { await logout().catch(() => undefined); setProfile(null); goTo("search"); }}>{content}</AppShell>;
  if (detail && screen === "search") return detailShell(<VehicleDetailPage detail={detail} onBack={() => { setDetail(null); navigate("/search"); }} />);
  if (workOrderDetail && screen === "search") return detailShell(<WorkOrderDetailPage detail={workOrderDetail} onBack={() => { setWorkOrderDetail(null); navigate("/search"); }} />);
  if (messageDetail && screen === "search") return detailShell(<WechatMessageDetailPage detail={messageDetail} onBack={() => { setMessageDetail(null); navigate("/search"); }} />);

  return <AppShell profile={profile} screen={screen} onNavigate={goTo} onLogout={async () => { await logout().catch(() => undefined); setProfile(null); goTo("search"); }}>
    {screen === "search" && <SearchView username={profile.username} onOpenVehicle={openVehicle} onOpenWorkOrder={async id => { setWorkOrderDetail(await getWorkOrder(id)); navigate(`/work-order/${id}`); }} onOpenMessage={async id => { setMessageDetail(await getWechatMessage(id)); navigate(`/wechat-message/${id}`); }} />}
    {screen === "schedule" && profile.scheduleEnabled && <ScheduleView />}
    {screen === "statistics" && <StatisticsPage profile={profile} onOpenVehicle={async id => { const vehicle = await getVehicle(id); setDetail(vehicle); navigate(`/vehicle/${id}`); }} />}
    {screen === "profile" && <ProfilePage profile={profile} onProfile={setProfile} onLogout={async () => { await logout(); setProfile(null); }} />}
    {screen === "admin" && profile.role === "ADMIN" && <AdminView isPrimaryAdministrator={canAccessPrimaryAdminModules(profile)} />}
  </AppShell>;
}

function screenFromPath(pathname: string): Screen {
  if (pathname.startsWith("/schedule")) return "schedule";
  if (pathname.startsWith("/statistics")) return "statistics";
  if (pathname.startsWith("/profile")) return "profile";
  if (pathname.startsWith("/admin")) return "admin";
  return "search";
}

function LoginView({ onSuccess, error, setError }: { onSuccess: (profile: Profile) => void; error: string; setError: (value: string) => void }) {
  const [username, setUsername] = useState(""); const [password, setPassword] = useState(""); const [busy, setBusy] = useState(false);
  return <main className="login-page"><div className="login-panel">
    <div className="eyebrow">PLATEVIEW / VEHICLE CONTROL</div><div className="brand-line"><div className="brand-mark">PV</div><div><h1>车辆核验</h1><p>让每一次通行确认都有依据</p></div></div>
    <form onSubmit={async e => { e.preventDefault(); setBusy(true); setError(""); try { await login(username, password); onSuccess(await getProfile()); } catch (err) { setError(err instanceof Error ? err.message : "登录失败"); } finally { setBusy(false); } }}>
      <label>账号<input autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} placeholder="请输入账号" /></label>
      <label>密码<input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} placeholder="请输入密码" /></label>
      {error && <div className="error-banner">{error}</div>}<button className="primary wide" disabled={busy || !username || !password}>{busy ? "正在登录" : "登录系统"}</button>
    </form><p className="login-note">在线查询 · 权限实时生效 · 支持 iPhone 与移动浏览器</p>
  </div></main>;
}

function Shell({ profile, screen, onNavigate, onLogout, children }: { profile: Profile; screen: Screen; onNavigate: (screen: Screen) => void; onLogout: () => void; children: React.ReactNode }) {
  const nav: Array<[Screen, string, React.ReactNode]> = [["search", "首页", <LayoutDashboard size={19} />], ...(profile.scheduleEnabled ? [["schedule", "排班", <CalendarDays size={19} />] as [Screen, string, React.ReactNode]] : []), ["statistics", "统计", <Activity size={19} />], ["profile", "我的", <UserRound size={19} />]];
  return <div className="app-shell"><aside className="sidebar"><div className="sidebar-brand"><div className="brand-mark small">PV</div><span>PlateView</span></div><div className="user-chip"><div className="avatar"><CircleUserRound size={18} /></div><div><strong>{profile.username}</strong><small>{profile.role === "ADMIN" ? "管理员" : "核验用户"}</small></div></div><nav>{nav.map(([key, label, icon]) => <button key={key} className={screen === key ? "nav-item active" : "nav-item"} onClick={() => onNavigate(key)}><span aria-hidden="true">{icon}</span>{label}</button>)}{profile.role === "ADMIN" && <button className={screen === "admin" ? "nav-item active" : "nav-item"} onClick={() => onNavigate("admin")}><span aria-hidden="true"><ShieldCheck size={19} /></span>工作台</button>}</nav><div className="sidebar-foot"><span className="online-dot" /> 服务在线<GlassButton tone="ghost" className="sidebar-logout" onClick={onLogout}><LogOut size={15} />退出</GlassButton></div></aside><main className="main-area"><header className="mobile-header"><div className="brand-mark small">PV</div><strong>{screen === "search" ? "车辆核验" : nav.find(item => item[0] === screen)?.[1] || "工作台"}</strong><button className="avatar" onClick={() => onNavigate("profile")} aria-label="打开我的"><CircleUserRound size={18} /></button></header><div className="content-wrap">{children}</div><nav className="bottom-nav">{nav.map(([key, label, icon]) => <button key={key} className={screen === key ? "active" : ""} onClick={() => onNavigate(key)}><span aria-hidden="true">{icon}</span>{label}</button>)}{profile.role === "ADMIN" && <button className={screen === "admin" ? "active" : ""} onClick={() => onNavigate("admin")}><span aria-hidden="true"><ShieldCheck size={18} /></span>管理</button>}</nav></main></div>;
}

function SearchView({ username, onOpenVehicle, onOpenWorkOrder, onOpenMessage }: { username: string; onOpenVehicle: (candidate: VehicleCandidate) => void; onOpenWorkOrder: (id: number) => void; onOpenMessage: (id: number) => void }) {
  const historyKey = `plateview.search.history.${username}`;
  const [query, setQuery] = useState(""); const [history, setHistory] = useState<string[]>(() => JSON.parse(localStorage.getItem(historyKey) || "[]")); const [results, setResults] = useState<{ candidates: VehicleCandidate[]; workOrders: WorkOrder[]; messages: WechatMessage[] }>({ candidates: [], workOrders: [], messages: [] }); const [state, setState] = useState<AsyncState<unknown>>({ loading: false, error: null, data: null });
  const [wechatStatus, setWechatStatus] = useState<Array<{ status: string; lastUploadedAt?: string }>>([]);
  useEffect(() => { getWechatStatus().then(setWechatStatus).catch(() => setWechatStatus([])); }, []);
  const rememberSearch = (value: string) => { const normalized = value.trim(); if (!normalized) return; const next = [normalized, ...history.filter(item => item !== normalized)].slice(0, 8); setHistory(next); localStorage.setItem(historyKey, JSON.stringify(next)); };
  useEffect(() => { if (!query.trim()) { setResults({ candidates: [], workOrders: [], messages: [] }); setState({ loading: false, error: null, data: null }); return; } let active = true; const timer = window.setTimeout(() => { rememberSearch(query); setState({ loading: true, error: null, data: null }); Promise.all([searchVehicles(query), searchHome(query)]).then(([vehicles, home]) => { if (active) setResults({ candidates: (vehicles.candidates || vehicles.items || []).filter(item => item.status !== "DELETED"), workOrders: home.workOrderCandidates || [], messages: home.wechatMessages || [] }); }).catch(err => { if (active) setState({ loading: false, error: err instanceof Error ? err.message : "查询失败", data: null }); }).finally(() => { if (active) setState(s => ({ ...s, loading: false })); }); }, 250); return () => { active = false; window.clearTimeout(timer); }; }, [query]);
  const healthy = wechatStatus.length > 0 && wechatStatus.every(item => item.status === "HEALTHY");
  return <section className="page"><div className="home-fixed-tools"><div className="page-heading"><div><h2>车辆核验</h2></div><div className="status-pills"><span className="status-pill"><span className="online-dot" />数据在线</span><span className={`status-pill wechat-status ${healthy ? "healthy" : "abnormal"}`}><span className="status-dot" />微信{healthy ? "正常" : "异常"}</span></div></div><div className="search-box"><Search size={21} aria-hidden="true" /><GlassInput value={query} onChange={e => setQuery(e.target.value)} autoFocus />{query && <button className="clear-search" onClick={() => setQuery("")} aria-label="清空搜索"><X size={18} /></button>}</div></div>{state.error && <div className="error-banner">{state.error}<button className="text-button" onClick={() => setQuery(query)}>重试</button></div>}{state.loading && <div className="loading-line"><span className="spinner" />正在查询最新数据</div>}{!query && <SearchHistory history={history} onSelect={setQuery} onClear={() => { setHistory([]); localStorage.removeItem(historyKey); }} onDelete={item => { const next = history.filter(value => value !== item); setHistory(next); localStorage.setItem(historyKey, JSON.stringify(next)); }} />}{query && !state.loading && !state.error && results.candidates.length === 0 && results.workOrders.length === 0 && results.messages.length === 0 && <div className="empty-state compact"><h3>没有找到匹配结果</h3><p>请检查车牌字符或尝试姓名、单位名称。</p></div>}{results.candidates.length > 0 && <ResultSection title="车辆档案" count={results.candidates.length}>{results.candidates.map(item => <VehicleRow key={item.id} item={item} onClick={() => onOpenVehicle(item)} />)}</ResultSection>}{results.workOrders.length > 0 && <ResultSection title="微信车单" count={results.workOrders.length}>{results.workOrders.map(item => <WorkOrderRow key={item.id} item={item} query={query} onClick={() => onOpenWorkOrder(item.id)} />)}</ResultSection>}{results.messages.length > 0 && <ResultSection title="微信聊天记录" count={results.messages.length}>{results.messages.map(item => <MessageRow key={item.id} item={item} query={query} onClick={() => onOpenMessage(item.id)} />)}</ResultSection>}</section>;
}

function SearchHistory({ history, onSelect, onClear, onDelete }: { history: string[]; onSelect: (value: string) => void; onClear: () => void; onDelete: (value: string) => void }) { const navigate = useNavigate(); if (!history.length) return <div className="empty-state"><div className="empty-icon"><CarFront size={25} /></div><h3>准备开始核验</h3><p>输入车牌的任意连续字符即可搜索。</p></div>; const open = (item: string) => { const raw = localStorage.getItem(`plateview.search.detail.${item}`); if (raw) { try { const candidate = JSON.parse(raw) as VehicleCandidate; if (candidate.id && candidate.detailAccessible !== false) { navigate(`/vehicle/${candidate.id}`); return; } } catch { /* 忽略旧的历史记录格式 */ } } onSelect(item); }; return <section className="history-section"><div className="section-title"><h3>历史查询</h3><button className="text-button" onClick={onClear}>清空</button></div><div className="history-list">{history.map(item => { const raw = localStorage.getItem(`plateview.search.detail.${item}`); let candidate: VehicleCandidate | null = null; try { candidate = raw ? JSON.parse(raw) as VehicleCandidate : null; } catch { candidate = null; } return <div className="history-row" key={item}><button className="history-row-main" onClick={() => open(item)}><span className="history-icon"><RefreshCw size={18} /></span><span><strong>{candidate?.plateNumber || item}</strong><small>{labels[candidate?.category || ""] || "车辆档案"} · {candidate?.primarySubject || "最近查询"}</small></span></button><button className="history-delete" onClick={() => onDelete(item)} aria-label={`删除搜索记录 ${item}`}><Trash2 size={17} /></button></div>; })}</div></section>; }

function WechatView({ onOpenWorkOrder, onOpenMessage }: { onOpenWorkOrder: (id: number) => void; onOpenMessage: (id: number) => void }) { const [query, setQuery] = useState(""); const [items, setItems] = useState<{ workOrders: WorkOrder[]; messages: WechatMessage[] }>({ workOrders: [], messages: [] }); const [status, setStatus] = useState<Array<{ displayName: string; status: string; lastUploadedAt?: string }>>([]); useEffect(() => { getWechatStatus().then(setStatus).catch(() => setStatus([])); }, []); useEffect(() => { const timer = window.setTimeout(() => { if (!query.trim()) { setItems({ workOrders: [], messages: [] }); return; } searchHome(query).then(result => setItems({ workOrders: result.workOrderCandidates || [], messages: result.wechatMessages || [] })).catch(() => undefined); }, 250); return () => clearTimeout(timer); }, [query]); return <section className="page"><div className="page-heading"><div><div className="eyebrow">WECHAT SOURCES</div><h2>微信同步</h2><p>查询结构化车单、聊天记录和同单号历史版本。</p></div></div><div className="source-strip">{status.map(source => <div className="source-card" key={source.displayName}><div><strong>{source.displayName}</strong><small>{source.status === "HEALTHY" ? "同步正常" : source.status}</small></div><span>最后上传<br /><b>{formatDate(source.lastUploadedAt)}</b></span></div>)}</div><div className="search-box"><span>⌕</span><input value={query} onChange={e => setQuery(e.target.value)} placeholder="搜索单号、车牌或正文" /></div>{items.workOrders.length > 0 && <ResultSection title="微信车单" count={items.workOrders.length}>{items.workOrders.map(item => <WorkOrderRow key={item.id} item={item} query={query} onClick={() => onOpenWorkOrder(item.id)} />)}</ResultSection>}{items.messages.length > 0 && <ResultSection title="聊天记录" count={items.messages.length}>{items.messages.map(item => <MessageRow key={item.id} item={item} query={query} onClick={() => onOpenMessage(item.id)} />)}</ResultSection>}{query && !items.workOrders.length && !items.messages.length && <div className="empty-state compact"><h3>没有匹配的微信记录</h3><p>可以尝试输入单号或车牌。</p></div>}</section>; }

function ScheduleView() {
  const [mode, setMode] = useState<"week" | "month">("week");
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [showAll, setShowAll] = useState(false);
  const [data, setData] = useState<ScheduleWeek | ScheduleMonth | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = () => { setError(null); setData(null); return (mode === "week" ? getWeek(date) : getMonth(date)).then(setData).catch(err => setError(err instanceof Error ? err.message : "排班加载失败")); };
  useEffect(() => { void load(); }, [mode, date]);
  const week = mode === "week" ? data as ScheduleWeek | null : null;
  const month = mode === "month" ? data as ScheduleMonth | null : null;
  const shiftWeek = (days: number) => { const next = new Date(`${date}T00:00:00`); next.setDate(next.getDate() + days); setDate(next.toISOString().slice(0, 10)); setMode("week"); };
  const visibleShifts = week?.shifts.filter(shift => showAll || shift.persons.length > 0) || [];
  return <section className="page"><div className="page-heading"><div><h2>排班</h2><p>快速确认本人或团队本周值班状态。</p></div></div><div className="schedule-toolbar"><div className="segmented"><button className={!showAll ? "selected" : ""} onClick={() => setShowAll(false)}>我的</button><button className={showAll ? "selected" : ""} onClick={() => setShowAll(true)}>全部</button></div><div className="schedule-tools"><button className="icon-button" onClick={() => setDate(new Date().toISOString().slice(0, 10))} aria-label="回到本周">今</button><button className="icon-button" onClick={() => setMode(mode === "week" ? "month" : "week")} aria-label={mode === "week" ? "打开月历" : "返回周视图"}>{mode === "week" ? "月" : "周"}</button></div></div>{error ? <ErrorState message={error} onRetry={() => void load()} /> : <><div className="week-switcher"><button className="icon-button" onClick={() => shiftWeek(-7)} aria-label="上一周">‹</button><strong>{week ? `第${week.weekNumber}周 · ${week.weekStart}` : "正在读取排班"}</strong><button className="icon-button" onClick={() => shiftWeek(7)} aria-label="下一周">›</button></div>{mode === "week" && week && <div className="schedule-grid">{visibleShifts.length ? visibleShifts.map((shift, index) => <div className="schedule-day" key={`${shift.date}-${index}`}><small>{shift.date}</small><strong className={shift.shiftType === "OFF" ? "off-label" : "duty-label"}>{shift.shiftType === "OFF" ? "休息" : shift.shiftType}</strong>{shift.persons.map(person => <span key={person.id}>{person.realName || person.username}</span>)}</div>) : <div className="empty-state compact"><h3>本周暂无排班</h3></div>}</div>}{mode === "month" && month && <div className="month-grid">{month.days.map(day => <div className={day.hasShift ? "month-day has-shift" : "month-day"} key={day.date}><span>{day.date.slice(-2)}</span>{day.hasShift ? <b>班</b> : <i>休</i>}</div>)}</div>}</>}</section>;
}



function AdminView({ isPrimaryAdministrator }: { isPrimaryAdministrator: boolean }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [tab, setTab] = useState(() => new URLSearchParams(location.search).has("importBatch") ? "导入中心" : "概览"), [data, setData] = useState<AdminList | null>(null), [dataTab, setDataTab] = useState("");
  const [refresh, setRefresh] = useState(0);
  const tabsRef = useRef<HTMLDivElement>(null);
  const tabs = ["概览", ...visibleAdminModules(isPrimaryAdministrator).map(module => module.title)];
  const selectTab = (next: string) => {
    if (!tabs.includes(next)) return;
    setTab(next);
    if (next !== "导入中心") {
      const params = new URLSearchParams(location.search);
      for (const key of ["importBatch", "importRow", "importFilter"]) params.delete(key);
      navigate({ pathname: location.pathname, search: params.toString() }, { replace: true });
    }
  };
  useEffect(() => { if (new URLSearchParams(location.search).has("importBatch")) setTab("导入中心"); }, [location.search]);
  useEffect(() => { if (!tabs.includes(tab)) setTab("概览"); }, [isPrimaryAdministrator, tab]);
  useEffect(() => {
    const loaders: Record<string, (signal: AbortSignal) => Promise<unknown>> = { "车辆档案": signal => getAdminVehicles("", "", 50, 0, signal), "账号管理": getAdminUsers };
    const loader = loaders[tab];
    if (!loader) return;
    let active = true;
    const controller = new AbortController();
    setData(null);
    loader(controller.signal).then(value => { if (active) { setData(adaptAdminList(value)); setDataTab(tab); } }).catch(reason => { if (active) { setData({ items: [], error: reason instanceof Error ? reason.message : "加载失败" }); setDataTab(tab); } });
    return () => { active = false; controller.abort(); };
  }, [tab, refresh]);
  useEffect(() => { const nav = tabsRef.current, button = nav?.querySelector<HTMLButtonElement>(".selected"); if (nav && button) nav.scrollTo({ left: Math.max(0, button.offsetLeft - nav.offsetLeft - 12), behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth" }); }, [tab]);
  const current = dataTab === tab ? data : null;
  return <section className="page admin-page"><div className="page-heading"><h2>管理工作台</h2></div><div className="admin-tabs" ref={tabsRef} aria-label="管理模块">{tabs.map(item => <button aria-pressed={tab === item} className={tab === item ? "selected" : ""} key={item} onClick={() => selectTab(item)}>{item}</button>)}</div>
    {tab === "概览" ? <AdminOverview onNavigate={selectTab} isPrimaryAdministrator={isPrimaryAdministrator} /> : tab === "导入中心" ? <ImportCenter /> : tab === "审计日志" ? <AuditPage /> : tab === "微信同步" && isPrimaryAdministrator ? <WechatRebuildPanel /> : tab === "排班规划" && isPrimaryAdministrator ? <SchedulePlanningPage /> : tab === "数据访问控制" && isPrimaryAdministrator ? <ClientPolicyPage /> : current?.error ? <div className="error-banner" role="alert">{String(current.error)}<button className="secondary" onClick={() => setRefresh(value => value + 1)}>重试</button></div> : <AdminTabContent key={tab} tab={tab} data={current} onNavigate={selectTab} isPrimaryAdministrator={isPrimaryAdministrator} />}
  </section>;
}

function AdminTabContent({ tab, data, onNavigate, isPrimaryAdministrator }: { tab: string; data: any; onNavigate: (tab: string) => void; isPrimaryAdministrator: boolean }) {
  if (!data) return <div className="data-panel"><div className="loading-line"><span className="spinner" />正在加载</div></div>;
  return tab === "车辆档案" ? <AdminVehicles data={data} /> : tab === "账号管理" ? <AdminUsers data={data} isPrimaryAdministrator={isPrimaryAdministrator} /> : null;
}

function AdminOverview({ onNavigate, isPrimaryAdministrator }: { onNavigate: (tab: string) => void; isPrimaryAdministrator: boolean }) {
  const icons: Record<string, React.ReactNode> = { "车辆档案": <ShieldCheck size={19} />, "账号管理": <Users size={19} />, "导入中心": <FileText size={19} />, "审计日志": <ShieldCheck size={19} />, "排班规划": <CalendarDays size={19} />, "微信同步": <RefreshCw size={19} />, "数据访问控制": <ShieldCheck size={19} /> };
  return <div className="admin-content"><div className="admin-module-grid">{visibleAdminModules(isPrimaryAdministrator).map(module => <button className={`admin-module-card ${module.tone}`} key={module.title} onClick={() => onNavigate(module.title)}><span className="admin-module-icon">{icons[module.title]}</span><span className="admin-module-arrow"><ArrowRight size={18} /></span><strong>{module.title}</strong></button>)}</div></div>;
}
function StatusLine({ label, value, ok }: { label: string; value: string; ok: boolean }) { return <div className="status-line"><span>{label}</span><strong className={ok ? "ok" : "muted"}>{value}</strong></div>; }
function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="vehicle-form-field"><span>{label}</span>{children}</label>; }
function FormSection({ title, hint, children }: { title: string; hint: string; children: React.ReactNode }) { return <section className="vehicle-form-section"><div className="vehicle-form-section-heading"><strong>{title}</strong><span>{hint}</span></div><div className="vehicle-form-grid">{children}</div></section>; }
function AdminVehicles({ data }: { data: any }) {
  const items = Array.isArray(data.items) ? data.items : [];
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [selected, setSelected] = useState<Record<string, unknown> | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [form, setForm] = useState({ id: "", version: "", plateNumber: "", category: "RESIDENT", vehicleType: "", status: "ACTIVE", ownerName: "", identityCardNumber: "", contactPhone: "", organizationName: "", passHolder: "", passageDetails: "", vehicleUse: "", passageArea: "", location: "", brandModel: "", approvedCapacity: "", plateColor: "", remarks: "" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [menuId, setMenuId] = useState<number | null>(null);
  const [remoteItems, setRemoteItems] = useState<any[]>(items);
  const [remoteTotal, setRemoteTotal] = useState<number>(data.total ?? items.length);
  const [loadingMore, setLoadingMore] = useState(false);
  const loadMoreRef = useRef<HTMLDivElement | null>(null);
  const queryRef = useRef(query);
  const filterRef = useRef(filter);
  const loadingMoreRef = useRef(false);
  const totalRef = useRef(remoteTotal);
  const [capabilities, setCapabilities] = useState<{ creatableCategories: string[]; canChangeVehicleCategory: boolean }>({ creatableCategories: ["OTHER_LONG_TERM"], canChangeVehicleCategory: false });
  const requestSequence = useRef(0);
  const previousSearch = useRef(JSON.stringify(["", "ALL"]));
  const paginationRequest = useRef<AbortController | null>(null);
  useEffect(() => () => paginationRequest.current?.abort(), []);
  useEffect(() => { queryRef.current = query; filterRef.current = filter; totalRef.current = remoteTotal; loadingMoreRef.current = loadingMore; }, [query, filter, remoteTotal, loadingMore]);
  useEffect(() => { const controller = new AbortController(); getAdminVehicleCapabilities(controller.signal).then(value => { if (!controller.signal.aborted) setCapabilities(value); }).catch(() => undefined); return () => controller.abort(); }, []);
  useEffect(() => { if (!form.id && !capabilities.canChangeVehicleCategory) setForm(current => ({ ...current, category: capabilities.creatableCategories[0] || "OTHER_LONG_TERM" })); }, [capabilities, form.id]);
  useEffect(() => { setRemoteItems(items); setRemoteTotal(data.total ?? items.length); }, [data]);
  useEffect(() => {
    const key = JSON.stringify([query, filter]);
    // 首屏已由父级读取，仅在用户改变查询条件后重新检索。
    if (previousSearch.current === key) return;
    previousSearch.current = key;
    paginationRequest.current?.abort();
    const controller = new AbortController();
    const sequence = ++requestSequence.current;
    const timer = window.setTimeout(() => {
      getAdminVehicles(query, filter === "ALL" ? "" : filter, 50, 0, controller.signal).then(value => {
        if (sequence !== requestSequence.current) return;
        const next = adaptAdminList(value);
        setRemoteItems(next.items);
        setRemoteTotal(next.total ?? next.items.length);
      }).catch(error => {
        if (!controller.signal.aborted && sequence === requestSequence.current) setMessage(error instanceof Error ? error.message : "车辆搜索失败");
      });
    }, 250);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [query, filter]);
  useEffect(() => {
    const node = loadMoreRef.current;
    if (!node) return;
    const observer = new IntersectionObserver(entries => {
      if (!entries[0]?.isIntersecting || loadingMoreRef.current) return;
      const currentItems = remoteItems.length;
      if (currentItems >= totalRef.current) return;
      const currentQuery = queryRef.current;
      const currentFilter = filterRef.current === "ALL" ? "" : filterRef.current;
      loadingMoreRef.current = true;
      setLoadingMore(true);
      const controller = new AbortController();
      paginationRequest.current = controller;
      getAdminVehicles(currentQuery, currentFilter, 50, currentItems, controller.signal).then(value => {
        if (controller.signal.aborted) return;
        if (currentQuery !== queryRef.current || currentFilter !== (filterRef.current === "ALL" ? "" : filterRef.current)) return;
        const next = adaptAdminList(value);
        setRemoteItems(previous => [...previous, ...next.items]);
        setRemoteTotal(next.total ?? totalRef.current);
      }).catch(error => { if (!controller.signal.aborted) setMessage(error instanceof Error ? error.message : "车辆列表加载失败"); }).finally(() => { loadingMoreRef.current = false; setLoadingMore(false); });
    }, { rootMargin: "280px" });
    observer.observe(node);
    return () => observer.disconnect();
  }, [remoteItems.length]);
  const visible = remoteItems;
  const reset = () => { setSelected(null); setEditorOpen(false); setForm({ id: "", version: "", plateNumber: "", category: "RESIDENT", vehicleType: "", status: "ACTIVE", ownerName: "", identityCardNumber: "", contactPhone: "", organizationName: "", passHolder: "", passageDetails: "", vehicleUse: "", passageArea: "", location: "", brandModel: "", approvedCapacity: "", plateColor: "", remarks: "" }); setMessage(""); };
  const edit = async (item: any) => {
    setBusy(true); setMessage(""); setEditorOpen(true);
    try { const detail = await getAdminVehicle(Number(item.id)); const resident = (detail.residentProfile || {}) as Record<string, unknown>; const longTerm = (detail.longTermProfile || {}) as Record<string, unknown>; const attributes = (detail.attributes || {}) as Record<string, unknown>; setSelected(detail); setForm({ id: String(detail.id || item.id), version: String(detail.version ?? item.version ?? ""), plateNumber: String(detail.plateNumber || ""), category: String(detail.category || "RESIDENT"), vehicleType: String(detail.vehicleType || ""), status: String(detail.status || "ACTIVE"), ownerName: String(resident.ownerName || ""), identityCardNumber: String(resident.identityCardNumber || ""), contactPhone: String(resident.contactPhone || ""), organizationName: String(longTerm.organizationName || ""), passHolder: String(longTerm.passHolder || ""), passageDetails: String(longTerm.passageDetails || ""), vehicleUse: String(attributes.vehicleUse || ""), passageArea: String(attributes.passageArea || attributes.accessArea || ""), location: String(attributes.position ?? attributes.location ?? attributes.所属位置 ?? ""), brandModel: String(attributes.brandModel || ""), approvedCapacity: String(attributes.approvedCapacity || ""), plateColor: String(attributes.plateColor || ""), remarks: String((detail.category === "RESIDENT" ? resident.remarks : longTerm.remarks) ?? attributes.remarks ?? "") }); }
    catch (error) { setMessage(error instanceof Error ? error.message : "车辆详情加载失败"); }
    finally { setBusy(false); }
  };
  const save = async () => {
    if (!form.plateNumber.trim()) { setMessage("请输入车牌号"); notify("error", "请输入车牌号"); return; }
    setBusy(true); setMessage("");
    try {
      const payload = buildVehicleCommand(form);
      if (form.id) await updateAdminVehicle(Number(form.id), payload, Number(form.version)); else await createAdminVehicle(payload);
      setMessage(form.id ? "车辆档案已更新" : "车辆档案已创建"); reset();
      window.location.reload();
    } catch (error) { const text = error instanceof Error ? error.message : "保存失败"; setMessage(text); notify("error", text); }
    finally { setBusy(false); }
  };
  const updateStatus = async (status: string) => {
    if (!form.id) return;
    setBusy(true); setMessage("");
    try { await updateAdminVehicleStatus(Number(form.id), status, Number(form.version)); setForm(current => ({ ...current, status })); setMessage("车辆状态已更新"); window.location.reload(); }
    catch (error) { setMessage(error instanceof Error ? error.message : "状态更新失败"); }
    finally { setBusy(false); }
  };
  const updateStatusForItem = async (item: any, status: string) => {
    setBusy(true); setMessage("");
    try { await updateAdminVehicleStatus(Number(item.id), status, Number(item.version)); setRemoteItems(current => current.map(value => Number(value.id) === Number(item.id) ? { ...value, status } : value)); setMessage("车辆状态已更新"); }
    catch (error) { setMessage(error instanceof Error ? error.message : "状态更新失败"); }
    finally { setBusy(false); }
  };
  const actionItems = (item: any): Array<[string, string, string]> => {
    const status = String(item.status || "ACTIVE");
    const actions: Array<[string, string, string]> = [["编辑档案", "edit", ""]];
    if (status !== "ACTIVE") actions.push(["启用档案", "status", "ACTIVE"]);
    if (status !== "STRICT_CHECK") actions.push(["标记严查", "status", "STRICT_CHECK"]);
    if (status !== "BLACKLISTED") actions.push(["拉黑车辆", "status", "BLACKLISTED"]);
    if (status !== "DELETED") actions.push(["删除车辆", "status", "DELETED"]);
    return actions;
  };
  return <div className="admin-content vehicle-workspace">
    {editorOpen && <div className="vehicle-editor-backdrop" onClick={event => { if (event.target === event.currentTarget) reset(); }}><GlassCard className="vehicle-editor-card"><div className="vehicle-editor-heading"><div><h3>{form.id ? "编辑车辆" : "新增车辆"}</h3><span>{form.id ? "维护车辆档案" : "创建车辆档案"}</span></div><div className="vehicle-editor-heading-actions"><strong>{data.total ?? items.length} 辆档案</strong><button type="button" className="vehicle-editor-close" onClick={reset} aria-label="关闭车辆表单"><X size={18} /></button></div></div>
      <div className="vehicle-form-sections">
        <FormSection title="车辆信息" hint="车牌与分类"><Field label="车牌号码"><input value={form.plateNumber} onChange={event => setForm({ ...form, plateNumber: event.target.value })} placeholder="请输入车牌号" /></Field><Field label="车辆型号"><input value={form.vehicleType} onChange={event => setForm({ ...form, vehicleType: event.target.value })} placeholder="" /></Field><Field label="所属类别"><select value={form.category} disabled={!capabilities.canChangeVehicleCategory} onChange={event => setForm({ ...form, category: event.target.value })}>{(capabilities.canChangeVehicleCategory ? ["RESIDENT", "SCENIC_UNIT", "SCENIC_ENTERPRISE", "KANAS_TOURISM_DEVELOPMENT", "CADRE", "OTHER_LONG_TERM"] : capabilities.creatableCategories).map(category => <option value={category} key={category}>{labels[category] || category}</option>)}</select></Field><Field label="当前状态"><select value={form.status} onChange={event => setForm({ ...form, status: event.target.value })}><option value="ACTIVE">正常</option><option value="STRICT_CHECK">严查</option><option value="BLACKLISTED">拉黑</option><option value="INACTIVE">停用</option><option value="DELETED">删除</option></select></Field></FormSection>
        {form.category === "RESIDENT" && <FormSection title="身份核验" hint="用于核对村民车辆归属"><Field label="姓名"><input value={form.ownerName} onChange={event => setForm({ ...form, ownerName: event.target.value })} placeholder="姓名" /></Field><Field label="身份证号"><input value={form.identityCardNumber} onChange={event => setForm({ ...form, identityCardNumber: event.target.value })} placeholder="身份证号" /></Field><Field label="手机号"><input value={form.contactPhone} onChange={event => setForm({ ...form, contactPhone: event.target.value })} placeholder="手机号" /></Field></FormSection>}
        {form.category !== "RESIDENT" && <FormSection title="单位与通行" hint="用于核对长期通行车辆"><Field label="单位名称"><input value={form.organizationName} onChange={event => setForm({ ...form, organizationName: event.target.value })} placeholder="单位名称" /></Field><Field label="通行持有人"><input value={form.passHolder} onChange={event => setForm({ ...form, passHolder: event.target.value })} placeholder="通行持有人" /></Field><Field label="通行事由"><input value={form.passageDetails} onChange={event => setForm({ ...form, passageDetails: event.target.value })} placeholder="通行事由" /></Field></FormSection>}
        <FormSection title="补充资料" hint="方便现场核对和通行放行"><Field label="车辆用途"><input value={form.vehicleUse} onChange={event => setForm({ ...form, vehicleUse: event.target.value })} placeholder="车辆用途" /></Field><Field label="通行区域"><input value={form.passageArea} onChange={event => setForm({ ...form, passageArea: event.target.value })} placeholder="通行区域" /></Field><Field label="所属位置"><input value={form.location} onChange={event => setForm({ ...form, location: event.target.value })} placeholder="所属位置" /></Field><Field label="品牌型号"><input value={form.brandModel} onChange={event => setForm({ ...form, brandModel: event.target.value })} placeholder="品牌型号" /></Field><Field label="核定载客数"><input value={form.approvedCapacity} onChange={event => setForm({ ...form, approvedCapacity: event.target.value })} placeholder="核定载客数" /></Field><Field label="车牌颜色"><input value={form.plateColor} onChange={event => setForm({ ...form, plateColor: event.target.value })} placeholder="车牌颜色" /></Field><Field label="备注"><textarea value={form.remarks} onChange={event => setForm({ ...form, remarks: event.target.value })} placeholder="备注" /></Field></FormSection>
      </div>
      <div className="vehicle-editor-actions"><button className="secondary" onClick={reset}>清空</button><button className="primary" onClick={() => void save()} disabled={busy}>{form.id ? "保存编辑" : "新增车辆"}</button></div>{message && <div className="admin-form-message">{message}</div>}
    </GlassCard></div>}
    <div className="vehicle-list-toolbar"><div className="vehicle-total-row"><strong>车辆档案</strong><span>共 {remoteTotal} 辆</span></div><button className="primary vehicle-add-button" onClick={() => { setForm({ id: "", version: "", plateNumber: "", category: capabilities.creatableCategories[0] || "OTHER_LONG_TERM", vehicleType: "", status: "ACTIVE", ownerName: "", identityCardNumber: "", contactPhone: "", organizationName: "", passHolder: "", passageDetails: "", vehicleUse: "", passageArea: "", location: "", brandModel: "", approvedCapacity: "", plateColor: "", remarks: "" }); setMessage(""); setEditorOpen(true); }}>+ 新增车辆</button><div className="vehicle-search"><Search size={18} /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="按车牌号检索车辆档案" /></div><div className="vehicle-filter-chips">{[["ALL", "全部"], ["ACTIVE", "启用"], ["STRICT_CHECK", "严查"], ["BLACKLISTED", "拉黑"], ["INACTIVE", "失效"], ["DELETED", "删除"]].map(([value, label]) => <button type="button" key={value} className={filter === value ? "selected" : ""} onClick={() => { setQuery(""); setFilter(value); }}>{label}</button>)}</div></div>
    <div className="vehicle-card-list">{visible.map((item: any) => <div className="vehicle-admin-card" key={String(item.id)} role="button" tabIndex={0} onClick={() => void edit(item)} onKeyDown={event => { if (event.key === "Enter") void edit(item); }}><Plate plate={String(item.plateNumber || "未知车牌")} /><span className="vehicle-admin-copy"><strong>{item.categoryLabel || labels[item.category] || item.category || "车辆档案"}</strong>{typeof item.vehicleType === "string" && item.vehicleType.trim() && <small>{item.vehicleType.trim()}</small>}</span><Status status={String(item.status || "ACTIVE")} /><span className="vehicle-card-actions"><button type="button" className="vehicle-card-menu" aria-label={`车辆操作 ${item.plateNumber || ""}`} onClick={event => { event.stopPropagation(); setMenuId(menuId === Number(item.id) ? null : Number(item.id)); }}>⋮</button>{menuId === Number(item.id) && <span className="vehicle-action-menu" onClick={event => event.stopPropagation()}>{actionItems(item).map(([label, kind, value]) => <button type="button" className={value === "DELETED" ? "danger-text" : ""} key={label} onClick={() => { setMenuId(null); if (kind === "edit") void edit(item); else void updateStatusForItem(item, value); }}>{label}</button>)}</span>}</span><ChevronRight size={18} /></div>)}{!visible.length && <div className="empty-state compact"><h3>没有匹配车辆</h3></div>}<div ref={loadMoreRef} className="vehicle-load-more">{loadingMore ? "正在加载更多" : remoteItems.length < remoteTotal ? "继续下滑加载" : "已加载全部档案"}</div></div>
  </div>;
}
function AdminUsers({ data, isPrimaryAdministrator }: { data: any; isPrimaryAdministrator: boolean }) {
  const items = Array.isArray(data.items) ? data.items : [];
  const [editor, setEditor] = useState<any | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const primary = Boolean(editor?.id) && (editor.isPrimaryAdministrator === true || canAccessPrimaryAdminModules(editor));
  const readOnly = primary && !isPrimaryAdministrator;
  useEffect(() => {
    if (!editor) return;
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === "Escape" && !busy) setEditor(null); };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [Boolean(editor), busy]);
  const openNew = () => {
    setMessage("");
    setEditor({ id: "", version: "", username: "", realName: "", password: "", role: "USER", status: "ACTIVE", scheduleEnabled: false, updatePolicy: false, otherLongTermAccessEnabled: false, residentRemarksAccessEnabled: false, wechatWorkOrderAccessEnabled: false });
  };
  const openEdit = (item: any) => {
    setMessage("");
    setEditor({ id: String(item.id), version: String(item.version ?? 0), username: String(item.username || ""), realName: String(item.realName || ""), password: "", role: String(item.role || "USER"), status: String(item.status || "ACTIVE"), scheduleEnabled: Boolean(item.scheduleEnabled), updatePolicy: Boolean(item.updatePolicy), otherLongTermAccessEnabled: Boolean(item.otherLongTermAccessEnabled), residentRemarksAccessEnabled: Boolean(item.residentRemarksAccessEnabled), wechatWorkOrderAccessEnabled: Boolean(item.wechatWorkOrderAccessEnabled), isPrimaryAdministrator: item.isPrimaryAdministrator === true });
  };
  const save = async () => {
    if (readOnly || busy) return;
    if (!editor.username.trim()) { setMessage("请输入账号"); notify("error", "请输入账号"); return; }
    if (!editor.id && !editor.password) { setMessage("新增账号必须设置密码"); notify("error", "新增账号必须设置密码"); return; }
    setBusy(true); setMessage("");
    try {
      if (editor.id) await updateAdminUser(Number(editor.id), {
        role: editor.role,
        status: editor.status,
        ...(isPrimaryAdministrator ? { username: editor.username.trim(), password: editor.password || null } : {}),
        ...(isPrimaryAdministrator && !primary ? {
          otherLongTermAccessEnabled: Boolean(editor.otherLongTermAccessEnabled),
          residentRemarksAccessEnabled: Boolean(editor.residentRemarksAccessEnabled),
          wechatWorkOrderAccessEnabled: Boolean(editor.wechatWorkOrderAccessEnabled),
        } : {}),
      }, Number(editor.version));
      else {
        const created = await createAdminUser({ username: editor.username.trim(), password: editor.password, role: editor.role }) as { id: number; version: number };
        if (editor.status === "DISABLED") {
          setEditor({ ...editor, id: String(created.id), version: String(created.version), password: "" });
          try { await updateAdminUser(created.id, { role: editor.role, status: editor.status }, created.version); }
          catch (error) { const text = `账号已创建，但停用失败：${error instanceof Error ? error.message : "请重试保存"}`; setMessage(text); notify("error", text); return; }
        }
      }
      setEditor(null); window.location.reload();
    } catch (error) { const text = error instanceof Error ? error.message : "保存失败"; setMessage(text); notify("error", text); }
    finally { setBusy(false); }
  };
  const toggle = (label: string, key: string) => <label className="account-toggle"><span><strong>{label}</strong><small>{key === "otherLongTermAccessEnabled" ? "允许查询和缓存其他长期通行车辆" : key === "residentRemarksAccessEnabled" ? "允许查看和缓存村民车辆备注" : "允许查询微信群车单、人员信息和相关图片"}</small></span><input type="checkbox" aria-label={label} disabled={busy || primary} checked={Boolean(editor[key])} onChange={event => setEditor({ ...editor, [key]: event.target.checked })} /></label>;
  return <div className="admin-content">
    <div className="account-list-heading"><h3>账号管理</h3><span>系统账号 {data.total ?? items.length} 个</span><button className="primary account-add-button" onClick={openNew}>+ 新增账号</button></div>
    <div className="admin-list">{items.map((item: any) => <div className="admin-user-row" key={String(item.id)} role="button" tabIndex={0} onClick={() => openEdit(item)} onKeyDown={event => { if (event.key === "Enter") openEdit(item); }}><AdminUserAvatar user={item} /><div className="admin-list-copy"><strong>{item.username}</strong><small>{item.username} · {item.role === "ADMIN" ? "管理员" : "核验用户"}</small><div className="permission-tags"><span className={item.status === "ACTIVE" ? "tag good" : "tag muted"}>{item.status === "ACTIVE" ? "已启用" : item.status || "未知状态"}</span></div></div>{<button className="admin-user-edit" type="button" onClick={event => { event.stopPropagation(); openEdit(item); }} aria-label={`编辑账号 ${item.username}`}>✎</button>}<span className="chevron">›</span></div>)}</div>
    {editor && <div className="vehicle-editor-backdrop" onClick={event => { if (!busy && event.target === event.currentTarget) setEditor(null); }}><GlassCard className="account-editor-card" role="dialog" aria-modal="true" aria-label={editor.id ? "维护账号信息" : "创建新账号"} aria-busy={busy}>
      {!editor.id && <div className="account-create-heading"><h2>创建新账号</h2><p>角色和启用状态会立即生效</p></div>}
      <div className="account-editor-hero"><div className="avatar">{editor.id ? editor.username.slice(0, 1).toUpperCase() || "?" : <Users size={21} />}</div><div><strong>正在编辑</strong><span>{editor.username || "待创建账号"}</span></div><button type="button" className="vehicle-editor-close" disabled={busy} onClick={() => setEditor(null)} aria-label="关闭账号表单"><X size={18} /></button></div>
      {((isPrimaryAdministrator && !readOnly) || !editor.id) && <><div className="account-form-section"><strong>{editor.id ? "账号资料" : "账号凭据"}</strong><span>{editor.id ? "修改后目标账号需要重新登录" : "创建后请妥善保存登录密码"}</span></div><div className="account-editor-fields">{editor.id ? <><label>用户名<input disabled={primary} value={editor.username} onChange={event => setEditor({ ...editor, username: event.target.value })} /></label><label>真实姓名<input value={editor.realName} onChange={event => setEditor({ ...editor, realName: event.target.value })} /></label><label>新登录密码<input type="password" value={editor.password} onChange={event => setEditor({ ...editor, password: event.target.value })} placeholder="留空表示不修改" /></label>{toggle("其他长期通行车辆", "otherLongTermAccessEnabled")}{toggle("村民车辆备注", "residentRemarksAccessEnabled")}{toggle("微信车单数据", "wechatWorkOrderAccessEnabled")}</> : <><label className="account-credential"><span className="sr-only">用户名</span><input aria-label="用户名" autoComplete="username" placeholder="用户名" value={editor.username} onChange={event => setEditor({ ...editor, username: event.target.value })} /></label><label className="account-credential"><span className="sr-only">登录密码</span><input aria-label="登录密码" autoComplete="new-password" type="password" placeholder="登录密码" value={editor.password} onChange={event => setEditor({ ...editor, password: event.target.value })} /></label></>}</div></>}
      {<><div className="account-form-section"><strong>访问权限</strong><span>决定可访问的管理范围</span></div><strong className="account-choice-label">分配角色</strong><div className="account-choice-row"><button disabled={primary} className={editor.role === "USER" ? "selected" : ""} onClick={() => setEditor({ ...editor, role: "USER" })}>普通用户</button><button disabled={primary} className={editor.role === "ADMIN" ? "selected" : ""} onClick={() => setEditor({ ...editor, role: "ADMIN" })}>管理员</button></div></>}
      <div className="account-form-section"><strong>账号状态</strong><span>状态修改会立即生效</span></div><div className="account-choice-row"><button disabled={primary} className={editor.status === "ACTIVE" ? "selected" : ""} onClick={() => setEditor({ ...editor, status: "ACTIVE" })}>启用</button><button disabled={primary} className={editor.status === "DISABLED" ? "selected" : ""} onClick={() => setEditor({ ...editor, status: "DISABLED" })}>停用</button></div>
      {message && <div className="admin-form-message" role="alert">{message}</div>}<div className="vehicle-editor-actions"><button className="secondary" disabled={busy} onClick={() => setEditor(null)}>取消</button><button className="primary" onClick={() => void save()} disabled={busy || readOnly}>{busy ? "正在保存" : editor.id ? "保存账号" : "创建账号"}</button></div>
    </GlassCard></div>}
  </div>;
}
function WechatRebuildPanel() {
  const [current, setCurrent] = useState<any>(null);
  const [backups, setBackups] = useState<unknown[]>([]);
  const [busy, setBusy] = useState(false), [message, setMessage] = useState("");
  const refreshController = useRef<AbortController | null>(null);
  const refresh = async () => {
    refreshController.current?.abort();
    const controller = new AbortController();
    refreshController.current = controller;
    setBusy(true); setMessage("");
    try {
      const [run, availableBackups] = await Promise.allSettled([getRebuild(controller.signal), getBackups(controller.signal)]);
      if (controller.signal.aborted) return;
      if (run.status === "fulfilled") setCurrent(run.value);
      if (availableBackups.status === "fulfilled") setBackups(Array.isArray(availableBackups.value) ? availableBackups.value : []);
      const failures = [run, availableBackups].filter(item => item.status === "rejected");
      if (failures.length) setMessage(failures.map(item => item.status === "rejected" && item.reason instanceof Error ? item.reason.message : "加载失败").join("；"));
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };
  useEffect(() => { void refresh(); return () => refreshController.current?.abort(); }, []);
  const run = async (action: () => Promise<unknown>, success: string) => { setBusy(true); setMessage(""); try { const result = await action(); setCurrent(result); setMessage(success); } catch (error) { setMessage(error instanceof Error ? error.message : "操作失败"); } finally { setBusy(false); } };
  const runId = current?.runId as string | undefined;
  const confirm = current?.confirmation || "CONFIRM";
  return <div className="rebuild-panel"><div className="rebuild-summary"><div><small>当前状态</small><strong>{rebuildStateLabel(current?.status)}</strong></div><div><small>批次编号</small><code>{runId || "尚未创建"}</code></div><button className="secondary" onClick={() => void refresh()} disabled={busy}>刷新状态</button></div>{message && <div className="notice-banner" role="alert">{message}</div>}<div className="admin-actions"><button className="primary" onClick={() => void run(previewRebuild, "已生成预览，请继续锁定并校验备份")} disabled={busy}>预览重构范围</button>{runId && <><button className="secondary" onClick={() => void run(() => lockRebuild(runId, confirm), "已暂停微信采集上传")} disabled={busy || current?.status !== "PREVIEW"}>暂停上传</button><button className="secondary" onClick={() => void run(() => verifyRebuildBackup(runId), "备份校验完成")} disabled={busy || !["LOCKED", "BACKUP_VERIFYING"].includes(current?.status)}>校验备份</button><button className="danger" onClick={() => { if (window.confirm("确认清空服务器微信业务数据并删除附件记录吗？")) void run(() => cleanRebuild(runId, confirm), "清理完成，等待采集器全量同步"); }} disabled={busy || current?.status !== "LOCKED"}>开始清理</button><button className="secondary" onClick={() => void run(() => verifyRebuild(runId), "重构验证已提交")} disabled={busy || !["REBUILDING", "VERIFYING", "FAILED"].includes(current?.status)}>重新验证</button><button className="secondary" onClick={() => void run(() => unlockRebuild(runId, false), "维护锁已取消")} disabled={busy || ["COMPLETED", "CANCELLED"].includes(current?.status)}>取消维护</button></>}</div><RebuildMetrics current={current} /><div className="backup-list"><h3>最近备份</h3>{backups.length ? backups.slice(0, 3).map((backup: any) => <div className="backup-row" key={String(backup.id || backup.path)}><span><strong>{backup.createdAt || backup.timestamp || "未知时间"}</strong><small>{backup.sizeBytes ? `${Math.round(backup.sizeBytes / 1024 / 1024)} MB` : backup.path || "服务器备份"}</small></span><button className="secondary" disabled={busy} onClick={() => { if (window.confirm("恢复备份会替换当前数据库状态，确认继续吗？")) void run(() => restoreBackup(String(backup.id || backup.path)), "恢复请求已提交"); }}>恢复</button></div>) : <p className="admin-note">当前没有可用备份。第一次重构会在清理前自动生成。</p>}</div></div>;
}
function RebuildMetrics({ current }: { current: any }) { if (!current) return <div className="data-panel"><p className="admin-note">首次使用时没有备份文件是正常的，预览不会要求备份已存在；开始清理前服务端会自动生成并验证备份。</p></div>; const preview = current.preview || {}; const deleted = current.deleted || current.deleteStats || {}; const uploads = current.uploadProgress || current.progress || {}; return <div className="rebuild-metrics"><div className="metric-grid"><Metric label="待清理消息" value={String(preview.wechatMessages ?? preview.messageCount ?? deleted.wechatMessages ?? "—")} tone="green" /><Metric label="待清理车单" value={String(preview.workOrders ?? preview.workOrderCount ?? deleted.workOrders ?? "—")} tone="teal" /><Metric label="附件记录" value={String(preview.attachments ?? preview.attachmentCount ?? deleted.attachments ?? "—")} tone="amber" /></div><div className="status-line"><span>全量同步进度</span><strong className="ok">{uploads.completed != null && uploads.total ? `${uploads.completed}/${uploads.total}` : uploads.percent != null ? `${uploads.percent}%` : current.status === "REBUILDING" ? "等待采集器上传" : "尚未开始"}</strong></div></div>; }

function DetailView({ detail, onBack }: { detail: VehicleDetail; onBack: () => void }) { const resident = (detail.residentProfile || {}) as Record<string, unknown>; const longTerm = (detail.longTermProfile || {}) as Record<string, unknown>; const attributes = (detail.attributes || {}) as Record<string, unknown>; const fields = (value: Record<string, unknown>, map: Array<[string, string]>) => map.filter(([, key]) => value[key] !== null && value[key] !== undefined && value[key] !== "").map(([label, key]) => [label, String(value[key])] as [string, string]); const residentFields = fields(resident, [["车主姓名", "ownerName"], ["身份证号", "identityCardNumber"], ["联系电话", "contactPhone"], ["村民备注", "remarks"]]); const longTermFields = fields(longTerm, [["单位名称", "organizationName"], ["通行人员", "passHolder"], ["通行信息", "passageDetails"], ["备注", "remarks"]]); const vehicleFields = [["车辆类型", detail.vehicleType], ...Object.entries(attributes).map(([key, value]) => [key, value] as [string, unknown])].filter(([, value]) => value !== null && value !== undefined && value !== "").map(([key, value]) => [fieldLabel(String(key)), String(value)] as [string, string]); return <section className="page detail-page"><button className="back-button" onClick={onBack}><ArrowLeft size={16} /> 返回查询</button><div className="vehicle-identity-card"><div className="identity-overlay"><Plate plate={String(detail.plateNumber || "未知车牌")} /><strong>{String(detail.categoryLabel || labels[String(detail.category)] || "车辆档案")}</strong><span>{detail.status === "STRICT_CHECK" ? "严查 · 核实三证合一及车辆、人员信息" : detail.status === "BLACKLISTED" ? "已拉黑" : detail.status === "INACTIVE" ? "已停用（已失效）" : "通行档案核验信息"}</span></div></div>{detail.status !== "ACTIVE" && <div className={`risk-banner ${String(detail.status).toLowerCase()}`}><Status status={String(detail.status)} /><span>{detail.status === "STRICT_CHECK" ? "该车辆需严查后再放行" : detail.status === "BLACKLISTED" ? "该车辆档案已被管理员拉黑" : "该车辆档案已失效"}</span></div>}{residentFields.length > 0 && <DetailSection title="村民资料"><DetailFields fields={residentFields} /></DetailSection>}{longTermFields.length > 0 && <DetailSection title="长期通行资料"><DetailFields fields={longTermFields} /></DetailSection>}{vehicleFields.length > 0 && <DetailSection title="车辆信息"><DetailFields fields={vehicleFields} /></DetailSection>}</section>; }
function DetailFields({ fields }: { fields: Array<[string, string]> }) { return <div className="detail-fields">{fields.map(([label, value]) => <div className="detail-field" key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>; }
function DetailSection({ title, children }: { title: string; children: React.ReactNode }) { return <section className="detail-section"><div className="section-title"><h3>{title}</h3></div>{children}</section>; }
function AttachmentList({ items }: { items: any[] }) { return <div className="attachment-list">{items.map((item: any, index) => <AttachmentRow key={String(item.id || item.fileName || index)} item={item} index={index} />)}</div>; }
function AttachmentRow({ item, index }: { item: any; index: number }) { const [state, setState] = useState<"idle" | "loading" | "failed">("idle"); const [url, setUrl] = useState<string | null>(null); const name = String(item.fileName || item.name || `${item.kind === "PDF" ? "PDF" : "图片"}附件 ${index + 1}`); const kind = String(item.kind || item.attachmentKind || "IMAGE"); useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]); const open = async () => { if (url) { window.open(url, "_blank", "noopener,noreferrer"); return; } if (!item.id) return; setState("loading"); try { const blob = await downloadAttachment(Number(item.id), kind === "PDF" ? "original" : "preview"); const nextUrl = URL.createObjectURL(blob); setUrl(nextUrl); window.open(nextUrl, "_blank", "noopener,noreferrer"); } catch { setState("failed"); } }; return <button className="attachment-row" onClick={() => void open()} disabled={state === "loading"}><span className={`attachment-icon ${kind === "PDF" ? "pdf" : "image"}`}>{kind === "PDF" ? <FileText size={16} /> : <ImageIcon size={16} />}</span><span><strong>{name}</strong><small>{state === "loading" ? "正在加载附件" : state === "failed" ? "加载失败，点击重试" : item.status || item.availability || "点击查看附件"}</small></span><span className="chevron"><ChevronRight size={19} /></span></button>; }
function WorkOrderDetailView({ detail, onBack }: { detail: WorkOrder; onBack: () => void }) { const attachments = adaptAttachments(detail.attachments || detail.images); const people = (detail.people || []) as any[]; return <section className="page detail-page"><button className="back-button" onClick={onBack}><ArrowLeft size={16} /> 返回微信车单</button><div className="detail-hero workorder-hero"><div><span className="category-label">微信车单 · {detail.orderYear || "未知年份"}</span><h2>{detail.orderNumber || "未识别单号"}</h2><p>{detail.sourceName || "微信来源"} · {formatDate(detail.sentAt)}</p></div><span className="status valid">有效记录</span></div><DetailSection title="微信原始内容"><div className="long-content">{detail.rawContent || detail.title || "暂无正文"}</div></DetailSection><DetailSection title="微信来源"><div className="field-grid"><Info label="微信群" value={String(detail.sourceName || "未知")} /><Info label="发送者" value={String(detail.senderName || detail.sender || "未知")} /><Info label="发送时间" value={formatDate(detail.sentAt)} /></div></DetailSection><DetailSection title="通行信息"><div className="field-grid"><Info label="车牌" value={(detail.plateNumbers || []).join("、") || "正文中查看"} /><Info label="车型" value={String(detail.vehicleType || "未填写")} /><Info label="时间" value={String(detail.rawValidTime || detail.validTime || "未填写")} /><Info label="地点" value={String(detail.location || "未填写")} /><Info label="方式" value={String(detail.verificationMethod || "未填写")} /></div></DetailSection>{people.length > 0 && <DetailSection title="人员信息"><div className="info-list">{people.map((person: any, index) => <Info key={String(person.id || index)} label={String(person.name || person.realName || `人员 ${index + 1}`)} value={String(person.phone || person.identityCardNumber || "已登记")} />)}</div></DetailSection>}<DetailSection title="事由与备注"><div className="field-grid"><Info label="事由" value={String(detail.reason || "未填写")} /><Info label="备注" value={String(detail.remarks || "未填写")} /></div></DetailSection>{attachments.length > 0 && <DetailSection title="相关附件"><AttachmentList items={attachments} /></DetailSection>}</section>; }
function MessageDetailView({ detail, onBack }: { detail: WechatMessage; onBack: () => void }) { const attachments = (detail.attachments || detail.images || []) as any[]; const passageState = String(detail.passageState || detail.status || ""); return <section className="page detail-page"><button className="back-button" onClick={onBack}>← 返回聊天记录</button><div className="detail-hero message-hero"><div><span className="category-label">微信聊天记录</span><h2>{detail.sourceName || "微信消息"}</h2><p>{formatDate(detail.sentAt)}</p></div>{passageState && <Status status={passageState} />}</div><DetailSection title="微信原始内容"><div className="long-content">{detail.rawContent || "暂无正文"}</div></DetailSection><DetailSection title="微信来源"><div className="field-grid"><Info label="发送者" value={String(detail.senderName || detail.sender || "未知")} /><Info label="微信群" value={String(detail.sourceName || "未知")} /><Info label="发送时间" value={formatDate(detail.sentAt)} /></div></DetailSection>{detail.plateNumbers?.length ? <DetailSection title="识别到的车牌"><div className="plate-list">{detail.plateNumbers.map(plate => <Plate key={plate} plate={plate} />)}</div></DetailSection> : null}{passageState && <DetailSection title="通行状态"><Status status={passageState} /></DetailSection>}{attachments.length > 0 && <DetailSection title="相关附件"><AttachmentList items={attachments} /></DetailSection>}</section>; }

function ResultSection({ title, count, children }: { title: string; count: number; children: React.ReactNode }) { return <section className="result-section"><div className="section-title"><h3>{title}</h3><span>{count} 条</span></div>{children}</section>; }
function VehicleRow({ item, onClick }: { item: VehicleCandidate; onClick: () => void }) { const remember = () => { if (item.detailAccessible !== false) localStorage.setItem(`plateview.search.detail.${item.plateNumber}`, JSON.stringify(item)); onClick(); }; return <button className={item.detailAccessible === false ? "result-row restricted" : "result-row"} onClick={item.detailAccessible === false ? undefined : remember}><Plate plate={item.plateNumber} color={item.plateColor} /><span className="result-copy"><strong className={`vehicle-category ${categoryTone(item.category)}`}>{labels[item.category] || item.category}</strong><small>{item.primarySubject || "车辆档案"}</small></span><VehicleStatus status={item.status || "ACTIVE"} /><span className="chevron">›</span></button>; }
function WorkOrderRow({ item, query, onClick }: { item: WorkOrder; query?: string; onClick: () => void }) { const status = displayWorkOrderStatus(item); const plate = selectWechatCandidatePlate(workOrderPlates(item), query || ""); const summary = [item.rawValidTime || item.validTime, item.location, item.sourceName, item.remarks].filter(value => value !== null && value !== undefined && String(value).trim() !== "").map(value => String(value).trim()).join(" · "); return <button className="result-row workorder-result" onClick={onClick}>{plate ? <Plate plate={plate} color="BLUE" /> : <div className="mini-icon amber"><ClipboardList size={18} /></div>}<span className="result-copy"><strong>{item.orderNumber || item.title || "微信车单"} · {item.orderYear || "未知年份"}</strong><small>{summary || `${item.sourceName || "微信来源"} · ${formatDate(item.sentAt)}`}</small><small className="result-meta-time">{item.sourceName || "微信来源"} · {formatDate(item.sentAt)}</small></span><Status status={status} /><span className="chevron"><ChevronRight size={20} /></span></button>; }
function MessageRow({ item, query, onClick }: { item: WechatMessage; query?: string; onClick: () => void }) { const status = String(item.status || item.passageState || "ACTIVE"); const plate = selectWechatCandidatePlate(item.plateNumbers || extractPlates(item.rawContent), query || String(item.rawContent || "")); const sender = String(item.displayName || item.senderDisplay || item.senderGroupNickname || "微信消息"); const leader = leaderTone(item); return <button className={`result-row ${leader ? `leader-result ${leader}` : ""}`} onClick={onClick}>{plate ? <Plate plate={plate} color="BLUE" /> : <div className="mini-icon teal"><MessageCircle size={18} /></div>}<span className="result-copy"><strong className={leader ? `leader-sender ${leader}` : ""}>{sender}</strong><small>{item.sourceName || "微信来源"} · {formatDate(item.sentAt)}</small></span><Status status={status} /><span className="chevron"><ChevronRight size={20} /></span></button>; }
function Plate({ plate, color }: { plate: string; color?: unknown }) { return <span className={`plate-badge plate-${plateTone(color)}`}>{plate}</span>; }
function workOrderPlates(item: WorkOrder): string[] { if (Array.isArray(item.plateNumbers)) return item.plateNumbers.filter(Boolean); if (Array.isArray(item.vehicles)) return (item.vehicles as unknown[]).map(value => { const vehicle = value as Record<string, unknown>; return String(vehicle.rawPlate || vehicle.plateNumber || ""); }).filter(Boolean); return extractPlates(item.rawContent); }
function extractPlates(value: unknown): string[] { const matches = String(value || "").match(/[京津沪渝冀豫云辽黑湘皖鲁苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼新使领][A-HJ-NP-Z][A-HJ-NP-Z0-9]{5,6}/gi); return matches ? Array.from(new Set(matches)) : []; }
function leaderTone(item: WechatMessage): string { const sender = String(item.displayName || item.senderDisplay || item.senderGroupNickname || ""); if (/徐站|徐如军/.test(sender)) return "leader-xuzhan"; if (/三叔/.test(sender)) return "leader-sanshu"; if (/孙主任|孙主?任/.test(sender)) return "leader-sunzhu"; return item.businessType === "PASSAGE_MESSAGE" ? "leader-xuzhan" : ""; }
function isLeaderMessage(item: WechatMessage) { return Boolean(leaderTone(item)); }
function categoryTone(category: string) { return ({ RESIDENT: "resident", SCENIC_UNIT: "scenic-unit", SCENIC_ENTERPRISE: "scenic-enterprise", KANAS_TOURISM_DEVELOPMENT: "kanas", CADRE: "cadre", OTHER_LONG_TERM: "long-term" } as Record<string, string>)[category] || "default"; }
function plateTone(color: unknown) { const value = String(color || "").toUpperCase(); if (/黄|YELLOW/.test(value)) return "yellow"; if (/绿|GREEN/.test(value)) return "green"; if (/白|WHITE/.test(value)) return "white"; if (/黑|BLACK/.test(value)) return "black"; return "blue"; }
function Status({ status }: { status: string }) { const labelsStatus: Record<string, string> = { ACTIVE: "正常通行", BLACKLISTED: "拉黑", STRICT_CHECK: "严查", INACTIVE: "已停用", DELETED: "已删除", VALID: "通行时间有效", OUTSIDE_ALLOWED_HOURS: "不在通行时间", AREA_MISMATCH: "通行区域不符", AREA_UNKNOWN: "通行区域未知", EXPIRED: "通行时间已过期", NOT_STARTED: "通行时间未开始", UNKNOWN: "通行时间待定" }; const label = labelsStatus[status] || status; const tone = /不符|不在|过期|已停用|已删除|拉黑|失效/.test(label) ? "abnormal" : /未知|待定|未开始/.test(label) ? "pending" : "active"; return <span className={`status ${tone}`}>{label}</span>; }
function VehicleStatus({ status }: { status: string }) { const labelsStatus: Record<string, string> = { ACTIVE: "核验就绪", STRICT_CHECK: "严查", BLACKLISTED: "拉黑", INACTIVE: "已停用", DELETED: "已删除" }; const tone = status === "STRICT_CHECK" ? "warning" : status === "BLACKLISTED" || status === "INACTIVE" || status === "DELETED" ? "danger" : "ready"; return <span className={`vehicle-status ${tone}`}>{labelsStatus[status] || status}</span>; }
function workOrderPassageLabel(item: WorkOrder): string { const explicit = String(item.passageState || item.passageStatus || ""); if (explicit) return explicit; if (item.status && item.status !== "ACTIVE") return String(item.status); if (item.location && !String(item.location).includes("喀纳斯")) return "AREA_MISMATCH"; const raw = String(item.rawValidTime || ""); const match = raw.match(/(\d{1,2})[./-](\d{1,2})\s*[-~至]\s*(\d{1,2})[./-](\d{1,2})/); if (!match || !item.sentAt) return "VALID"; const now = new Date(); const year = Number(item.orderYear || now.getFullYear()); const end = new Date(year, Number(match[3]) - 1, Number(match[4])); const start = new Date(year, Number(match[1]) - 1, Number(match[2])); if (now < start) return "NOT_STARTED"; if (now > end) return "EXPIRED"; const remark = String(item.remarks || ""); const time = remark.match(/(\d{1,2}):(\d{2})\s*[-~至]\s*(\d{1,2}):(\d{2})/); if (time) { const minutes = now.getHours() * 60 + now.getMinutes(); const from = Number(time[1]) * 60 + Number(time[2]); const to = Number(time[3]) * 60 + Number(time[4]); if (minutes < from || minutes > to) return "OUTSIDE_ALLOWED_HOURS"; } return "VALID"; }
function displayWorkOrderStatus(item: WorkOrder): string { if (!String(item.passageState || item.passageStatus || "").trim() && !String(item.rawValidTime || item.validTime || "").trim()) return "UNKNOWN"; if (!String(item.passageState || item.passageStatus || "").trim() && !item.location) return "AREA_UNKNOWN"; return workOrderPassageLabel(item); }
function Metric({ label, value, tone }: { label: string; value: string; tone: string }) { return <div className={`metric ${tone}`}><small>{label}</small><strong>{value}</strong></div>; }
function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) { return <div className="empty-state compact error-state"><h3>暂时无法加载</h3><p>{message}</p><button className="secondary" onClick={onRetry}>重新加载</button></div>; }
function Info({ label, value }: { label: string; value: string }) { return <div className="info-row"><span>{label}</span><strong>{value || "—"}</strong></div>; }
function fieldLabel(key: string) { const map: Record<string, string> = { plateNumber: "车牌号", plateColor: "车牌颜色", category: "车辆类别", status: "状态", vehicleType: "车辆类型", vehicleUse: "车辆用途", passageArea: "通行区域", ownerName: "车主姓名", identityCardNumber: "身份证号", contactPhone: "联系电话", organizationName: "单位名称", passHolder: "通行人员", passageDetails: "通行信息", remarks: "备注", brandModel: "品牌型号", approvedCapacity: "核定载客数", approvedLoad: "核定载质量", vehicleColor: "车辆颜色", fuelType: "燃料类型", registrationDate: "登记日期", registrationNumber: "登记编号", department: "所属部门", usage: "使用用途", accessArea: "通行区域", accessTime: "通行时间" }; return map[key] || key; }
function formatDate(value?: string | null) { if (!value) return "暂无记录"; const date = new Date(value); if (Number.isNaN(date.getTime())) return value; return new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "short", timeZone: "Asia/Shanghai" }).format(date); }

createRoot(document.getElementById("root")!).render(<React.StrictMode><BrowserRouter basename="/web"><App /><OperationFeedback /></BrowserRouter></React.StrictMode>);

if ("serviceWorker" in navigator && window.location.protocol === "https:") {
  window.addEventListener("load", () => navigator.serviceWorker.register("/web/sw.js").catch(() => undefined));
}

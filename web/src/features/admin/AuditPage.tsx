import { useEffect, useRef, useState } from "react";
import { RefreshCw, ShieldCheck } from "../../components/icons";
import { getAudit } from "../../api";
import { adaptAudit, auditActionLabel, auditResultLabel, auditTargetLabel, auditTime, type AuditPageData } from "./audit";
import "./admin.css";

export function AuditPage() {
  const [range, setRange] = useState("24h"), [actor, setActor] = useState(""), [action, setAction] = useState(""), [result, setResult] = useState("");
  const [data, setData] = useState<AuditPageData>(() => adaptAudit(null));
  const [busy, setBusy] = useState(true), [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const sentinel = useRef<HTMLDivElement>(null);
  const loadMore = useRef<(() => void) | null>(null);
  useEffect(() => {
    let active = true, loading = false, offset = 0, total = 0, failed = false;
    const controller = new AbortController();
    setData(adaptAudit(null)); setError("");
    const load = async () => {
      if (!active || loading) return;
      loading = true; failed = false; setBusy(true); setError("");
      try {
        const next = adaptAudit(await getAudit({ range, actorId: actor ? Number(actor) : null, actionType: action, result, offset }, controller.signal));
        if (!active) return;
        const first = offset === 0;
        // 偏移按服务端返回条数推进，去重不能改变下一页的偏移。
        offset += next.items.length; total = next.total;
        if (!next.items.length) total = offset;
        setData(previous => ({ ...next, actors: [...new Map([...previous.actors, ...next.actors].map(item => [item.id, item])).values()], actionTypes: [...new Set([...previous.actionTypes, ...next.actionTypes])], items: [...new Map([...(first ? [] : previous.items), ...next.items].map(item => [item.id, item])).values()] }));
      } catch (reason) {
        if (active) { failed = true; setError(reason instanceof Error ? reason.message : "审计记录加载失败"); }
      } finally { loading = false; if (active) setBusy(false); }
    };
    loadMore.current = () => { if (!loading && (failed || offset < total)) void load(); };
    void load();
    const observer = new IntersectionObserver(entries => { if (entries[0]?.isIntersecting && !failed) loadMore.current?.(); }, { rootMargin: "160px" });
    if (sentinel.current) observer.observe(sentinel.current);
    return () => { active = false; controller.abort(); loadMore.current = null; observer.disconnect(); };
  }, [range, actor, action, result, revision]);
  const summary = data.summary;
  return <section className="audit-page" aria-label="审计日志">
    <header className="admin-feature-heading"><h3><ShieldCheck size={20} />操作审计</h3><span>{data.total} 条记录</span><button className="icon-button" aria-label="刷新审计日志" onClick={() => setRevision(value => value + 1)} disabled={busy}><RefreshCw size={18} /></button></header>
    <div className="audit-summary">{[["总记录", summary.total], ["正常", summary.successCount], ["异常", summary.abnormalCount], ["操作人", summary.activeActorCount]].map(([label, value]) => <div key={label} className={label === "异常" ? "abnormal" : ""}><span>{label}</span><strong>{value}</strong></div>)}</div>
    <div className="audit-filters" aria-label="审计时间范围">{[["24h", "近24小时"], ["7d", "近7天"], ["30d", "近30天"], ["all", "全部时间"]].map(([value, label]) => <button key={value} aria-pressed={range === value} className={range === value ? "selected" : ""} onClick={() => setRange(value)}>{label}</button>)}</div>
    <div className="audit-selectors"><select aria-label="操作人" value={actor} onChange={event => setActor(event.target.value)}><option value="">全部用户</option>{actor && !data.actors.some(item => String(item.id) === actor) && <option value={actor}>指定用户</option>}{data.actors.map(item => <option value={item.id} key={item.id}>{item.username}</option>)}</select><select aria-label="操作类型" value={action} onChange={event => setAction(event.target.value)}><option value="">全部操作</option>{[...new Set([...data.actionTypes, ...(action ? [action] : [])])].map(item => <option value={item} key={item}>{auditActionLabel(item)}</option>)}</select></div>
    <div className="audit-filters" aria-label="审计结果">{[["", "全部结果"], ["SUCCESS", "正常"], ["ABNORMAL", "异常"]].map(([value, label]) => <button key={value} aria-pressed={result === value} className={result === value ? "selected" : ""} onClick={() => setResult(value)}>{label}</button>)}</div>
    <div className="audit-entries" aria-busy={busy}>{data.items.map(item => <article key={item.id} className="audit-entry"><div className="audit-entry-meta"><span className={`audit-result ${item.resultStatus === "SUCCESS" ? "success" : "failure"}`}>{auditResultLabel(item.resultStatus)}</span><time dateTime={item.createdAt}>{auditTime(item.createdAt)}</time></div><strong>{auditActionLabel(item.actionType)}</strong><p>{item.actorUsername} · {auditTargetLabel(item.targetType)}{item.targetId !== null ? ` #${item.targetId}` : ""}</p></article>)}</div>
    {error && <div className="audit-error" role="alert"><span>{error}</span><button className="secondary" onClick={() => loadMore.current?.()}>重试</button></div>}
    {!busy && !error && !data.items.length && <div className="empty-state compact"><h3>暂无审计记录</h3></div>}
    <div ref={sentinel} className="audit-sentinel">{busy ? "正在加载审计记录" : !error && data.items.length < data.total ? <button className="secondary" onClick={() => loadMore.current?.()}>加载更多</button> : null}</div>
  </section>;
}

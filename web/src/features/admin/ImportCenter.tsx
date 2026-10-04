import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import * as Dialog from "@radix-ui/react-dialog";
import { ArrowLeft, CheckCircle2, Clock3, RefreshCw, Upload, X } from "lucide-react";
import { getImports, getImportBatch, getImportRowDetail, previewImport, resolveImportRows, publishImport, rollbackImport } from "../../api";
import { notify } from "../../state/feedback";
import { adaptImportBatch, adaptImportDetail, adaptImportList, canPublish, canResolve, confirmLabel, detailLabel, importFilters, importStatus, rowAction, skipLabel, type ImportBatch, type ImportDetail, type ImportFilter, type ImportSummary } from "./imports";
import "./imports.css";

const message = (error: unknown) => error instanceof Error ? error.message : "请求失败，请重试";
const date = (value: string) => { const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? "时间未记录" : new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium", timeZone: "Asia/Shanghai" }).format(parsed); };
const unique = <T extends { id: number }>(items: T[]) => [...new Map(items.map(item => [item.id, item])).values()];
const positiveId = (value: string | null) => value && /^\d+$/.test(value) && Number(value) > 0 ? Number(value) : 0;

export function ImportCenter() {
  const [params, setParams] = useSearchParams();
  const batchId = positiveId(params.get("importBatch")), rowId = positiveId(params.get("importRow"));
  const filter: ImportFilter = importFilters.find(([value]) => value === params.get("importFilter"))?.[0] || "REVIEW";
  const [items, setItems] = useState<ImportSummary[]>([]), [hasMore, setHasMore] = useState(false);
  const [listLoading, setListLoading] = useState(false), [listError, setListError] = useState("");
  const [batch, setBatch] = useState<ImportBatch | null>(null), [detail, setDetail] = useState<ImportDetail | null>(null);
  const [rowLoading, setRowLoading] = useState(false), [rowError, setRowError] = useState("");
  const [detailLoading, setDetailLoading] = useState(false), [detailError, setDetailError] = useState("");
  const [busy, setBusy] = useState(false), [actionError, setActionError] = useState("");
  const [listRevision, setListRevision] = useState(0), [revision, setRevision] = useState(0), [detailRevision, setDetailRevision] = useState(0);
  const [confirmation, setConfirmation] = useState<"publish" | "rollback" | null>(null);
  const fileInput = useRef<HTMLInputElement>(null), sentinel = useRef<HTMLDivElement>(null);
  const listLoad = useRef<() => void>(() => {}), rowLoad = useRef<() => void>(() => {});

  useEffect(() => { window.scrollTo({ top: 0, behavior: "instant" }); }, [batchId, rowId]);

  const navigate = (id: number, row = 0, nextFilter: ImportFilter = filter, replace = false) => {
    const next = new URLSearchParams(params);
    for (const key of ["importBatch", "importRow", "importFilter"]) next.delete(key);
    if (id) { next.set("importBatch", String(id)); next.set("importFilter", nextFilter); }
    if (row) next.set("importRow", String(row));
    setParams(next, { replace });
  };

  useEffect(() => {
    const controller = new AbortController(); let active = true, loading = false, offset = 0, more = true;
    const load = async () => {
      if (loading || !more || !active) return;
      loading = true; setListLoading(true); setListError("");
      try {
        const next = adaptImportList(await getImports(controller.signal, 50, offset));
        if (!active) return;
        const firstPage = offset === 0;
        setItems(current => firstPage ? next : unique([...current, ...next]));
        offset += next.length; more = next.length === 50; setHasMore(more);
      } catch (error) { if (active) setListError(message(error)); }
      finally { loading = false; if (active) setListLoading(false); }
    };
    listLoad.current = () => void load(); void load();
    return () => { active = false; controller.abort(); };
  }, [listRevision]);

  useEffect(() => {
    if (!batchId) { setBatch(null); return; }
    const controller = new AbortController(); let active = true, loading = false, offset = 0, more = true;
    setBatch(current => current?.id === batchId ? { ...current, rows: [], rowTotal: 0 } : null);
    setActionError("");
    const load = async () => {
      if (loading || !more || !active) return;
      loading = true; setRowLoading(true); setRowError("");
      try {
        const next = adaptImportBatch(await getImportBatch(batchId, filter, 50, offset, controller.signal));
        if (!active) return;
        if (next.id !== batchId) throw new Error("返回的导入批次不匹配，请重试");
        if (!next.rows.length && offset < next.rowTotal) throw new Error("导入分页返回不完整，请重试");
        const firstPage = offset === 0;
        setBatch(current => ({ ...next, rows: firstPage ? next.rows : unique([...(current?.rows || []), ...next.rows]) }));
        offset += next.rows.length; more = offset < next.rowTotal;
      } catch (error) { if (active) setRowError(message(error)); }
      finally { loading = false; if (active) setRowLoading(false); }
    };
    rowLoad.current = () => void load(); void load();
    return () => { active = false; controller.abort(); };
  }, [batchId, filter, revision]);

  useEffect(() => {
    setDetail(null); setDetailError("");
    if (!batchId || !rowId) return;
    const controller = new AbortController(); let active = true; setDetailLoading(true);
    getImportRowDetail(batchId, rowId, controller.signal).then(value => {
      if (!active) return;
      const next = adaptImportDetail(value);
      if (next.row.id !== rowId) throw new Error("返回的导入记录不匹配，请重试");
      setDetail(next);
    }).catch(error => { if (active) setDetailError(message(error)); }).finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [batchId, rowId, detailRevision]);

  useEffect(() => {
    const node = sentinel.current;
    if (!node || rowId || busy || listError || rowError) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) (batchId ? rowLoad : listLoad).current();
    }, { rootMargin: "180px" });
    observer.observe(node); return () => observer.disconnect();
  }, [batchId, rowId, busy, listLoading, rowLoading, items.length, batch?.rows.length, listError, rowError]);

  const upload = async (file: File) => {
    if (!/\.(xlsx|xls)$/i.test(file.name) || file.size > 10 * 1024 * 1024 || file.size === 0) { notify("error", "请选择不超过10MiB的非空 Excel 文件（xlsx或xls）"); return; }
    setBusy(true); setActionError("");
    try {
      const next = adaptImportBatch(await previewImport(file));
      setListRevision(value => value + 1); navigate(next.id, 0, "REVIEW");
    } catch (error) { const text = message(error); setActionError(text); notify("error", text); }
    finally { setBusy(false); }
  };

  const run = async (action: () => Promise<unknown>, rowAction = false) => {
    if (busy || !batch) return;
    setBusy(true); setActionError("");
    try {
      const next = adaptImportBatch(await action());
      if (next.id !== batchId) throw new Error("返回的导入批次不匹配，请刷新确认操作结果");
      setBatch(current => current ? { ...current, status: next.status, stats: next.stats } : next);
      setListRevision(value => value + 1); setRevision(value => value + 1); setConfirmation(null);
      if (rowAction) navigate(batchId, 0, filter, true);
    } catch (error) { const text = message(error); setActionError(text); notify("error", text); }
    finally { setBusy(false); }
  };

  const error = (text: string, retry?: () => void) => text && <div className="import-error" role="alert">{text}{retry && <button className="secondary" type="button" onClick={retry}>重试</button>}</div>;
  const heading = (title: string, back: () => void) => <header className="import-workspace-heading"><button type="button" className="icon-button" disabled={busy} aria-label={rowId ? "返回差异核对" : "返回导入列表"} onClick={back}><ArrowLeft /></button><h3>{title}</h3><button type="button" className="icon-button" aria-label="刷新导入详情" disabled={busy || rowLoading || detailLoading} onClick={() => { setRevision(value => value + 1); setDetailRevision(value => value + 1); }}><RefreshCw /></button></header>;
  const result = (status: string) => <span className={`import-state ${status === "PUBLISHED" ? "good" : "muted"}`}>{status === "PUBLISHED" && <CheckCircle2 size={16} />}{importStatus(status)}</span>;

  return <section className="import-center" aria-label="导入中心" aria-busy={busy}>
    {!batchId ? <>
      <header className="import-list-heading"><Upload aria-hidden="true" /><h3>数据导入</h3>{!listLoading && !listError && <span>{hasMore ? "已加载 " : ""}{items.length} 个批次</span>}<button className="icon-button" type="button" aria-label="刷新导入批次" disabled={busy || listLoading} onClick={() => setListRevision(value => value + 1)}><RefreshCw /></button></header>
      <button className="primary import-upload" type="button" disabled={busy} onClick={() => fileInput.current?.click()}><Upload size={18} />{busy ? "正在识别 Excel" : "上传 Excel"}</button>
      <input ref={fileInput} className="sr-only" type="file" aria-label="上传 Excel 文件" accept=".xlsx,.xls" disabled={busy} onChange={event => { const file = event.target.files?.[0]; event.target.value = ""; if (file) void upload(file); }} />
      {error(actionError)}{error(listError, () => listLoad.current())}
      <div className="import-batches">{items.map(item => <button className="import-batch-card" key={item.id} type="button" disabled={busy} onClick={() => navigate(item.id, 0, "REVIEW")}><time><Clock3 size={16} />{date(item.createdAt)}</time><div className="import-batch-title"><strong>{item.sourceFileName}</strong>{result(item.status)}</div><div className="import-counts"><span>共 {item.totalRows} 行</span><span className={item.errorRows ? "bad-text" : ""}>异常 {item.errorRows} 行</span></div></button>)}</div>
      {listLoading && <p role="status">正在加载导入批次</p>}{!listLoading && !listError && !items.length && <p className="empty-state">暂无导入批次</p>}
      {hasMore && <div ref={sentinel}><button className="secondary" disabled={listLoading} onClick={() => listLoad.current()}>加载更多批次</button></div>}
    </> : rowId ? <>
      {heading(detail ? detailLabel(detail.row) : "导入记录详情", () => navigate(batchId))}
      {error(actionError)}{error(detailError, () => setDetailRevision(value => value + 1))}
      {detailLoading && <p role="status">正在加载差异详情</p>}
      {detail && <><div className="import-detail-identity"><strong>{detail.row.plateNumber || "未识别车牌"}</strong><span>{rowAction(detail.row)}{detail.row.sourceRowNumber > 0 ? ` · Excel 第${detail.row.sourceRowNumber}行` : " · 系统差异检测"}</span><span>{detail.row.sourceSheetName}</span></div>{detail.row.errorMessage && <p className="import-error">{detail.row.errorMessage}</p>}{detail.row.warningMessage && <p className="import-warning">{detail.row.warningMessage}</p>}
        {detail.sections.map((section, index) => <section className="import-diff-section" key={index}><h4>{section.title}</h4><div className="import-diff-columns"><span>字段</span><span>原值</span><span>新值</span></div>{section.fields.map((field, index) => <div className="import-diff-values" key={index}><strong>{field.label}</strong><span>{field.before || "未填写"}</span><span className={field.before !== field.after ? "import-changed" : ""}>{field.after || "未填写"}</span></div>)}</section>)}
        {!!detail.sourceValues.length && <section className="import-diff-section"><h4>Excel 源字段</h4><dl className="import-source-fields">{detail.sourceValues.map((field, index) => <div key={index}><dt>{field.label}</dt><dd>{field.value || "未填写"}</dd></div>)}</dl></section>}
        <footer className="import-bottom-actions">{batch && canResolve(batch, detail.row) ? <><button type="button" className="secondary" disabled={busy || rowLoading} onClick={() => void run(() => resolveImportRows(batchId, [{ rowId, resolution: "SKIP" }]), true)}>{skipLabel(detail.row)}</button><button type="button" className="primary" disabled={busy || rowLoading} onClick={() => void run(() => resolveImportRows(batchId, [{ rowId, resolution: "PUBLISH" }]), true)}>{busy ? "正在保存" : confirmLabel(detail.row)}</button></> : <><span>{detail.row.resolution === "SKIP" ? "已跳过" : detail.row.resolution === "PUBLISH" ? "已确认" : detail.row.resultStatus === "ERROR" ? "异常行不可发布" : "只读记录"}</span><button className="secondary" type="button" disabled={busy} onClick={() => navigate(batchId)}>关闭详情</button></>}</footer>
      </>}
    </> : <>
      {heading("数据差异核对", () => navigate(0))}{error(actionError)}
      {batch && <><h3 className="import-file-name">{batch.sourceFileName}</h3>{result(batch.status)}<div className="import-review-stats">{([["newRows", "新增"], ["updateRows", "更新"], ["reactivateRows", "恢复"], ["deactivateRows", "待失效"], ["errorRows", "异常"], ["pendingReviewRows", "待确认"], ["duplicateRows", "完全一致"]] as const).map(([key, title]) => <span key={key}>{title} <b>{batch.stats[key] < 0 ? "未读取" : batch.stats[key]}</b></span>)}</div><div className="import-filter-bar" aria-label="核对类型">{importFilters.map(([value, label]) => <button type="button" key={value} aria-pressed={filter === value} className={filter === value ? "selected" : ""} disabled={busy} onClick={() => navigate(batchId, 0, value, true)}>{label}</button>)}</div>
        <div className="import-review-rows">{batch.rows.map(row => <button type="button" className={`import-row-card action-${row.plannedAction.toLowerCase()}`} key={row.id} disabled={busy} onClick={() => navigate(batchId, row.id)}><div className="import-row-heading"><strong>{row.plateNumber || "???"}</strong><span>{rowAction(row)}</span>{row.sourceRowNumber > 0 && <span>第{row.sourceRowNumber}行</span>}</div>{row.primarySubject && <p>{row.primarySubject}</p>}{row.warningMessage && <p className="import-warning">{row.warningMessage}</p>}{row.errorMessage && <p className="import-error">{row.errorMessage}</p>}<div className="import-row-detail-link">{detailLabel(row)}<span>{row.resolution === "PENDING" ? "待确认" : row.resolution === "PUBLISH" ? "已确认" : row.resolution === "SKIP" ? "已跳过" : ""}</span></div></button>)}</div>
        {!rowLoading && !rowError && !batch.rows.length && <p className="empty-state">当前分类没有需核对记录</p>}
      </>}{error(rowError, () => rowLoad.current())}{rowLoading && <p role="status">正在加载导入记录</p>}
      {batch && <><p className="import-loaded">已加载 {batch.rows.length} / {batch.rowTotal} 条</p>{batch.rows.length < batch.rowTotal && <div ref={sentinel}><button className="secondary" type="button" disabled={rowLoading || busy} onClick={() => rowLoad.current()}>加载更多记录</button></div>}<footer className="import-bottom-actions">{batch.status === "PUBLISHED" ? <button className="secondary" type="button" disabled={busy || rowLoading} onClick={() => setConfirmation("rollback")}>撤销发布</button> : <>{batch.stats.pendingReviewRows > 0 && <span>还有 {batch.stats.pendingReviewRows} 条待确认</span>}<button className="primary" type="button" disabled={busy || rowLoading || !!rowError || !canPublish(batch)} onClick={() => setConfirmation("publish")}>{batch.status === "ROLLED_BACK" ? "重新发布数据" : "正式发布数据"}</button></>}</footer></>}
    </>}
    <Dialog.Root open={confirmation !== null} onOpenChange={open => { if (!open && !busy) setConfirmation(null); }}><Dialog.Portal><Dialog.Overlay className="glass-dialog-backdrop profile-overlay" /><Dialog.Content className="glass-dialog profile-dialog import-confirm-dialog" aria-describedby="import-confirm-description"><div className="glass-dialog-heading"><Dialog.Title>{confirmation === "rollback" ? "确认撤销发布？" : "确认发布数据？"}</Dialog.Title><Dialog.Close className="icon-button" aria-label="关闭发布确认" disabled={busy}><X /></Dialog.Close></div><p id="import-confirm-description">{confirmation === "rollback" ? "该批次已发布的档案变更将被撤销。" : `将发布已确认的 ${batch?.stats.publishableRows || 0} 条车辆档案变更。`}</p>{error(actionError)}<div className="import-confirm-actions"><Dialog.Close className="secondary" disabled={busy}>取消</Dialog.Close><button className={confirmation === "rollback" ? "danger" : "primary"} disabled={busy} onClick={() => { if (confirmation === "rollback") void run(() => rollbackImport(batchId)); else if (batch && canPublish(batch)) void run(() => publishImport(batchId)); }}>{busy ? "正在处理" : confirmation === "rollback" ? "确认撤销" : "确认发布"}</button></div></Dialog.Content></Dialog.Portal></Dialog.Root>
  </section>;
}

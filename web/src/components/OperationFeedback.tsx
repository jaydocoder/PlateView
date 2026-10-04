import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { CheckCircle2, CircleAlert, X } from "lucide-react";
import { dismissFeedback, getFeedback, subscribeFeedback } from "../state/feedback";
import "./operation-feedback.css";

export function OperationFeedback() {
  const [feedback, setFeedback] = useState(getFeedback);
  const [host, setHost] = useState<HTMLElement>(document.body);
  const [scrollTop, setScrollTop] = useState(0);
  useEffect(() => subscribeFeedback(setFeedback), []);
  useEffect(() => {
    // 放在当前弹框内，避免被弹框的无障碍隔离隐藏或阻止点击关闭。
    const update = () => {
      const dialogs = Array.from(document.querySelectorAll<HTMLElement>('[role="dialog"]')).filter(node => node.getAttribute("data-state") !== "closed");
      setHost(dialogs[dialogs.length - 1] || document.body);
    };
    update();
    const observer = new MutationObserver(update);
    observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["data-state"] });
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const update = () => setScrollTop(host.scrollTop);
    update(); host.addEventListener("scroll", update, { passive: true });
    return () => host.removeEventListener("scroll", update);
  }, [host]);
  useEffect(() => {
    if (!feedback) return;
    const timer = window.setTimeout(dismissFeedback, Math.max(0, feedback.expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [feedback]);
  if (!feedback) return null;
  const Icon = feedback.tone === "success" ? CheckCircle2 : CircleAlert;
  return createPortal(<div className={`operation-feedback ${feedback.tone}`} style={host === document.body ? undefined : { position: "absolute", top: scrollTop + 72, width: "min(420px, calc(100% - 24px))" }} data-testid="operation-feedback" key={feedback.id}>
    <Icon aria-hidden="true" /><div role={feedback.tone === "error" ? "alert" : "status"} aria-atomic="true"><strong>{feedback.tone === "success" ? "操作成功" : "操作失败"}</strong><span>{feedback.message}</span></div>
    <button type="button" onClick={dismissFeedback} aria-label="关闭操作提示"><X aria-hidden="true" /></button>
  </div>, host);
}

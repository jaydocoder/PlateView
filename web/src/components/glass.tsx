import React from "react";
import { motion } from "motion/react";

type GlassProps = React.HTMLAttributes<HTMLDivElement> & {
  variant?: "frosted" | "liquid";
  strength?: "subtle" | "medium" | "strong";
};

export function GlassSurface({ variant = "frosted", strength = "medium", className = "", ...props }: GlassProps) {
  return <div className={`glass-surface glass-${variant} glass-${strength} ${className}`} {...props} />;
}

export function GlassCard(props: GlassProps) {
  return <GlassSurface {...props} className={`glass-card ${props.className || ""}`} />;
}

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  tone?: "primary" | "secondary" | "ghost" | "danger";
  loading?: boolean;
};

export function GlassButton({ tone = "secondary", loading = false, disabled, children, className = "", ...props }: ButtonProps) {
  return <motion.div whileTap={{ scale: 0.97 }} transition={{ duration: 0.16 }}><button className={`glass-button glass-button-${tone} ${className}`} disabled={disabled || loading} {...props}>
    {loading && <span className="button-spinner" aria-hidden="true" />}
    {children}
  </button></motion.div>;
}

export function GlassInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`glass-input ${props.className || ""}`} />;
}

export function GlassSwitch({ checked, onChange, label }: { checked: boolean; onChange: (checked: boolean) => void; label?: string }) {
  return <button type="button" role="switch" aria-checked={checked} aria-label={label} className={`glass-switch ${checked ? "checked" : ""}`} onClick={() => onChange(!checked)}><span /></button>;
}

export function GlassSlider(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} type="range" className={`glass-slider ${props.className || ""}`} />;
}

export function GlassDialog({ open, title, children, onClose }: { open: boolean; title: string; children: React.ReactNode; onClose: () => void }) {
  if (!open) return null;
  return <div className="glass-dialog-backdrop" role="presentation" onMouseDown={event => { if (event.currentTarget === event.target) onClose(); }}><div className="glass-dialog" role="dialog" aria-modal="true" aria-label={title}><div className="glass-dialog-heading"><h2>{title}</h2><button className="clear-search" onClick={onClose} aria-label="关闭"><span aria-hidden="true">×</span></button></div>{children}</div></div>;
}

export function GlassBottomSheet({ open, title, children, onClose }: { open: boolean; title: string; children: React.ReactNode; onClose: () => void }) {
  if (!open) return null;
  return <div className="glass-dialog-backdrop" role="presentation" onMouseDown={event => { if (event.currentTarget === event.target) onClose(); }}><div className="glass-bottom-sheet" role="dialog" aria-modal="true" aria-label={title}><div className="sheet-handle" /><div className="glass-dialog-heading"><h2>{title}</h2><button className="clear-search" onClick={onClose} aria-label="关闭"><span aria-hidden="true">×</span></button></div>{children}</div></div>;
}

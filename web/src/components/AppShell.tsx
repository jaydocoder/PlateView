import React, { useEffect, useState } from "react";
import { Activity, CalendarDays, LayoutDashboard, LogOut, ShieldCheck, UserRound } from "./icons";
import { GlassButton } from "./glass";
import { getUserAvatar, type Profile } from "../api";
import { AppAvatar } from "./AppAvatar";

export type AppScreen = "search" | "schedule" | "statistics" | "profile" | "admin";

export function AppShell({ profile, screen, onNavigate, onLogout, children }: { profile: Profile; screen: AppScreen; onNavigate: (screen: AppScreen) => void; onLogout: () => void; children: React.ReactNode }) {
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    if (!profile.hasAvatar) { setAvatarUrl(null); return; }
    getUserAvatar().then(blob => {
      if (!active) return;
      objectUrl = URL.createObjectURL(blob);
      setAvatarUrl(objectUrl);
    }).catch(() => { if (active) setAvatarUrl(null); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [profile.id, profile.avatarVersion, profile.hasAvatar]);
  const avatar = avatarUrl ? <img src={avatarUrl} alt="" onError={() => setAvatarUrl(null)} /> : <AppAvatar />;
  const nav: Array<[AppScreen, string, React.ReactNode]> = [["search", "首页", <LayoutDashboard size={19} key="search" />], ...(profile.scheduleEnabled ? [["schedule", "排班", <CalendarDays size={19} key="schedule" />] as [AppScreen, string, React.ReactNode]] : []), ["statistics", "统计", <Activity size={19} key="statistics" />], ...(profile.role === "ADMIN" ? [["admin", "管理", <ShieldCheck size={19} key="admin" />] as [AppScreen, string, React.ReactNode]] : []), ["profile", "我的", <UserRound size={19} key="profile" />]];
  return <div className="app-shell"><aside className="sidebar"><div className="sidebar-brand"><div className="brand-mark small">PV</div><span>PlateView</span></div><div className="user-chip"><div className="avatar">{avatar}</div><div><strong>{profile.username}</strong><small>{profile.role === "ADMIN" ? "管理员" : "核验用户"}</small></div></div><nav>{nav.map(([key, label, icon]) => <button key={key} className={screen === key ? "nav-item active" : "nav-item"} onClick={() => onNavigate(key)}><span aria-hidden="true">{icon}</span>{label}</button>)}</nav><div className="sidebar-foot"><span className="online-dot" /> 服务在线<GlassButton tone="ghost" className="sidebar-logout" onClick={onLogout}><LogOut size={15} />退出</GlassButton></div></aside><main className="main-area"><header className="mobile-header"><div className="brand-mark small">PV</div><strong>{screen === "search" ? "车辆核验" : nav.find(item => item[0] === screen)?.[1] || "工作台"}</strong><button className="avatar user-avatar-button" onClick={() => onNavigate("profile")} aria-label="打开我的">{avatar}</button></header><div className="content-wrap">{children}</div><nav className="bottom-nav">{nav.map(([key, label, icon]) => <button key={key} className={screen === key ? "active" : ""} onClick={() => onNavigate(key)}><span aria-hidden="true">{icon}</span>{label}</button>)}</nav></main></div>;
}

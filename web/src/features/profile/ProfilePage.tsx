import { useRef, useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Camera, ChevronRight, CodeXml, LogOut, Pencil, UserRoundCog, X } from "lucide-react";
import { getProfile, login, removeOwnAvatar, updateOwnProfile, uploadOwnAvatar, type Profile } from "../../api";
import { CurrentUserAvatar } from "../../components/AdminUserAvatar";
import { GlassButton } from "../../components/glass";
import "./profile.css";
import { notify } from "../../state/feedback";

export function ProfilePage({ profile, onProfile, onLogout }: { profile: Profile; onProfile: (profile: Profile) => void; onLogout: () => Promise<void> }) {
  const [editor, setEditor] = useState(false);
  const [confirmLogout, setConfirmLogout] = useState(false);
  const [username, setUsername] = useState(profile.username);
  const [currentPassword, setCurrentPassword] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [savedCredentials, setSavedCredentials] = useState(false);
  const editorTrigger = useRef<HTMLElement | null>(null);
  const invalid = (text: string) => { setMessage(text); notify("error", text); };

  function openEditor() {
    editorTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setUsername(profile.username); setCurrentPassword(""); setPassword(""); setConfirmation(""); setMessage(""); setSavedCredentials(false); setEditor(true);
  }

  async function changeAvatar(file?: File) {
    if (file && (!/image\/(jpeg|png|webp|gif|bmp)/.test(file.type) || file.size > 10 * 1024 * 1024)) {
      invalid("请选择不超过10MiB的图片（JPEG、PNG、WebP、GIF或BMP）"); return;
    }
    setBusy(true); setMessage("");
    try {
      const changed = file ? await uploadOwnAvatar(file) : await removeOwnAvatar();
      onProfile({ ...profile, ...changed }); setMessage(file ? "头像已更新" : "头像已移除");
    } catch (error) { setMessage(error instanceof Error ? error.message : "头像更新失败，请重试"); }
    finally { setBusy(false); }
  }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    if (!username.trim()) { invalid("请输入用户名"); return; }
    if (username.trim().length > 64) { invalid("用户名不能超过64个字符"); return; }
    if (!password && confirmation) { invalid("请输入新密码"); return; }
    if (password && password.length < 6) { invalid("密码至少需要6个字符"); return; }
    if (password && !password.trim()) { invalid("新密码不能全部为空格"); return; }
    if (password && password !== confirmation) { invalid("两次输入的密码不一致"); return; }
    if (!savedCredentials && username.trim() === profile.username && !password) { invalid(currentPassword ? "请输入新密码并确认新密码" : "没有需要保存的修改"); return; }
    if (!currentPassword) { invalid("请输入当前密码"); return; }
    setBusy(true); setMessage("");
    try {
      // 资料更新撤销旧会话；重试重新登录时不能再次使用旧密码提交资料。
      if (!savedCredentials) {
        await updateOwnProfile({ username: username.trim(), ...(password ? { currentPassword, password } : {}) });
        setSavedCredentials(true);
      }
      await login(username.trim(), password || currentPassword);
      onProfile(await getProfile()); setEditor(false); setCurrentPassword(""); setPassword(""); setConfirmation("");
      notify("success", "账号资料已保存");
    } catch (error) { invalid(error instanceof Error ? error.message : "保存失败，请重试"); }
    finally { setBusy(false); }
  }

  return <section className="profile-page" aria-label="我的资料">
    <div className="profile-identity">
      <CurrentUserAvatar user={profile} />
      <div className="profile-identity-text"><h2>{profile.username}</h2><span className="profile-role">{profile.role === "ADMIN" ? "管理员" : "普通用户"}</span></div>
      <button type="button" className="profile-edit" aria-label="编辑我的资料" title="编辑我的资料" onClick={openEditor}><Pencil size={22} /></button>
    </div>
    <div className="profile-menu">
      <button className="profile-menu-row" onClick={openEditor}><span className="profile-menu-icon"><UserRoundCog /></span><span>账号与安全</span><ChevronRight /></button>
      <a className="profile-menu-row" href="https://github.com/jaydocoder/PlateView" target="_blank" rel="noopener noreferrer"><span className="profile-menu-icon source"><CodeXml /></span><span>项目源码</span><ChevronRight /></a>
      <button className="profile-menu-row logout" onClick={() => { setMessage(""); setConfirmLogout(true); }}><span className="profile-menu-icon"><LogOut /></span><span>退出登录</span><ChevronRight /></button>
    </div>
    <Dialog.Root open={editor} onOpenChange={value => { if (!busy) setEditor(value); }}>
      <Dialog.Portal><Dialog.Overlay className="glass-dialog-backdrop profile-overlay" /><Dialog.Content className="glass-dialog profile-dialog" aria-describedby={undefined} onCloseAutoFocus={event => { event.preventDefault(); editorTrigger.current?.focus(); }}>
        <div className="glass-dialog-heading"><Dialog.Title>账号与安全</Dialog.Title><Dialog.Close className="profile-edit" disabled={busy} aria-label="关闭账号与安全"><X size={22} /></Dialog.Close></div>
        <div className="profile-avatar-editor"><CurrentUserAvatar user={profile} /><div><label className={`profile-avatar-upload ${busy || savedCredentials ? "disabled" : ""}`}><Camera size={18} />更换头像<input type="file" aria-label="选择头像" accept="image/jpeg,image/png,image/webp,image/gif,image/bmp" disabled={busy || savedCredentials} onChange={event => { const file = event.target.files?.[0]; if (file) void changeAvatar(file); event.target.value = ""; }} /></label><button type="button" disabled={busy || !profile.hasAvatar || savedCredentials} onClick={() => void changeAvatar()}>移除头像</button></div></div>
        <form className="profile-account-form" onSubmit={save} noValidate>
          <fieldset><legend>账号资料</legend><label>用户名<input className="profile-account-input" aria-label="用户名" placeholder="输入用户名" autoComplete="username" value={username} maxLength={64} disabled={busy || savedCredentials} onChange={event => setUsername(event.target.value)} required /></label></fieldset>
          <fieldset><legend>修改密码</legend>
            <label>当前密码<input className="profile-account-input" aria-label="当前密码" placeholder="输入当前密码" type="password" autoComplete="current-password" value={currentPassword} disabled={busy} onChange={event => setCurrentPassword(event.target.value)} /></label>
            <label>新密码<input className="profile-account-input" aria-label="新密码" placeholder="输入新密码" type="password" autoComplete="new-password" minLength={6} value={password} disabled={busy || savedCredentials} onChange={event => setPassword(event.target.value)} /></label>
            <label>确认新密码<input className="profile-account-input" aria-label="确认新密码" placeholder="再次输入新密码" type="password" autoComplete="new-password" value={confirmation} disabled={busy || savedCredentials} onChange={event => setConfirmation(event.target.value)} /></label>
          </fieldset>
          {message && <p role="status" className="profile-message">{message}</p>}
          <div className="profile-save-row"><GlassButton tone="primary" type="submit" disabled={busy}>{busy ? "正在保存…" : savedCredentials ? "重新连接账号" : "保存更改"}</GlassButton></div>
        </form>
      </Dialog.Content></Dialog.Portal>
    </Dialog.Root>
    <Dialog.Root open={confirmLogout} onOpenChange={value => { if (!busy) setConfirmLogout(value); }}><Dialog.Portal><Dialog.Overlay className="glass-dialog-backdrop profile-overlay" /><Dialog.Content className="glass-dialog profile-dialog" aria-describedby={undefined}><div className="glass-dialog-heading"><Dialog.Title>确认退出登录？</Dialog.Title><Dialog.Close className="profile-edit" disabled={busy} aria-label="关闭退出确认"><X /></Dialog.Close></div>{message && <p role="status">{message}</p>}<div className="profile-dialog-actions"><Dialog.Close asChild><GlassButton tone="ghost" disabled={busy}>取消</GlassButton></Dialog.Close><GlassButton disabled={busy} onClick={async () => { setBusy(true); try { await onLogout(); } catch { setMessage("退出失败，请重试"); } finally { setBusy(false); } }}>确认退出</GlassButton></div></Dialog.Content></Dialog.Portal></Dialog.Root>
  </section>;
}

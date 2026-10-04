import { useEffect, useState } from "react";
import { getAdminUserAvatar, getUserAvatar } from "../api";
import { AppAvatar } from "./AppAvatar";

type AvatarUser = {
  id: number;
  username: string;
  hasAvatar?: boolean;
  avatarVersion?: number;
};

export function AdminUserAvatar({ user }: { user: AvatarUser }) {
  return <AvatarImage user={user} load={getAdminUserAvatar} className="account-list-avatar" />;
}

export function CurrentUserAvatar({ user }: { user: AvatarUser }) {
  return <AvatarImage user={user} load={getUserAvatar} className="large" />;
}

function AvatarImage({ user, load, className }: { user: AvatarUser; load: (id: number) => Promise<Blob>; className: string }) {
  const [image, setImage] = useState<{ key: string; url: string } | null>(null);
  const key = `${user.id}:${user.avatarVersion ?? 0}`;

  useEffect(() => {
    let active = true;
    let url: string | null = null;
    setImage(null);
    if (!user.hasAvatar) return;
    load(user.id).then(blob => {
      if (!active) return;
      url = URL.createObjectURL(blob);
      setImage({ key, url });
    }).catch(() => { if (active) setImage(null); });
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [user.id, user.hasAvatar, key, load]);

  return <div className={`avatar ${className}`}>
    {user.hasAvatar && image?.key === key
      ? <img src={image.url} alt={`${user.username}的头像`} onError={() => setImage(null)} />
      : <AppAvatar />}
  </div>;
}

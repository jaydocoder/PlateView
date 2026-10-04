import { test, expect, type Page } from "@playwright/test";

async function session(page: Page) {
  const profile = { id: 3, username: "资料测试", role: "USER", hasAvatar: false, avatarVersion: 0 };
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: profile }));
  return profile;
}

for (const colorScheme of ["light", "dark"] as const) {
  test(`账号表单输入框无需悬停即可识别和阅读：${colorScheme === "light" ? "浅色" : "暗色"}`, async ({ page }, testInfo) => {
    await session(page);
    await page.emulateMedia({ colorScheme });
    await page.goto("profile");
    await page.getByRole("button", { name: "账号与安全", exact: true }).click();
    await page.mouse.move(0, 0);
    const modal = page.getByRole("dialog", { name: "账号与安全" });
    const inputs = modal.locator(".profile-account-input");
    await expect(inputs).toHaveCount(4);
    for (const input of await inputs.all()) {
      await expect(input).toHaveCSS("border-top-width", "1px");
      await expect(input).toHaveCSS("border-top-style", "solid");
      await expect(input).toHaveCSS("background-color", colorScheme === "light" ? "rgb(251, 253, 251)" : "rgb(35, 58, 48)");
      await expect(input).toHaveCSS("color", colorScheme === "light" ? "rgb(23, 53, 45)" : "rgb(237, 246, 240)");
      await expect(input).toHaveCSS("height", "48px");
      await expect(input).not.toHaveClass(/glass-input/);
    }
    await expect(modal.getByLabel("用户名", { exact: true })).toHaveValue("资料测试");
    await modal.getByLabel("新密码", { exact: true }).fill("test-password");
    await modal.getByLabel("确认新密码", { exact: true }).fill("test-password");
    await modal.getByRole("button", { name: "关闭账号与安全" }).focus();
    await page.mouse.move(0, 0);
    await expect(modal.getByLabel("新密码", { exact: true })).toHaveValue("test-password");
    await expect(modal.getByLabel("新密码", { exact: true })).toHaveAttribute("type", "password");
    await page.screenshot({ path: testInfo.outputPath("账号表单.png") });
  });
}

test("我的资料布局、菜单、弹框关闭和退出确认", async ({ page }, testInfo) => {
  await session(page);
  await page.route("**/auth/web-logout", route => route.fulfill({ status: 204 }));
  await page.goto("profile");
  const region = page.getByRole("region", { name: "我的资料" });
  await expect(region.getByRole("heading", { name: "资料测试" })).toBeVisible();
  await expect(region.getByText("管理工作台")).toHaveCount(0);
  await expect(region.getByRole("link", { name: "软件更新" })).toHaveCount(0);
  await expect(region.getByRole("link", { name: "项目源码" })).toHaveAttribute("href", "https://github.com/jaydocoder/PlateView");
  await region.getByRole("button", { name: "账号与安全", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "账号与安全" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(region.getByRole("button", { name: "账号与安全", exact: true })).toBeFocused();
  await region.getByRole("button", { name: "编辑我的资料" }).click();
  await page.getByRole("button", { name: "关闭账号与安全" }).click();
  await page.screenshot({ path: testInfo.outputPath("我的页面.png"), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await region.getByRole("button", { name: "退出登录", exact: true }).click();
  await page.getByRole("button", { name: "取消", exact: true }).click();
  await expect(region).toBeVisible();
  await region.getByRole("button", { name: "退出登录", exact: true }).click();
  await page.getByRole("button", { name: "确认退出", exact: true }).click();
  await expect(page.getByRole("button", { name: "登录系统", exact: true })).toBeVisible();
});

test("资料失败保留输入、密码校验与保存后会话恢复", async ({ page }) => {
  const profile = await session(page);
  let fail = true;
  let writes = 0;
  await page.route("**/auth/profile", route => {
    if (route.request().method() === "GET") return route.fulfill({ json: profile });
    writes++;
    if (fail) { fail = false; return route.fulfill({ status: 503, json: { message: "暂时无法保存" } }); }
    expect(route.request().postDataJSON()).toEqual({ username: "新的用户名", currentPassword: "old-password", password: "new-password" });
    profile.username = "新的用户名";
    return route.fulfill({ status: 204 });
  });
  await page.route("**/auth/web-login", route => {
    expect(route.request().postDataJSON()).toEqual({ username: "新的用户名", password: "new-password" });
    return route.fulfill({ json: { accessToken: "new", user: profile } });
  });
  await page.goto("profile");
  await page.getByRole("button", { name: "账号与安全", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.getByLabel("用户名", { exact: true }).fill("新的用户名");
  await modal.getByLabel("当前密码", { exact: true }).fill("old-password");
  await modal.getByLabel("新密码", { exact: true }).fill("new-password");
  await modal.getByLabel("确认新密码", { exact: true }).fill("not-matched");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(modal.locator('p[role="status"]')).toContainText("不一致");
  expect(writes).toBe(0);
  await modal.getByLabel("确认新密码", { exact: true }).fill("new-password");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(modal.locator('p[role="status"]')).toContainText("暂时无法保存");
  await expect(modal.getByLabel("用户名", { exact: true })).toHaveValue("新的用户名");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(modal).toHaveCount(0);
  await expect(page.locator(".profile-identity h2")).toHaveText("新的用户名");
});

test("头像上传、移除和无头像回退", async ({ page }) => {
  const profile = await session(page);
  let uploaded = false;
  await page.route("**/auth/profile/avatar", route => {
    if (route.request().method() === "POST") {
      expect(route.request().headers()["content-type"]).toContain("multipart/form-data");
      uploaded = true; profile.hasAvatar = true; profile.avatarVersion++;
      return route.fulfill({ json: profile });
    }
    return route.fulfill({ contentType: "image/svg+xml", body: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="green"/></svg>' });
  });
  await page.route("**/auth/profile/avatar/delete", route => { profile.hasAvatar = false; profile.avatarVersion++; return route.fulfill({ json: profile }); });
  await page.goto("profile");
  await page.getByRole("button", { name: "账号与安全", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.getByLabel("选择头像").setInputFiles({ name: "头像.png", mimeType: "image/png", buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", "base64") });
  await expect(modal.locator('p[role="status"]')).toHaveText("头像已更新");
  expect(uploaded).toBe(true);
  await expect(modal.locator(".avatar img")).toHaveAttribute("src", /^blob:/);
  await modal.getByRole("button", { name: "移除头像" }).click();
  await expect(modal.locator('p[role="status"]')).toHaveText("头像已移除");
  await expect(modal.locator(".avatar img")).toHaveAttribute("src", /app-icon.png$/);
});

test("窄屏弹框可滚动且不横向溢出", async ({ page }, testInfo) => {
  await session(page);
  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto("profile");
  await page.getByRole("button", { name: "账号与安全", exact: true }).click();
  const modal = page.getByRole("dialog");
  await expect(modal).toBeVisible();
  await modal.getByRole("button", { name: "保存更改" }).scrollIntoViewIfNeeded();
  expect(await modal.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("账号与安全窄屏.png") });
});

test("资料已保存但重新连接失败时不重复提交", async ({ page }) => {
  const profile = await session(page);
  let writes = 0;
  let failLogin = true;
  await page.route("**/auth/profile", route => {
    if (route.request().method() === "GET") return route.fulfill({ json: profile });
    writes++; profile.username = "已保存用户名"; return route.fulfill({ status: 204 });
  });
  await page.route("**/auth/web-login", route => {
    if (failLogin) { failLogin = false; return route.fulfill({ status: 503, json: { message: "连接失败" } }); }
    return route.fulfill({ json: { accessToken: "new", user: profile } });
  });
  await page.goto("profile");
  await page.getByRole("button", { name: "账号与安全", exact: true }).click();
  const modal = page.getByRole("dialog");
  await modal.getByLabel("用户名", { exact: true }).fill("已保存用户名");
  await modal.getByLabel("当前密码", { exact: true }).fill("current-password");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(modal.locator('p[role="status"]')).toContainText("连接失败");
  await modal.getByRole("button", { name: "重新连接账号" }).click();
  await expect(modal).toHaveCount(0);
  expect(writes).toBe(1);
});

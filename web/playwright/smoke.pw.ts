import { test, expect } from "@playwright/test";

test("未登录访问网页会显示登录页", async ({ page }) => {
  await page.goto("search");
  await expect(page).toHaveTitle(/PlateView/);
  await expect(page.getByText("车辆核验")).toBeVisible();
  await expect(page.locator("input[autocomplete='username']")).toBeVisible();
});

test("移动端页面没有横向溢出", async ({ page }) => {
  await page.goto("search");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("车辆历史记录优先跳转详情路由", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "test-token" }) }));
  await page.route("**/auth/profile", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: 1, username: "admin", role: "ADMIN", scheduleEnabled: true }) }));
  await page.goto("search");
  await page.evaluate(() => {
    localStorage.setItem("plateview.search.history.admin", JSON.stringify(["新H12345"]));
    localStorage.setItem("plateview.search.detail.新H12345", JSON.stringify({ id: 1, plateNumber: "新H12345", category: "RESIDENT", detailAccessible: true }));
  });
  await page.reload();
  await page.locator(".history-row-main").click();
  await expect(page).toHaveURL(/\/vehicle\/1$/);
});

test("管理员可打开按设计呈现的新增账号弹窗并看到用户头像", async ({ page }, testInfo) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "test-token" }) }));
  await page.route("**/auth/profile", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: 1, username: "test-admin", role: "ADMIN", scheduleEnabled: true, hasAvatar: true, avatarVersion: 1 }) }));
  await page.route("**/auth/avatar", route => route.fulfill({ status: 200, contentType: "image/svg+xml", body: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><circle cx="16" cy="16" r="16" fill="#276b52"/></svg>' }));
  await page.route("**/work-orders/status", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ sources: [] }) }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({}) }));
  await page.route("**/admin/users?limit=50&offset=0", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ items: [], total: 0 }) }));

  await page.goto("search");
  await expect(page.locator(".app-shell")).toBeVisible();
  if (await page.locator(".user-avatar-button").isVisible()) await expect(page.locator(".user-avatar-button img")).toBeVisible();
  else await expect(page.locator(".sidebar .user-chip .avatar img")).toBeVisible();
  await page.getByRole("button", { name: "管理", exact: true }).click();
  await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
  await page.getByRole("button", { name: "+ 新增账号" }).click();

  await expect(page.getByRole("heading", { name: "创建新账号" })).toBeVisible();
  await expect(page.getByPlaceholder("用户名")).toBeVisible();
  await expect(page.getByPlaceholder("登录密码")).toBeVisible();
  await expect(page.getByText("分配角色")).toBeVisible();
  await expect(page.getByText("账号状态")).toBeVisible();
  await expect(page.getByText("真实姓名")).toHaveCount(0);
  await expect(page.locator(".account-toggle")).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath("新增账号.png"), fullPage: true });
  await page.getByRole("button", { name: "创建账号", exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath("新增账号底部.png"), fullPage: true });
  await page.getByRole("button", { name: "创建账号", exact: true }).click();
  await expect(page.locator(".admin-form-message")).toHaveText("请输入账号");
  await expect(page.getByTestId("operation-feedback")).toContainText("请输入账号");
  await page.getByPlaceholder("用户名").fill("new-user");
  await page.getByPlaceholder("登录密码").fill("test-password");
  await page.getByRole("button", { name: "停用", exact: true }).click();
  let createdCount = 0;
  await page.route("**/admin/users", async route => {
    expect(route.request().postDataJSON()).toEqual({ username: "new-user", password: "test-password", role: "USER" });
    createdCount++;
    await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify({ id: 8, version: 1 }) });
  });
  await page.route("**/admin/users/8", async route => {
    expect(route.request().postDataJSON()).toEqual({ role: "USER", status: "DISABLED" });
    await route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "服务暂不可用" }) });
  });
  await page.getByRole("button", { name: "创建账号", exact: true }).click();
  await expect(page.locator(".admin-form-message")).toHaveText("账号已创建，但停用失败：服务暂不可用");
  await expect(page.getByTestId("operation-feedback")).toContainText("账号已创建，但停用失败：服务暂不可用");
  await expect(page.getByRole("button", { name: "保存账号", exact: true })).toBeVisible();
  expect(createdCount).toBe(1);
  await page.getByRole("button", { name: "关闭账号表单" }).click();
  await expect(page.locator(".account-editor-card")).toHaveCount(0);
});

test("账号列表显示各自头像并在无头像或加载失败时回退", async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    const revoke = URL.revokeObjectURL.bind(URL);
    (window as any).revokedAvatarUrls = [];
    URL.revokeObjectURL = url => { (window as any).revokedAvatarUrls.push(url); revoke(url); };
  });
  await page.route("**/auth/web-refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "test-token" }) }));
  await page.route("**/auth/profile", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: 1, username: "manager", role: "ADMIN" }) }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  const users = [
    { id: 21, username: "alice", hasAvatar: true, avatarVersion: 1 },
    { id: 22, username: "bob", hasAvatar: false },
    { id: 23, username: "carol", hasAvatar: true, avatarVersion: 1 },
    { id: 24, username: "david", hasAvatar: true, avatarVersion: 1 },
    { id: 25, username: "ella", hasAvatar: true, avatarVersion: 1 },
  ].map(user => ({ ...user, role: "USER", status: "ACTIVE" }));
  await page.route("**/admin/users?limit=50&offset=0", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ items: users }) }));
  const requestedIds: number[] = [];
  await page.route("**/admin/users/*/avatar", async route => {
    expect(route.request().headers().authorization).toBe("Bearer test-token");
    const id = Number(route.request().url().match(/users\/(\d+)\/avatar/)?.[1]);
    requestedIds.push(id);
    if (id === 23) { await route.fulfill({ status: 404 }); return; }
    if (id === 24) { await route.fulfill({ status: 200, contentType: "image/png", body: "损坏的图片" }); return; }
    const color = id === 21 ? "#276b52" : "#bf8122";
    await route.fulfill({ status: 200, contentType: "image/svg+xml", body: `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="${color}"/></svg>` });
  });
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
  const rows = page.locator(".admin-user-row");
  await expect(rows).toHaveCount(5);
  await expect(rows.nth(0).getByAltText("alice的头像")).toBeVisible();
  await expect(rows.nth(4).getByAltText("ella的头像")).toBeVisible();
  await expect.poll(() => rows.nth(0).locator("img").evaluate((image: HTMLImageElement) => image.naturalWidth)).toBe(32);
  await expect(rows.nth(1).getByAltText("默认头像")).toBeVisible();
  await expect.poll(() => requestedIds.includes(23) && requestedIds.includes(24)).toBe(true);
  await expect(rows.nth(2).getByAltText("默认头像")).toBeVisible();
  await expect(rows.nth(3).getByAltText("默认头像")).toBeVisible();
  expect(requestedIds).not.toContain(22);
  const urls = await page.locator('.account-list-avatar img[src^="blob:"]').evaluateAll(images => images.map(image => image.getAttribute("src")));
  expect(new Set(urls).size).toBe(2);
  await page.screenshot({ path: testInfo.outputPath("账号列表头像.png"), fullPage: true });
  await page.getByRole("button", { name: "概览", exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).revokedAvatarUrls.length)).toBe(3);
});

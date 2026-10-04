import { test, expect } from "@playwright/test";

for (const hasPrimaryFlag of [true, false]) {
test(`普通管理员保存角色状态且 admin 只读，接口主管理员标识${hasPrimaryFlag ? "存在" : "缺失"}`, async ({ page }, testInfo) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "test-token" }) }));
  await page.route("**/auth/profile", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: 10, username: "任意管理账号", role: "ADMIN", isPrimaryAdministrator: false }) }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  const users = [
    { id: 1, username: "admin", role: "ADMIN", status: "ACTIVE", version: 4, ...(hasPrimaryFlag ? { isPrimaryAdministrator: true } : {}) },
    { id: 2, username: "普通用户代表", role: "USER", status: "ACTIVE", version: 2 },
    { id: 3, username: "其他管理员代表", role: "ADMIN", status: "ACTIVE", version: 3 },
  ];
  await page.route("**/admin/users?limit=50&offset=0", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ items: users }) }));
  const updates: Array<{ id: number; payload: Record<string, unknown> }> = [];
  await page.route("**/admin/users/*", async route => {
    const payload = route.request().postDataJSON();
    const id = Number(route.request().url().split("/").pop());
    expect(route.request().headers()["if-match-version"]).toBe(String(id === 2 ? 2 : 3));
    updates.push({ id, payload });
    if (payload.username != null || payload.password != null) {
      await route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({ message: "仅admin账号可以修改其他账号的用户名、密码或头像" }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id, version: 5 }) });
  });
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
  await page.getByRole("button", { name: "编辑账号 普通用户代表", exact: true }).click();
  await page.getByRole("button", { name: "管理员", exact: true }).click();
  await page.getByRole("button", { name: "停用", exact: true }).click();
  await page.getByRole("button", { name: "保存账号", exact: true }).click();
  await expect(page.getByText("仅admin账号可以修改其他账号的用户名、密码或头像")).toHaveCount(0);
  await expect(page.locator(".account-editor-card")).toHaveCount(0);
  expect(updates[0]).toEqual({ id: 2, payload: { role: "ADMIN", status: "DISABLED" } });
  await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
  await page.getByRole("button", { name: "编辑账号 其他管理员代表", exact: true }).click();
  await page.getByRole("button", { name: "普通用户", exact: true }).click();
  await page.getByRole("button", { name: "保存账号", exact: true }).click();
  await expect(page.locator(".account-editor-card")).toHaveCount(0);
  expect(updates[1]).toEqual({ id: 3, payload: { role: "USER", status: "ACTIVE" } });
  await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
  await page.getByRole("button", { name: "编辑账号 admin", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "维护账号信息" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "普通用户", exact: true })).toBeDisabled();
  await expect(dialog.getByRole("button", { name: "管理员", exact: true })).toBeDisabled();
  await expect(dialog.getByRole("button", { name: "启用", exact: true })).toBeDisabled();
  await expect(dialog.getByRole("button", { name: "停用", exact: true })).toBeDisabled();
  await expect(dialog.getByRole("button", { name: "保存账号", exact: true })).toBeDisabled();
  const roleButton = dialog.getByRole("button", { name: "管理员", exact: true });
  await expect(roleButton).toHaveCSS("background-color", "rgb(223, 228, 226)");
  await page.screenshot({ path: testInfo.outputPath("主管理员只读.png") });
  expect(updates).toHaveLength(2);
  await dialog.getByRole("button", { name: "关闭账号表单" }).click();
  await expect(dialog).toHaveCount(0);
  await page.locator(".admin-user-row").filter({ has: page.getByText("admin", { exact: true }) }).click();
  await expect(dialog.getByRole("button", { name: "保存账号", exact: true })).toBeDisabled();
  expect(updates).toHaveLength(2);
});
}

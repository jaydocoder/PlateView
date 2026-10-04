import { test, expect } from "@playwright/test";

test("主管理员保存三项数据访问权限后重新打开仍保留，取消勾选也可保存", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
  const user = { id: 2, username: "test1", role: "USER", status: "ACTIVE", version: 7, otherLongTermAccessEnabled: false, residentRemarksAccessEnabled: false, wechatWorkOrderAccessEnabled: false };
  await page.route("**/admin/users?*", route => route.fulfill({ json: { items: [user], total: 1 } }));
  const updates: Record<string, unknown>[] = [];
  await page.route("**/admin/users/2", route => {
    expect(route.request().method()).toBe("PUT");
    expect(route.request().headers()["if-match-version"]).toBe(String(user.version));
    const payload = route.request().postDataJSON();
    updates.push(payload);
    Object.assign(user, payload, { version: user.version + 1 });
    return route.fulfill({ json: user });
  });
  const open = async () => {
    await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
    await page.getByRole("button", { name: "编辑账号 test1", exact: true }).click();
  };
  const boxes = () => page.getByRole("dialog").getByRole("checkbox");
  await page.goto("admin"); await open();
  for (const enabled of [true, false]) {
    for (let index = 0; index < 3; index++) await boxes().nth(index).setChecked(enabled);
    await page.getByRole("button", { name: "保存账号", exact: true }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    expect(updates.at(-1)).toMatchObject({ otherLongTermAccessEnabled: enabled, residentRemarksAccessEnabled: enabled, wechatWorkOrderAccessEnabled: enabled });
    await open();
    for (let index = 0; index < 3; index++) await expect(boxes().nth(index)).toBeChecked({ checked: enabled });
  }
});

test("三项权限独立保存，保存失败保留选择并允许重试", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
  const user = { id: 2, username: "test1", role: "USER", status: "ACTIVE", version: 8, otherLongTermAccessEnabled: false, residentRemarksAccessEnabled: true, wechatWorkOrderAccessEnabled: false };
  await page.route("**/admin/users?*", route => route.fulfill({ json: { items: [user], total: 1 } }));
  let attempts = 0;
  await page.route("**/admin/users/2", route => {
    attempts++;
    expect(route.request().postDataJSON()).toMatchObject({ otherLongTermAccessEnabled: true, residentRemarksAccessEnabled: false, wechatWorkOrderAccessEnabled: false });
    return attempts === 1 ? route.fulfill({ status: 500, json: { message: "保存暂不可用" } }) : route.fulfill({ json: { ...user, version: 9 } });
  });
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
  await page.getByRole("button", { name: "编辑账号 test1", exact: true }).click();
  await page.getByRole("checkbox", { name: "其他长期通行车辆", exact: true }).check();
  await page.getByRole("checkbox", { name: "村民车辆备注", exact: true }).uncheck();
  await page.getByRole("button", { name: "保存账号", exact: true }).click();
  await expect(page.locator('.admin-form-message[role="alert"]')).toHaveText("保存暂不可用");
  await expect(page.getByTestId("operation-feedback")).toContainText("保存暂不可用");
  await expect(page.getByRole("checkbox", { name: "其他长期通行车辆", exact: true })).toBeChecked();
  await expect(page.getByRole("checkbox", { name: "村民车辆备注", exact: true })).not.toBeChecked();
  await page.getByRole("button", { name: "保存账号", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(attempts).toBe(2);
});

test("主管理员自身受保护数据权限不能切换且保存不携带权限字段", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
  await page.route("**/admin/users?*", route => route.fulfill({ json: { items: [{ id: 1, username: "admin", role: "ADMIN", status: "ACTIVE", version: 5, otherLongTermAccessEnabled: true, residentRemarksAccessEnabled: true, wechatWorkOrderAccessEnabled: true }] } }));
  let payload: Record<string, unknown> | null = null;
  await page.route("**/admin/users/1", route => { payload = route.request().postDataJSON(); return route.fulfill({ json: { id: 1, version: 6 } }); });
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "账号管理", exact: true }).click();
  await page.getByRole("button", { name: "编辑账号 admin", exact: true }).click();
  for (const label of ["其他长期通行车辆", "村民车辆备注", "微信车单数据"]) {
    await expect(page.getByRole("checkbox", { name: label, exact: true })).toBeDisabled();
  }
  await page.getByRole("button", { name: "保存账号", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(payload).not.toHaveProperty("otherLongTermAccessEnabled");
  expect(payload).not.toHaveProperty("residentRemarksAccessEnabled");
  expect(payload).not.toHaveProperty("wechatWorkOrderAccessEnabled");
});

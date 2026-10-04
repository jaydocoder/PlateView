import { expect, test } from "@playwright/test";

test("操作结果可见、失败保留输入、刷新保留成功提示", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
  const policy = { revision: 1, vehicleResultLimit: 10, workOrderResultLimit: 10, wechatMessageResultLimit: 10, apiBaseUrl: "http://localhost/", updateBaseUrl: "http://localhost/", clientCount: 0, appliedClientCount: 0 };
  let failure = true;
  await page.route("**/admin/client-policy**", route => {
    if (route.request().method() === "GET") return route.fulfill({ json: policy });
    if (route.request().url().endsWith("/test")) return route.fulfill({ json: { success: false, message: "目标服务不可达" } });
    if (failure) { failure = false; return route.fulfill({ status: 409, json: { message: "配置版本冲突，请重试" } }); }
    return route.fulfill({ json: policy });
  });
  await page.goto("admin");
  await page.locator(".admin-module-card").filter({ hasText: "数据访问控制" }).click();
  await page.getByLabel("车辆档案", { exact: true }).fill("12");
  await page.getByRole("button", { name: "保存数量", exact: true }).click();
  const toast = page.getByTestId("operation-feedback");
  await expect(toast).toContainText("操作失败");
  await expect(toast).toContainText("版本冲突");
  await expect(page.getByLabel("车辆档案", { exact: true })).toHaveValue("12");
  await toast.getByRole("button", { name: "关闭操作提示" }).click();
  await expect(toast).toHaveCount(0);
  await page.getByRole("button", { name: "保存数量", exact: true }).click();
  await expect(toast).toContainText("操作成功");
  await page.reload();
  await expect(toast).toContainText("返回数量保存成功");
  await toast.getByRole("button", { name: "关闭操作提示" }).click();
  await page.locator(".admin-module-card").filter({ hasText: "数据访问控制" }).click();
  await page.getByRole("button", { name: "测试连接", exact: true }).first().click();
  await expect(toast).toContainText("操作失败");
  await expect(toast).toContainText("目标服务不可达");
  await page.screenshot({ path: test.info().outputPath("操作失败提示.png") });
});

test("弹框内校验失败提示可关闭且保留编辑内容", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 2, username: "测试用户", role: "USER" } }));
  await page.goto("profile");
  await page.getByRole("button", { name: "账号与安全", exact: true }).click();
  const modal = page.getByRole("dialog", { name: "账号与安全" });
  await modal.getByLabel("新密码", { exact: true }).fill("password-one");
  await modal.getByLabel("确认新密码", { exact: true }).fill("password-two");
  await modal.getByRole("button", { name: "保存更改" }).click();
  const toast = page.getByTestId("operation-feedback");
  await expect(toast).toContainText("两次输入的密码不一致");
  await expect(toast).toBeVisible();
  expect(await toast.evaluate(node => { const rect = node.getBoundingClientRect(); return rect.top >= 0 && rect.bottom <= innerHeight && rect.left >= 0 && rect.right <= innerWidth; })).toBe(true);
  await page.screenshot({ path: test.info().outputPath("弹框操作提示.png") });
  await toast.getByRole("button", { name: "关闭操作提示" }).click();
  await expect(modal).toBeVisible();
  await expect(modal.getByLabel("新密码", { exact: true })).toHaveValue("password-one");
  await expect(toast).toHaveCount(0);
});

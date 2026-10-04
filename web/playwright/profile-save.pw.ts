import { expect, test } from "@playwright/test";

async function setup(page: import("@playwright/test").Page) {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "资料测试", role: "ADMIN" } }));
  await page.goto("profile");
  await page.getByRole("button", { name: "账号与安全", exact: true }).click();
  return page.getByRole("dialog", { name: "账号与安全" });
}

test("只填写当前密码不能假装保存成功或关闭表单", async ({ page }) => {
  const modal = await setup(page);
  await modal.getByLabel("当前密码", { exact: true }).fill("old-password");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(modal).toBeVisible();
  await expect(page.getByTestId("operation-feedback")).toContainText("请输入新密码");
});

test("短密码与空用户名显示明确失败提示而非静默原生校验", async ({ page }) => {
  const modal = await setup(page);
  await modal.getByLabel("当前密码", { exact: true }).fill("old-password");
  await modal.getByLabel("新密码", { exact: true }).fill("123");
  await modal.getByLabel("确认新密码", { exact: true }).fill("123");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(page.getByTestId("operation-feedback")).toContainText("密码至少需要6个字符");
  await modal.getByLabel("用户名", { exact: true }).fill("");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(page.getByTestId("operation-feedback")).toContainText("请输入用户名");
});

test("仅修改密码使用当前密码提交并用新密码恢复会话", async ({ page }) => {
  const modal = await setup(page);
  let saved = false;
  await page.route("**/auth/profile", route => {
    if (route.request().method() === "GET") return route.fulfill({ json: { id: 1, username: "资料测试", role: "ADMIN" } });
    expect(route.request().postDataJSON()).toMatchObject({ currentPassword: "old-password", password: "new-password" });
    saved = true; return route.fulfill({ status: 204 });
  });
  await page.route("**/auth/web-login", route => {
    expect(saved).toBe(true);
    expect(route.request().postDataJSON()).toEqual({ username: "资料测试", password: "new-password" });
    return route.fulfill({ json: { accessToken: "new" } });
  });
  await modal.getByLabel("当前密码", { exact: true }).fill("old-password");
  await modal.getByLabel("新密码", { exact: true }).fill("new-password");
  await modal.getByLabel("确认新密码", { exact: true }).fill("new-password");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(modal).toHaveCount(0);
  await expect(page.getByTestId("operation-feedback")).toContainText("账号资料已保存");
});

test("不完整与空白密码不提交，当前密码错误保留输入", async ({ page }) => {
  const modal = await setup(page);
  let writes = 0;
  await page.route("**/auth/profile", route => {
    if (route.request().method() === "GET") return route.fulfill({ json: { id: 1, username: "资料测试", role: "ADMIN" } });
    writes++; return route.fulfill({ status: 400, json: { message: "当前密码不正确" } });
  });
  const cases = [
    { password: "", confirmation: "new-password", current: "old-password", message: "请输入新密码" },
    { password: "      ", confirmation: "      ", current: "old-password", message: "新密码不能全部为空格" },
    { password: "new-password", confirmation: "new-password", current: "", message: "请输入当前密码" },
  ];
  for (const value of cases) {
    await modal.getByLabel("当前密码", { exact: true }).fill(value.current);
    await modal.getByLabel("新密码", { exact: true }).fill(value.password);
    await modal.getByLabel("确认新密码", { exact: true }).fill(value.confirmation);
    await modal.getByRole("button", { name: "保存更改" }).click();
    await expect(page.getByTestId("operation-feedback")).toContainText(value.message);
    expect(writes).toBe(0);
  }
  await modal.getByLabel("当前密码", { exact: true }).fill("incorrect-password");
  await modal.getByRole("button", { name: "保存更改" }).click();
  await expect(page.getByTestId("operation-feedback")).toContainText("当前密码不正确");
  await expect(modal.getByLabel("新密码", { exact: true })).toHaveValue("new-password");
  await expect(modal).toBeVisible();
  expect(writes).toBe(1);
});

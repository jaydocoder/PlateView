import { test, expect } from "@playwright/test";

for (const wechatAllowed of [false, true]) {
  test(wechatAllowed ? "有微信权限同时显示车辆和微信结果" : "无微信权限不影响普通车辆查询及详情", async ({ page }) => {
    await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
    await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "普通查询用户", role: "USER", wechatWorkOrderAccessEnabled: wechatAllowed } }));
    await page.route("**/work-orders/status", route => route.fulfill({ json: { sources: [] } }));
    const candidates = ["RESIDENT", "SCENIC_UNIT", "SCENIC_ENTERPRISE", "CADRE", "KANAS_TOURISM_DEVELOPMENT"].map((category, index) => ({ id: index + 1, plateNumber: `新H1234${index}`, category, status: "ACTIVE", detailAccessible: true }));
    await page.route("**/vehicles/search?*", route => route.fulfill({ json: { candidates } }));
    await page.route("**/work-orders/home-search?*", route => wechatAllowed
      ? route.fulfill({ json: { workOrderCandidates: [{ id: 1, orderNumber: "123", plateNumbers: ["新H12340"] }], wechatMessages: [{ id: 2, rawContent: "允许的聊天内容", plateNumbers: ["新H12340"] }] } })
      : route.fulfill({ status: 403, json: { code: "WORK_ORDER_PERMISSION_DENIED", message: "当前账号没有微信车单访问权限" } }));
    await page.route("**/vehicles/1", route => route.fulfill({ json: candidates[0] }));
    await page.goto("search");
    await page.locator(".home-fixed-tools input").fill("1234");
    await expect(page.locator(".result-section").filter({ has: page.getByRole("heading", { name: "车辆档案", exact: true }) }).locator(".result-row")).toHaveCount(5);
    await expect(page.getByText("当前账号没有微信车单访问权限", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "微信车单", exact: true })).toHaveCount(wechatAllowed ? 1 : 0);
    await expect(page.getByRole("heading", { name: "微信聊天记录", exact: true })).toHaveCount(wechatAllowed ? 1 : 0);
    await expect(page.getByText("其他长期车辆", { exact: true })).toHaveCount(0);
    await page.locator(".result-section").filter({ has: page.getByRole("heading", { name: "车辆档案", exact: true }) }).locator(".result-row").first().click();
    await expect(page).toHaveURL(/\/vehicle\/1$/);
    await expect(page.getByText("村民车辆", { exact: true })).toBeVisible();
  });
}

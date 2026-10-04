import { test, expect } from "@playwright/test";

test("车辆型号为空时不显示占位行，有值时显示型号", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "测试管理员", role: "ADMIN" } }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
  await page.route("**/admin/vehicles/creation-capabilities", route => route.fulfill({ json: { creatableCategories: ["OTHER_LONG_TERM"], canChangeVehicleCategory: false } }));
  await page.route("**/admin/vehicles?*", route => route.fulfill({ json: {
    items: [null, undefined, "", "  ", " 丰田 "].map((vehicleType, index) => ({ id: index + 1, plateNumber: `新H1234${index}`, category: "RESIDENT", status: "ACTIVE", vehicleType })), total: 5,
  } }));
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "车辆档案", exact: true }).click();
  const rows = page.locator(".vehicle-admin-card");
  await expect(rows).toHaveCount(5);
  for (let index = 0; index < 4; index++) {
    await expect(rows.nth(index).locator(".vehicle-admin-copy small")).toHaveCount(0);
  }
  await expect(rows.nth(4).locator(".vehicle-admin-copy small")).toHaveText("丰田");
  await expect(page.getByText("未填写车辆类型", { exact: true })).toHaveCount(0);
});

test("车辆列表全部筛选不发送空状态，其他筛选发送有效枚举", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "测试管理员", role: "ADMIN" } }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
  await page.route("**/admin/vehicles/creation-capabilities", route => route.fulfill({ json: { creatableCategories: ["OTHER_LONG_TERM"], canChangeVehicleCategory: false } }));
  const requests: URL[] = [];
  await page.route("**/admin/vehicles?*", route => {
    const url = new URL(route.request().url());
    requests.push(url);
    const status = url.searchParams.get("status");
    // 与当前运行后端相同：缺省表示全部，空字符串不是合法枚举。
    if (status !== null && !["ACTIVE", "STRICT_CHECK", "BLACKLISTED", "INACTIVE", "DELETED"].includes(status)) {
      return route.fulfill({ status: 400, json: { message: "车辆状态无效" } });
    }
    return route.fulfill({ json: { items: [{ id: 1, plateNumber: "新H12345", category: "OTHER_LONG_TERM", status: status || "ACTIVE", version: 1 }], total: 1 } });
  });
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "车辆档案", exact: true }).click();
  await expect(page.locator(".vehicle-search input")).toBeVisible();
  await expect(page.getByText("车辆状态无效", { exact: true })).toHaveCount(0);
  await expect(page.locator(".vehicle-total-row")).toContainText("共 1 辆");
  for (const [label, status] of [["启用", "ACTIVE"], ["严查", "STRICT_CHECK"], ["拉黑", "BLACKLISTED"], ["失效", "INACTIVE"], ["删除", "DELETED"], ["全部", null]]) {
    const count = requests.length;
    await page.locator(".vehicle-filter-chips").getByRole("button", { name: label!, exact: true }).click();
    await expect.poll(() => requests.slice(count).some(url => url.searchParams.get("status") === status)).toBe(true);
    await expect(page.getByText("车辆状态无效", { exact: true })).toHaveCount(0);
  }
  await page.locator(".vehicle-search input").fill("H12345");
  await expect.poll(() => requests.at(-1)?.searchParams.get("keyword")).toBe("H12345");
  expect(requests.at(-1)!.searchParams.has("status")).toBe(false);
});

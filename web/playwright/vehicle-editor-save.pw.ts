import { test, expect } from "@playwright/test";

const categories = ["RESIDENT", "SCENIC_UNIT", "SCENIC_ENTERPRISE", "KANAS_TOURISM_DEVELOPMENT", "CADRE", "OTHER_LONG_TERM"];
const allowedAttributes = ["vehicleUse", "passageArea", "position", "brandModel", "approvedCapacity", "plateColor"];

for (const category of categories) {
  test(`${category} 车辆编辑可保存备注及所属位置`, async ({ page }) => {
    let saved: Record<string, any> | undefined;
    let rejectNextSave = category === "RESIDENT";
    await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
    await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
    await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
    await page.route("**/admin/vehicles/creation-capabilities", route => route.fulfill({ json: { creatableCategories: categories, canChangeVehicleCategory: true } }));
    const detail = { id: 7, plateNumber: "新H12345", category, status: "ACTIVE", version: 3, attributes: { position: "原位置", plateColor: "黄色" }, residentProfile: category === "RESIDENT" ? { ownerName: "测试村民", identityCardNumber: "测试证件", remarks: "原备注" } : null, longTermProfile: category !== "RESIDENT" ? { organizationName: "测试单位", remarks: "原备注" } : null };
    await page.route("**/admin/vehicles?*", route => route.fulfill({ json: { items: [detail], total: 1 } }));
    await page.route("**/admin/vehicles/7", route => {
      if (route.request().method() === "PUT") {
        const body = route.request().postDataJSON();
        if (Object.keys(body.attributes).some(key => !allowedAttributes.includes(key))) {
          return route.fulfill({ status: 400, json: { message: "存在不支持的车辆附加字段" } });
        }
        if (rejectNextSave) {
          rejectNextSave = false;
          return route.fulfill({ status: 503, json: { message: "服务暂不可用" } });
        }
        saved = body;
        expect(route.request().headers()["if-match-version"]).toBe(String(detail.version));
        Object.assign(detail, body, { version: detail.version + 1 });
        return route.fulfill({ json: detail });
      }
      return route.fulfill({ json: detail });
    });
    await page.goto("admin");
    await page.locator(".admin-tabs").getByRole("button", { name: "车辆档案", exact: true }).click();
    await page.locator(".vehicle-admin-card").click();
    const editor = page.locator(".vehicle-editor-card");
    await expect(editor.locator("textarea")).toHaveValue("原备注");
    await editor.locator("textarea").fill("修改后的备注");
    await editor.getByRole("button", { name: "保存编辑", exact: true }).click();
    if (category === "RESIDENT") {
      await expect(editor.getByText("服务暂不可用", { exact: true })).toBeVisible();
      await expect(editor.locator("textarea")).toHaveValue("修改后的备注");
      await editor.getByRole("button", { name: "保存编辑", exact: true }).click();
    }
    await expect.poll(() => saved, { timeout: 3000 }).toBeTruthy();
    expect(saved!.attributes).toEqual({ position: "原位置", plateColor: "黄色" });
    expect(saved![category === "RESIDENT" ? "residentProfile" : "longTermProfile"].remarks).toBe("修改后的备注");
    await expect(page.getByText("存在不支持的车辆附加字段", { exact: true })).toHaveCount(0);
    await expect(editor).toHaveCount(0);
    await page.locator(".admin-tabs").getByRole("button", { name: "车辆档案", exact: true }).click();
    await page.locator(".vehicle-admin-card").click();
    await expect(editor.locator("textarea")).toHaveValue("修改后的备注");
    await editor.locator("textarea").fill("");
    await editor.getByRole("button", { name: "保存编辑", exact: true }).click();
    await expect.poll(() => saved![category === "RESIDENT" ? "residentProfile" : "longTermProfile"].remarks).toBeNull();
  });

  test(`${category} 新增车辆使用相同字段协议`, async ({ page }) => {
    let saved: Record<string, any> | undefined;
    await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
    await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
    await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
    await page.route("**/admin/vehicles/creation-capabilities", route => route.fulfill({ json: { creatableCategories: categories, canChangeVehicleCategory: true } }));
    await page.route("**/admin/vehicles?*", route => route.fulfill({ json: { items: [], total: 0 } }));
    await page.route("**/admin/vehicles", route => {
      const body = route.request().postDataJSON();
      if (Object.keys(body.attributes).some(key => !allowedAttributes.includes(key))) {
        return route.fulfill({ status: 400, json: { message: "存在不支持的车辆附加字段" } });
      }
      saved = body;
      return route.fulfill({ json: { ...body, id: 8, version: 1 } });
    });
    await page.goto("admin");
    await page.locator(".admin-tabs").getByRole("button", { name: "车辆档案", exact: true }).click();
    await page.getByRole("button", { name: "+ 新增车辆", exact: true }).click();
    const editor = page.locator(".vehicle-editor-card");
    await editor.getByLabel("车牌号码", { exact: true }).fill("新H12345");
    await expect(editor.getByRole("combobox", { name: "所属类别", exact: true })).toBeEnabled();
    await editor.getByRole("combobox", { name: "所属类别", exact: true }).selectOption(category);
    if (category === "RESIDENT") {
      await editor.getByLabel("姓名", { exact: true }).fill("测试村民");
      await editor.getByLabel("身份证号", { exact: true }).fill("测试证件");
    } else {
      await editor.getByLabel("单位名称", { exact: true }).fill("测试单位");
    }
    await editor.getByLabel("所属位置", { exact: true }).fill(" 测试位置 ");
    await editor.locator("textarea").fill("新增备注");
    await editor.getByRole("button", { name: "新增车辆", exact: true }).click();
    await expect.poll(() => saved).toBeTruthy();
    expect(saved!.attributes).toEqual({ position: "测试位置" });
    expect(saved![category === "RESIDENT" ? "residentProfile" : "longTermProfile"].remarks).toBe("新增备注");
  });
}

import { test, expect } from "@playwright/test";

for (const scenario of ["未设置", "请求失败", "图片损坏", "已设置"]) {
  test(`当前用户头像：${scenario}`, async ({ page }, testInfo) => {
    await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
    await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "头像测试", role: "USER", hasAvatar: scenario !== "未设置" } }));
    let avatarRequests = 0;
    await page.route("**/auth/profile/avatar", route => {
      avatarRequests++;
      if (scenario === "请求失败") return route.fulfill({ status: 404 });
      if (scenario === "图片损坏") return route.fulfill({ contentType: "image/png", body: "损坏图片" });
      return route.fulfill({ contentType: "image/svg+xml", body: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#087b8a"/></svg>' });
    });
    await page.goto("profile");
    const areas = [page.locator(".user-avatar-button"), page.locator(".sidebar .user-chip .avatar"), page.locator(".profile-identity .avatar")];
    for (const area of areas) {
      const img = area.locator("img");
      await expect(img).toHaveCount(1);
      await expect(img).toHaveAttribute("src", scenario === "已设置" ? /^blob:/ : /\/web\/app-icon\.png$/);
      await expect.poll(() => img.evaluate((node: HTMLImageElement) => node.complete && node.naturalWidth > 0)).toBe(true);
    }
    if (scenario === "未设置") expect(avatarRequests).toBe(0);
    else expect(avatarRequests).toBeGreaterThan(0);
    await page.screenshot({ path: testInfo.outputPath("用户头像.png") });
  });
}

import { test, expect } from "@playwright/test";

test("首页标题与搜索工具栏无缝同色并保持固定", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "移动端", "桌面端首页标题位于单个工具栏内部");
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "顶部测试", role: "USER" } }));
  await page.route("**/work-orders/status", route => route.fulfill({ json: { sources: [{ sourceKey: "test", status: "HEALTHY" }] } }));
  for (const width of [320, 375, 390, 430, 760]) {
    await page.setViewportSize({ width, height: 844 });
    await page.goto("search");
    await expect(page.locator(".home-fixed-tools .search-box")).toBeVisible();
    const geometry = await page.evaluate(() => {
      const header = document.querySelector(".mobile-header")!;
      const tools = document.querySelector(".home-fixed-tools")!;
      const search = tools.querySelector(".search-box")!;
      return {
        headerBottom: header.getBoundingClientRect().bottom,
        toolsTop: tools.getBoundingClientRect().top,
        searchTop: search.getBoundingClientRect().top,
        colors: [header, tools, search].map(node => getComputedStyle(node).backgroundColor),
        filters: [header, tools].map(node => getComputedStyle(node).backdropFilter),
        positions: [header, tools].map(node => getComputedStyle(node).position),
        borders: [header, tools].map(node => getComputedStyle(node).borderBottomWidth),
      };
    });
    expect(geometry.toolsTop).toBe(geometry.headerBottom);
    expect(geometry.searchTop).toBe(geometry.headerBottom);
    expect(new Set(geometry.colors).size).toBe(1);
    expect(geometry.filters).toEqual(["none", "none"]);
    expect(geometry.positions).toEqual(["fixed", "fixed"]);
    expect(geometry.borders).toEqual(["0px", "0px"]);
    await page.screenshot({ path: testInfo.outputPath(`首页顶部-${width}.png`) });
  }
});

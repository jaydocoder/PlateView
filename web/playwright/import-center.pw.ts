import { test, expect, type Page } from "@playwright/test";

const fileName = "2026年哈纳斯村村民车辆统计表10.02更新（副本）（1）（2）.xlsx";
const makeRow = (id: number, plannedAction = "UPDATE", resolution = "PENDING") => ({ id, plateNumber: `新A${String(id).padStart(5, "0")}`, sourceSheetName: "村民车辆", sourceRowNumber: plannedAction === "DEACTIVATE" ? 0 : id + 1, primarySubject: `档案${id}`, plannedAction, resultStatus: plannedAction === "NONE" ? "ERROR" : "VALID", resolution, warningMessage: plannedAction === "UPDATE" ? "正式库存在同车牌数据，请确认是否更新" : null, errorMessage: plannedAction === "NONE" ? "车牌号格式不规范或无法拆分" : null });

async function setup(page: Page, options: { count?: number; publishError?: boolean; detailError?: boolean; uploadError?: boolean; role?: string } = {}) {
  const rows = options.count ? Array.from({ length: options.count }, (_, index) => makeRow(index + 1)) : [makeRow(1, "CREATE"), makeRow(2), makeRow(3, "REACTIVATE"), makeRow(4, "DEACTIVATE"), makeRow(5, "NONE", "ERROR")];
  let status = "VALIDATED", detailFailed = false, uploadFailed = false;
  const writes: Array<{ path: string; body: any }> = [], reads: string[] = [];
  const batch = (filter = "REVIEW", offset = 0) => {
    const filtered = rows.filter(row => filter === "REVIEW" || (filter === "ERROR" ? row.resultStatus === "ERROR" : row.plannedAction === filter));
    return { id: 7, sourceFileName: fileName, createdAt: "2026-10-02T16:15:00Z", status, rowTotal: filtered.length, rows: filtered.slice(offset, offset + 50), stats: { totalRows: rows.length, newRows: rows.filter(row => row.plannedAction === "CREATE").length, updateRows: rows.filter(row => row.plannedAction === "UPDATE").length, reactivateRows: rows.filter(row => row.plannedAction === "REACTIVATE").length, deactivateRows: rows.filter(row => row.plannedAction === "DEACTIVATE").length, errorRows: rows.filter(row => row.resultStatus === "ERROR").length, duplicateRows: 9, pendingReviewRows: rows.filter(row => row.resolution === "PENDING").length, publishableRows: rows.filter(row => row.resolution === "PUBLISH").length } };
  };
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-session" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "导入管理员", role: options.role || "ADMIN" } }));
  await page.route("http://127.0.0.1:4175/admin/imports**", async route => {
    const url = new URL(route.request().url()), path = url.pathname, method = route.request().method();
    if (method === "GET") reads.push(url.pathname + url.search);
    else writes.push({ path, body: path.endsWith("preview") ? route.request().postData() : route.request().postDataJSON() });
    if (path === "/admin/imports") return route.fulfill({ json: { items: [{ ...batch(), totalRows: rows.length, errorRows: 1 }] } });
    if (path.endsWith("/preview")) {
      if (options.uploadError && !uploadFailed) { uploadFailed = true; return route.fulfill({ status: 422, json: { message: "Excel 表头无法识别", code: "IMPORT_INVALID" } }); }
      return route.fulfill({ status: 201, json: batch() });
    }
    if (/\/rows\/\d+$/.test(path)) {
      if (options.detailError && !detailFailed) { detailFailed = true; return route.fulfill({ status: 503, json: { message: "差异详情暂不可用" } }); }
      const row = rows.find(row => row.id === Number(path.split("/").pop()));
      return route.fulfill({ json: { row, sections: [{ title: "车辆信息", fields: [{ label: "备注", before: "原备注", after: "新备注" }, { label: "车辆型号", before: null, after: "客车" }] }], sourceValues: [{ label: "车主姓名", value: "巴依尔" }, { label: "备注", value: "新备注" }] } });
    }
    if (path.endsWith("/resolutions")) {
      for (const change of route.request().postDataJSON().rows) { const row = rows.find(row => row.id === change.rowId); if (row) row.resolution = change.resolution; }
      return route.fulfill({ json: batch() });
    }
    if (path.endsWith("/publish") || path.endsWith("/rollback")) {
      if (options.publishError) return route.fulfill({ status: 409, json: { code: "IMPORT_CONFLICT", message: "档案已被其他管理员修改，请重新核对" } });
      status = path.endsWith("/publish") ? "PUBLISHED" : "ROLLED_BACK";
      return route.fulfill({ json: batch() });
    }
    return route.fulfill({ json: batch(url.searchParams.get("filter") || "REVIEW", Number(url.searchParams.get("offset") || 0)) });
  });
  return { rows, writes, reads };
}

const center = (page: Page) => page.getByRole("region", { name: "导入中心", exact: true });
const rowCard = (page: Page, id: number) => page.locator(".import-row-card").filter({ hasText: `新A${String(id).padStart(5, "0")}` });
async function open(page: Page) {
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "导入中心", exact: true }).click();
  await page.locator(".import-batch-card").click();
  await expect(center(page).getByRole("heading", { name: "数据差异核对" })).toBeVisible();
}

test("上传后打开预览并逐条核对、发布、撤销和重新发布", async ({ page }) => {
  const state = await setup(page);
  await page.goto("admin");
  await page.locator(".admin-tabs").getByRole("button", { name: "导入中心", exact: true }).click();
  await page.getByLabel("上传 Excel 文件").setInputFiles({ name: "村民车辆.xlsx", mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buffer: Buffer.from("模拟上传内容") });
  await expect(page).toHaveURL(/importBatch=7/);
  await expect(center(page).getByRole("button", { name: "正式发布数据" })).toBeDisabled();
  for (const [id, label] of [[1, "确认新增"], [2, "确认更新"], [3, "确认恢复"], [4, "保留有效"]] as const) {
    await rowCard(page, id).click();
    await expect(center(page).getByText("原备注", { exact: true })).toBeVisible();
    await expect(center(page).getByText("新备注", { exact: true }).first()).toBeVisible();
    await expect(center(page).getByRole("heading", { name: "Excel 源字段" })).toBeVisible();
    await center(page).getByRole("button", { name: label, exact: true }).click();
    await expect(page).not.toHaveURL(/importRow=/);
  }
  await center(page).getByRole("button", { name: "正式发布数据" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "取消", exact: true }).click();
  expect(state.writes.filter(write => write.path.endsWith("/publish"))).toHaveLength(0);
  await center(page).getByRole("button", { name: "正式发布数据" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "确认发布", exact: true }).click();
  await expect(center(page).getByText("已发布", { exact: true })).toBeVisible();
  await center(page).getByRole("button", { name: "撤销发布", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "确认撤销", exact: true }).click();
  await expect(center(page).getByText("已撤销", { exact: true })).toBeVisible();
  await center(page).getByRole("button", { name: "重新发布数据" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "确认发布", exact: true }).click();
  await expect(center(page).getByText("已发布", { exact: true })).toBeVisible();
  expect(state.rows[3].resolution).toBe("SKIP");
  expect(state.writes.filter(write => write.path.endsWith("/publish"))).toHaveLength(2);
});

test("六种筛选请求、确认更新后保持筛选及异常只读", async ({ page }) => {
  const state = await setup(page); await open(page);
  for (const [filter, label] of [["CREATE", "新增"], ["UPDATE", "更新"], ["REACTIVATE", "恢复"], ["DEACTIVATE", "待失效"], ["ERROR", "异常"], ["REVIEW", "全部待核对"]]) {
    await page.getByLabel("核对类型").getByRole("button", { name: label, exact: true }).click();
    await expect.poll(() => state.reads.some(path => path.includes(`filter=${filter}&`))).toBe(true);
    await expect(center(page).getByText(filter === "REVIEW" ? "已加载 5 / 5 条" : "已加载 1 / 1 条", { exact: true })).toBeVisible();
  }
  await page.getByLabel("核对类型").getByRole("button", { name: "更新", exact: true }).click();
  await rowCard(page, 2).click();
  await center(page).getByRole("button", { name: "确认更新", exact: true }).click();
  await expect(page).toHaveURL(/importFilter=UPDATE/);
  await expect(page.locator(".import-row-card")).toHaveCount(1);
  await page.getByLabel("核对类型").getByRole("button", { name: "异常", exact: true }).click();
  await rowCard(page, 5).click();
  await expect(center(page).getByText("异常行不可发布", { exact: true })).toBeVisible();
  await expect(center(page).getByRole("button", { name: "确认", exact: true })).toHaveCount(0);
});

test("失败保留批次、错误详情可重试、刷新和浏览器返回", async ({ page }) => {
  const state = await setup(page, { detailError: true, publishError: true });
  state.rows.forEach(row => { if (row.resultStatus !== "ERROR") row.resolution = "PUBLISH"; });
  await open(page); await rowCard(page, 2).click();
  await expect(center(page).locator(".import-error")).toContainText("差异详情暂不可用");
  await center(page).getByRole("button", { name: "重试", exact: true }).click();
  await expect(center(page).getByText("原备注", { exact: true })).toBeVisible();
  await page.reload();
  await expect(center(page).getByText("原备注", { exact: true })).toBeVisible();
  await page.goBack();
  await expect(center(page).getByRole("heading", { name: "数据差异核对" })).toBeVisible();
  await center(page).getByRole("button", { name: "正式发布数据" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "确认发布", exact: true }).click();
  await expect(page.getByRole("dialog").locator(".import-error")).toContainText("档案已被其他管理员修改");
  await page.getByRole("dialog").getByRole("button", { name: "取消", exact: true }).click();
  await expect(center(page).getByText("待发布", { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/importBatch=7/);
});

test("下滑分页不遗漏或重复档案", async ({ page }) => {
  const state = await setup(page, { count: 65 }); await open(page);
  await page.locator(".import-row-card").last().scrollIntoViewIfNeeded();
  await expect.poll(() => state.reads.some(path => path.includes("offset=50"))).toBe(true);
  await expect(page.locator(".import-row-card")).toHaveCount(65);
  await expect(rowCard(page, 1)).toHaveCount(1);
  await expect(rowCard(page, 65)).toHaveCount(1);
});

test("上传校验和服务器识别失败均可重试", async ({ page }) => {
  const state = await setup(page, { uploadError: true });
  await page.goto("admin"); await page.locator(".admin-tabs").getByRole("button", { name: "导入中心", exact: true }).click();
  await page.getByLabel("上传 Excel 文件").setInputFiles({ name: "车辆.txt", mimeType: "text/plain", buffer: Buffer.from("无效") });
  await expect(page.getByTestId("operation-feedback")).toContainText("请选择不超过10MiB");
  expect(state.writes).toHaveLength(0);
  await page.getByLabel("上传 Excel 文件").setInputFiles({ name: "车辆.xlsx", mimeType: "application/octet-stream", buffer: Buffer.from("模拟") });
  await expect(center(page).locator(".import-error")).toContainText("Excel 表头无法识别");
  await page.getByLabel("上传 Excel 文件").setInputFiles({ name: "车辆.xlsx", mimeType: "application/octet-stream", buffer: Buffer.from("模拟") });
  await expect(page).toHaveURL(/importBatch=7/);
});

for (const width of [320, 375, 390, 430, 768, 1280, 1440]) {
  test(`${width}宽度下批次、核对、字段详情无横向溢出`, async ({ page }) => {
    await page.setViewportSize({ width, height: 932 }); await setup(page); await open(page);
    await expect(page.locator(".import-row-card")).toHaveCount(5);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/import-review-${width}.png`, fullPage: true });
    await rowCard(page, 2).click();
    await expect(center(page).getByRole("heading", { name: "Excel 源字段" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/import-detail-${width}.png`, fullPage: true });
  });
}

test("批次列表逐页读取并准确显示最终数量与北京时间", async ({ page }) => {
  await setup(page);
  const offsets: number[] = [];
  await page.route("http://127.0.0.1:4175/admin/imports?**", route => {
    const offset = Number(new URL(route.request().url()).searchParams.get("offset")); offsets.push(offset);
    return route.fulfill({ json: { items: Array.from({ length: offset === 0 ? 50 : 6 }, (_, index) => ({ id: offset + index + 1, sourceFileName: `村民车辆${offset + index + 1}.xlsx`, createdAt: "2026-10-02T16:15:00Z", status: "PUBLISHED", totalRows: 100, errorRows: 0 })) } });
  });
  await page.goto("admin"); await page.locator(".admin-tabs").getByRole("button", { name: "导入中心", exact: true }).click();
  await expect(center(page).getByText("已加载 50 个批次", { exact: true })).toBeVisible();
  await expect(page.locator(".import-batch-card").first().locator("time")).toContainText("00:15:00");
  await page.locator(".import-batch-card").last().scrollIntoViewIfNeeded();
  await expect(page.locator(".import-batch-card")).toHaveCount(56);
  await expect(center(page).getByText("56 个批次", { exact: true })).toHaveCount(1);
  expect(offsets.filter(offset => offset > 0)).toEqual([50]);
});

test("批次加载失败显示重试而不伪造空列表", async ({ page }) => {
  await setup(page); let unavailable = true;
  await page.route("http://127.0.0.1:4175/admin/imports?**", route => {
    if (unavailable) return route.fulfill({ status: 503, json: { message: "导入批次暂不可用" } });
    return route.fulfill({ json: { items: [] } });
  });
  await page.goto("admin"); await page.locator(".admin-tabs").getByRole("button", { name: "导入中心", exact: true }).click();
  await expect(center(page).locator(".import-error")).toContainText("导入批次暂不可用");
  await expect(center(page).getByText("暂无导入批次", { exact: true })).toHaveCount(0);
  unavailable = false;
  await center(page).getByRole("button", { name: "重试", exact: true }).click();
  await expect(center(page).getByText("暂无导入批次", { exact: true })).toBeVisible();
});

test("确认失败保留差异详情并可再次确认", async ({ page }) => {
  const state = await setup(page); let failed = false;
  await page.route("**/admin/imports/7/rows/resolutions", route => {
    if (!failed) { failed = true; return route.fulfill({ status: 409, json: { message: "核对状态保存失败，请重试" } }); }
    return route.fallback();
  });
  await open(page); await rowCard(page, 2).click();
  await center(page).getByRole("button", { name: "确认更新", exact: true }).click();
  await expect(center(page).locator(".import-error")).toContainText("核对状态保存失败");
  await expect(page).toHaveURL(/importRow=2/);
  expect(state.rows[1].resolution).toBe("PENDING");
  await center(page).getByRole("button", { name: "确认更新", exact: true }).click();
  await expect(page).not.toHaveURL(/importRow=/);
  expect(state.rows[1].resolution).toBe("PUBLISH");
});

test("撤销失败保持已发布状态和当前会话", async ({ page }) => {
  const state = await setup(page);
  state.rows.forEach(row => { if (row.resultStatus !== "ERROR") row.resolution = "PUBLISH"; });
  await page.route("**/admin/imports/7/rollback", route => route.fulfill({ status: 409, json: { message: "该档案已发生后续修改，无法撤销" } }));
  await open(page); await center(page).getByRole("button", { name: "正式发布数据" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "确认发布", exact: true }).click();
  await center(page).getByRole("button", { name: "撤销发布", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "确认撤销", exact: true }).click();
  await expect(page.getByRole("dialog").locator(".import-error")).toContainText("无法撤销");
  await page.getByRole("dialog").getByRole("button", { name: "取消", exact: true }).click();
  await expect(center(page).getByText("已发布", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "登录系统", exact: true })).toHaveCount(0);
});

test("快速切换筛选不显示旧分类的迟到响应", async ({ page }) => {
  const state = await setup(page); let started = false;
  await page.route("**/admin/imports/7?filter=CREATE&**", async route => {
    started = true;
    await new Promise(resolve => setTimeout(resolve, 450));
    await route.fulfill({ json: { id: 7, status: "VALIDATED", sourceFileName: fileName, stats: {}, rowTotal: 1, rows: [state.rows[0]] } }).catch(() => undefined);
  });
  await open(page);
  await page.getByLabel("核对类型").getByRole("button", { name: "新增", exact: true }).click();
  await expect.poll(() => started).toBe(true);
  await page.getByLabel("核对类型").getByRole("button", { name: "异常", exact: true }).click();
  await expect(rowCard(page, 5)).toBeVisible();
  await page.waitForTimeout(500);
  await expect(page.locator(".import-row-card")).toHaveCount(1);
  await expect(rowCard(page, 1)).toHaveCount(0);
});

test("普通用户不能从查询参数进入导入中心", async ({ page }) => {
  const state = await setup(page, { role: "USER" });
  await page.goto("admin?importBatch=7");
  await expect(page.locator(".app-shell")).toBeVisible();
  await expect(center(page)).toHaveCount(0);
  expect(state.reads).toHaveLength(0);
});

test("空文件和超限文件在上传前被阻止", async ({ page }) => {
  const state = await setup(page);
  await page.goto("admin"); await page.locator(".admin-tabs").getByRole("button", { name: "导入中心", exact: true }).click();
  for (const size of [0, 10 * 1024 * 1024 + 1]) {
    await page.getByLabel("上传 Excel 文件").setInputFiles({ name: "车辆.xlsx", mimeType: "application/octet-stream", buffer: Buffer.alloc(size) });
    await expect(page.getByTestId("operation-feedback")).toContainText("请选择不超过10MiB");
  }
  expect(state.writes).toHaveLength(0);
});

test("暗色与减少动画下详情可读且操作不遮挡导航", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "dark", reducedMotion: "reduce" });
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page); await open(page); await rowCard(page, 2).click();
  await expect(center(page)).toHaveCSS("background-color", "rgb(32, 59, 46)");
  await expect(center(page).getByRole("button", { name: "确认更新", exact: true })).toBeInViewport();
  const actions = await page.locator(".import-bottom-actions").boundingBox(), dock = await page.locator(".bottom-nav").boundingBox();
  expect(actions && dock && actions.y + actions.height <= dock.y + 1).toBe(true);
  await page.screenshot({ path: "test-results/import-detail-dark.png" });
});

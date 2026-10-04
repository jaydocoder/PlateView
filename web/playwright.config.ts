import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./playwright",
  testMatch: "**/*.pw.ts",
  timeout: 15_000,
  use: { baseURL: "http://127.0.0.1:4175/web/", trace: "retain-on-failure" },
  webServer: { command: "npm run dev -- --host 127.0.0.1 --port 4175", url: "http://127.0.0.1:4175/web/", reuseExistingServer: true },
  projects: [
    { name: "移动端", use: { ...devices["iPhone 13"] } },
    { name: "桌面端", use: { ...devices["Desktop Chrome"] } },
  ],
});

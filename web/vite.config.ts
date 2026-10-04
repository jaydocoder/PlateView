import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/web/",
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/auth": "http://127.0.0.1:8080",
      "/vehicles": "http://127.0.0.1:8080",
      "/work-orders": "http://127.0.0.1:8080",
      "/schedule": "http://127.0.0.1:8080",
      "/statistics": "http://127.0.0.1:8080",
      "/admin": "http://127.0.0.1:8080",
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
    cssCodeSplit: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules/react") || id.includes("node_modules/react-dom") || id.includes("node_modules/react-router")) return "react";
          if (id.includes("node_modules/motion")) return "motion";
          if (id.includes("node_modules/lucide-react")) return "icons";
          return undefined;
        },
      },
    },
  },
});

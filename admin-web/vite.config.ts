import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Веб-панель суперадмина (ТЗ §7).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
});

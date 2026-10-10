import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "./",
  plugins: [react()],
  build: {
    outDir: "../../app/src/main/assets/openui",
    emptyOutDir: true,
    sourcemap: false,
    assetsInlineLimit: 8192,
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
        entryFileNames: "assets/openui.js",
        chunkFileNames: "assets/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash][extname]"
      }
    }
  }
});
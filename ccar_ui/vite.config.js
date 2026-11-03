import {defineConfig} from "vite";
import react from "@vitejs/plugin-react";

// The browser calls /ccar/... on the Vite origin and Vite forwards it to the Java service,
// so no CORS configuration is needed on the backend in dev environment.
export default defineConfig({
    plugins: [react()],
    server: {proxy: {"/ccar": {target: "http://localhost:8081", changeOrigin: true}}},
});

import {defineConfig, loadEnv} from "vite";
import react from "@vitejs/plugin-react";

// The browser calls /ccar/... and /users/... on the Vite origin and Vite forwards them to the API gateway,
// so no CORS configuration is needed in the dev environment. Going through the gateway (not straight to
// ccar-core) means every call is checked against the JWT from the sign-in page.
// Override the target with VITE_GATEWAY_URL in .env.development.local if the gateway runs elsewhere.
export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), "");
    const gateway = env.VITE_GATEWAY_URL || "http://localhost:8089";
    return {
        plugins: [react()],
        server: {
            proxy: {
                "/ccar": {target: gateway, changeOrigin: true},
                "/users": {target: gateway, changeOrigin: true},
            },
        },
    };
});

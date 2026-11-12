import React from "react";
import {createRoot} from "react-dom/client";
import AuthGate from "./auth/AuthGate.jsx";
import ErrorBoundary from "./components/ErrorBoundary.jsx";
import "./styles.css";

createRoot(document.getElementById("root")).render(
    <React.StrictMode><ErrorBoundary><AuthGate/></ErrorBoundary></React.StrictMode>
);
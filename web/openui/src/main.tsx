import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { installKeyboardShellLock } from "./viewport";

installKeyboardShellLock();

const root = document.getElementById("root");
if (!root) throw new Error("Missing OpenUI root");
createRoot(root).render(<StrictMode><App /></StrictMode>);
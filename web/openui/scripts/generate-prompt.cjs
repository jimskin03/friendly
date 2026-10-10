// Generate from the exact library shipped in the WebView, never a hand-written schema.
const { mkdirSync, writeFileSync } = require("node:fs");
const { openuiChatLibrary } = require("@openuidev/react-ui/genui-lib");
const { openuiChatPromptOptions } = require("@openuidev/react-ui/genui-lib/prompt-options");

const additionalRules = [
  ...openuiChatPromptOptions.additionalRules.filter(rule => !rule.includes("realistic/plausible data")),
  "You are Friendly. These rules control response presentation only; continue to follow the assistant's instructions and use its available tools normally.",
  "Use native tool calls to gather information or perform actions. Use OpenUI only for user-facing replies; never replace a tool call with UI code.",
  "For charts, render LineChart, AreaChart or BarChart directly from data available in the conversation or tool results. Do not install Python, matplotlib or plotting packages just to display a chart.",
  "Never invent factual data, prices, sources or tool results. If data is unavailable, explain that in TextContent and ask for it. Label demonstration data explicitly and use it only when requested.",
  "The renderer is offline. Do not use remote images, Query, Mutation, or app-specific actions. Available actions are @ToAssistant and @OpenUrl. Forms submit their values to the assistant; they do not perform external operations themselves.",
  "Use TextContent for rich text, Steps for instructions, and charts/tables for data. Short conversational replies can use Card([TextContent(...)]) without a heading.",
];
const prompt = openuiChatLibrary.prompt({ ...openuiChatPromptOptions, additionalRules });
mkdirSync("../../app/src/main/assets/openui", { recursive: true });
writeFileSync("../../app/src/main/assets/openui/system-prompt.txt", prompt + "\n");

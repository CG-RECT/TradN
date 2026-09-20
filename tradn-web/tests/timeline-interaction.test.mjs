import assert from "node:assert/strict";
import { test } from "node:test";
import ts from "typescript";
import { readFile } from "node:fs/promises";

// 直接编译交互判断纯函数，避免依赖浏览器 DOM 环境。
const source = await readFile(
  new URL("../src/utils/timelineInteraction.ts", import.meta.url),
  "utf8",
);
const javascript = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext },
}).outputText;
const { isTimelineInteractiveTarget } = await import(
  `data:text/javascript;base64,${Buffer.from(javascript).toString("base64")}`
);

test("添加按钮和表单控件不会触发时间线拖动捕获", () => {
  const button = { closest: (selector) => selector.includes("button") };
  const input = { closest: (selector) => selector.includes("input") };

  assert.equal(isTimelineInteractiveTarget(button), true);
  assert.equal(isTimelineInteractiveTarget(input), true);
});

test("普通时间线空白区域仍可发起拖动", () => {
  const blankArea = { closest: () => null };

  assert.equal(isTimelineInteractiveTarget(blankArea), false);
  assert.equal(isTimelineInteractiveTarget(null), false);
});

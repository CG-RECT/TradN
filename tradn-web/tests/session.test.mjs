import assert from "node:assert/strict";
import { test } from "node:test";
import ts from "typescript";
import { readFile } from "node:fs/promises";

// 测试纯函数时只在内存编译，不在源码旁生成构建文件。
const source = await readFile(new URL("../src/utils/session.ts", import.meta.url), "utf8");
const javascript = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext },
}).outputText;
const { isSessionFailure, safeReturnPath } = await import(
  `data:text/javascript;base64,${Buffer.from(javascript).toString("base64")}`
);

test("HTTP 与业务码中的 401 都触发重新认证", () => {
  assert.equal(isSessionFailure(401, undefined, "/auth/profile"), true);
  assert.equal(isSessionFailure(200, 401, "/trades"), true);
  assert.equal(isSessionFailure(400, 401, "/trades"), true);
});

test("权限不足、网络错误和登录密码错误不会被误判为会话过期", () => {
  assert.equal(isSessionFailure(403, 403, "/trades"), false);
  assert.equal(isSessionFailure(undefined, undefined, "/trades"), false);
  assert.equal(isSessionFailure(401, 401, "/auth/login"), false);
});

test("重新登录可以返回之前的站内页面", () => {
  assert.equal(safeReturnPath("/notes/123?mode=edit#content"), "/notes/123?mode=edit#content");
});

test("拒绝外部跳转和返回登录页的循环", () => {
  for (const path of [undefined, [], "https://example.org", "//example.org", "/\\example.org", "/login", "/login?redirect=/", "/\n/example.org"]) {
    assert.equal(safeReturnPath(path), "/");
  }
});

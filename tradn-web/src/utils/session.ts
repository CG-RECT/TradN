/** 将认证失效与业务拒绝区分开，403 不应使正常登录用户退出。 */
export function isSessionFailure(status: number | undefined, code: unknown, url: string) {
  const isLogin = url.split("?")[0].endsWith("/auth/login");
  return !isLogin && (status === 401 || code === 401);
}

/** 重新登录后只允许返回本站页面，防止查询参数被用来跳转外部网站。 */
export function safeReturnPath(value: unknown): string {
  if (
    typeof value !== "string" ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    /[\\\u0000-\u0020]/.test(value) ||
    /^\/login(?:[/?#]|$)/.test(value)
  ) {
    return "/";
  }
  return value;
}

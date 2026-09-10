import axios from "axios";
import { message } from "ant-design-vue";
import { isSessionFailure, safeReturnPath } from "../utils/session";

let redirectingToLogin = false;

/** 多个接口同时失效时只跳转一次，同时忽略旧请求对新登录会话的干扰。 */
function expireSession(authorization: unknown) {
  const currentToken = localStorage.getItem("tradn-token");
  if (currentToken && authorization !== `Bearer ${currentToken}`) return;
  if (redirectingToLogin) return;
  localStorage.removeItem("tradn-token");
  if (location.pathname === "/login") return;
  redirectingToLogin = true;
  const returnTo = safeReturnPath(location.pathname + location.search + location.hash);
  location.replace(`/login?reason=expired&redirect=${encodeURIComponent(returnTo)}`);
}

// 所有业务请求统一走同源 /api/v1，由开发代理或生产 Nginx 转发到后端。
const http = axios.create({ baseURL: "/api/v1", timeout: 30000 });

// 令牌只附加到本站 API 请求，不把令牌写入 URL、日志或业务参数。
http.interceptors.request.use((config) => {
  const token = localStorage.getItem("tradn-token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data;
    // 后端统一响应的 HTTP 状态可能为 200，业务失败仍需根据 code 拒绝 Promise。
    if (body && typeof body.code === "number" && body.code !== 0) {
      if (isSessionFailure(response.status, body.code, response.config.url || "")) {
        expireSession(response.config.headers?.Authorization);
      } else {
        message.error(
          `${body.message}${body.requestId ? `（${body.requestId}）` : ""}`,
        );
      }
      return Promise.reject(new Error(body.message));
    }
    return body?.data ?? body;
  },
  (error) => {
    // 401 表示令牌无效或已被强制下线，立即清理本地登录态，避免继续发送失效令牌。
    if (
      isSessionFailure(
        error.response?.status,
        error.response?.data?.code,
        error.config?.url || "",
      )
    ) {
      expireSession(error.config?.headers?.Authorization);
    } else {
      message.error(
        error.response?.data?.message ||
          (error.response?.status === 403 ? "没有操作权限，请联系管理员授权" : error.message) ||
          "请求失败",
      );
    }
    return Promise.reject(error);
  },
);

export default http;

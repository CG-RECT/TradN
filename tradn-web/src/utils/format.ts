import dayjs from "dayjs";

/** 将后端 ISO 时间统一显示为易读的本地时间，空值显示短横线。 */
export function formatDateTime(value: unknown): string {
  if (!value) {
    return "-";
  }
  const parsed = dayjs(String(value));
  return parsed.isValid() ? parsed.format("YYYY-MM-DD HH:mm:ss") : String(value);
}

/** 将布尔/数字数据库标记统一转换为是否文本。 */
export function formatBoolean(value: unknown): string {
  return value === true || value === 1 || value === "1" ? "是" : "否";
}

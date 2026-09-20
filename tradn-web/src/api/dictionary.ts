import http from "./http";

export interface DictionaryOption {
  code: string;
  value: string;
  label: string;
  color?: string;
}

const cache = new Map<string, Promise<DictionaryOption[]>>();

/**
 * 加载启用的业务字典项并在当前页面会话内复用请求。
 * 后端仍会校验和保存业务编码，字典只负责页面显示和选择。
 */
export function loadDictionary(typeCode: string): Promise<DictionaryOption[]> {
  const existing = cache.get(typeCode);
  if (existing) {
    return existing;
  }
  const request = http
    .get(`/dictionary-options/${typeCode}`)
    .then((rows: any) =>
      rows.map((row: any) => ({
        code: row.item_code,
        value: String(row.item_value),
        label: row.item_label,
        color: row.display_style,
      })),
    );
  cache.set(typeCode, request);
  return request;
}

/** 将字典项转换为 Ant Design Vue 下拉框选项。 */
export function toSelectOptions(options: DictionaryOption[]) {
  return options.map((item) => ({ value: item.value, label: item.label }));
}

/** 根据业务值获取字典显示文本，未知值保留原编码便于排查数据。 */
export function dictionaryLabel(
  options: DictionaryOption[],
  value: unknown,
): string {
  const normalized = String(value ?? "");
  return options.find((item) => item.value === normalized)?.label || normalized || "-";
}

/** 根据业务值获取字典配置的标签颜色。 */
export function dictionaryColor(
  options: DictionaryOption[],
  value: unknown,
): string {
  const normalized = String(value ?? "");
  return options.find((item) => item.value === normalized)?.color || "default";
}

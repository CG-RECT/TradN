/** 时间线中的这些元素优先响应自身点击，不参与外层横向拖动。 */
const INTERACTIVE_SELECTOR =
  "button, a, input, textarea, select, [role='button'], [contenteditable='true']";

interface ClosestTarget {
  closest: (selector: string) => unknown;
}

/** 判断指针按下位置是否属于按钮、链接或表单控件。 */
export function isTimelineInteractiveTarget(target: EventTarget | null): boolean {
  if (!target || !("closest" in target)) {
    return false;
  }

  const candidate = target as unknown as ClosestTarget;
  return typeof candidate.closest === "function" &&
    Boolean(candidate.closest(INTERACTIVE_SELECTOR));
}

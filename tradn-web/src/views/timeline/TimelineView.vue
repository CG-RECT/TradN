<template>
  <div class="page timeline-page">
    <div class="page-header">
      <div>
        <div class="page-title">黄金时间线</div>
        <div class="muted">
          默认显示今天前后各 10 天；按住鼠标拖动浏览，到达边缘时继续加载
        </div>
      </div>
      <a-space>
        <a-button :disabled="zoom <= MIN_ZOOM" @click="adjustZoom(-ZOOM_STEP)">
          缩小
        </a-button>
        <span class="zoom-label">{{ Math.round(zoom * 100) }}%</span>
        <a-button :disabled="zoom >= MAX_ZOOM" @click="adjustZoom(ZOOM_STEP)">
          放大
        </a-button>
        <a-button @click="centerToday">回到今天</a-button>
      </a-space>
    </div>

    <div class="page-actions timeline-actions"><span class="muted">横向拖动浏览日期，点击日期或卡片可补充当天记录</span></div>
    <div class="timeline-frame content-card">
      <a-button
        class="edge-button edge-left"
        shape="circle"
        :loading="loadingPrevious"
        @click="loadPrevious"
      >
        ‹
      </a-button>
      <div
        ref="shell"
        class="timeline-shell"
        :class="{ dragging }"
        @scroll="handleScroll"
        @wheel="scrollHorizontally"
        @pointerdown="startDrag"
        @pointermove="dragTimeline"
        @pointerup="endDrag"
        @pointercancel="endDrag"
      >
        <div class="timeline-grid" :style="{ width: `${gridWidth}px`, height: `${gridHeight}px` }">
          <article
            v-for="item in days"
            :key="item.date"
            class="day-column"
            :class="{ today: item.date === todayValue }"
            :style="dayStyle"
          >
            <button class="date-button" type="button" @click="openEditor(item.date)">
              <strong>{{ item.date.slice(5) }}</strong>
              <span>{{ weekday(item.date) }}</span>
            </button>
            <div class="timeline-dot" />
            <button class="date-add-button" type="button" title="添加当天备注" @click="openEditor(item.date)">
              +
            </button>

            <button v-if="!(item.data?.entries?.length)" class="day-add-hint" type="button" @click.stop="openEditor(item.date)">
              <strong>+ 添加当天记录</strong>
              <span>文字备注 · 图片备注 · 笔记备注</span>
            </button>
          </article>

          <template v-for="card in layoutEntries" :key="card.key">
            <button
              class="timeline-entry-card"
              :class="[`lane-${card.lane}`, `entry-${card.entry.entryType.toLowerCase()}`]"
              :style="entryStyle(card)"
              type="button"
              @click.stop="handleEntryClick(card.entry)"
            >
              <template v-if="card.entry.entryType === 'TEXT'">
                <span class="entry-type">文字备注</span>
                <span class="entry-content">{{ card.entry.content }}</span>
              </template>
              <template v-else-if="card.entry.entryType === 'IMAGE'">
                <img v-if="card.entry.file?.thumbnailUrl" :src="card.entry.file.thumbnailUrl" loading="lazy" alt="黄金走势图备注" />
                <span v-else class="entry-type">图片备注</span>
              </template>
              <template v-else>
                <span class="entry-type">笔记备注</span>
                <strong>{{ card.entry.note?.title || "关联笔记" }}</strong>
                <span v-if="card.entry.note?.summary" class="entry-content">{{ card.entry.note.summary }}</span>
              </template>
            </button>
          </template>
        </div>
      </div>
      <a-button
        class="edge-button edge-right"
        shape="circle"
        :loading="loadingNext"
        @click="loadNext"
      >
        ›
      </a-button>
    </div>

    <a-image
      v-if="previewUrl"
      :width="0"
      :height="0"
      :preview="{ visible: previewVisible, src: previewUrl, onVisibleChange: changePreview }"
    />

    <a-modal
      v-model:open="editorOpen"
      :title="`${editor.entryId ? '编辑' : '添加'} ${current.date} 备注`"
      width="760px"
      :confirm-loading="saving"
      @ok="saveEntry"
      @cancel="closeEditor"
    >
      <a-form layout="vertical">
        <a-form-item label="备注类型">
          <a-select v-model:value="editor.entryType" :disabled="Boolean(editor.entryId)" :options="entryTypeOptions" />
        </a-form-item>
        <div v-if="editor.entryType === 'TEXT'" class="editor-section text-note-section">
          <div class="editor-section-title">文字备注</div>
          <a-textarea v-model:value="editor.content" :rows="6" placeholder="记录当天看到的行情、消息和自己的判断……" />
        </div>
        <div v-else-if="editor.entryType === 'IMAGE'" class="editor-section image-note-section">
          <div class="editor-section-title">图片备注</div>
          <a-form-item label="上传当天走势图或其他资料图片">
            <a-upload
              :before-upload="beforeUpload"
              :file-list="uploadList"
              list-type="picture"
              multiple
            >
              <a-button :disabled="Boolean(editor.entryId)">选择图片</a-button>
            </a-upload>
          </a-form-item>
          <div class="muted editor-help">可以一次选择多张图片，每张图片会作为一张独立备注卡片。</div>
        </div>
        <div v-else class="editor-section linked-note-section">
          <div class="editor-section-title">笔记备注</div>
          <a-radio-group v-model:value="editor.noteMode" :disabled="Boolean(editor.entryId)">
            <a-radio value="EXISTING">关联已有笔记</a-radio>
            <a-radio value="NEW">新建笔记</a-radio>
          </a-radio-group>
          <a-select v-if="editor.noteMode === 'EXISTING'" v-model:value="editor.noteId" class="full-width editor-control" :options="noteOptions" placeholder="选择笔记模块中的已有笔记" />
          <template v-else>
            <a-form-item label="笔记标题" class="editor-control"><a-input v-model:value="editor.newNoteTitle" placeholder="例如：黄金走势观察" /></a-form-item>
            <a-form-item label="笔记摘要" class="editor-control"><a-input v-model:value="editor.newNoteSummary" placeholder="可选" /></a-form-item>
            <a-form-item label="笔记正文" class="editor-control"><a-textarea v-model:value="editor.newNoteContent" :rows="4" placeholder="可选，支持 Markdown" /></a-form-item>
          </template>
        </div>
      </a-form>
      <template #footer>
        <a-button v-if="editor.entryId && editor.entryType === 'TEXT'" danger @click="removeCurrentEntry">删除备注</a-button>
        <a-button @click="closeEditor">关闭</a-button>
        <a-button type="primary" :loading="saving" @click="saveEntry">保存</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import dayjs from "dayjs";
import { message, Modal } from "ant-design-vue";
import { useRouter } from "vue-router";
import http from "../../api/http";
import { isTimelineInteractiveTarget } from "../../utils/timelineInteraction";

const INITIAL_SIDE_DAYS = 10;
const EDGE_LOAD_DAYS = 10;
const MAX_CONTENT_ROWS = 6;
const MIN_ZOOM = 0.7;
const MAX_ZOOM = 1.6;
const ZOOM_STEP = 0.15;
const GAP = 14;
const LINE_Y = 460;

const router = useRouter();
const shell = ref<HTMLElement>();
const viewportWidth = ref(1200);
const loadedFrom = ref(dayjs().subtract(INITIAL_SIDE_DAYS, "day"));
const loadedTo = ref(dayjs().add(INITIAL_SIDE_DAYS, "day"));
const records = ref<any[]>([]);
const editorOpen = ref(false);
const saving = ref(false);
const loadingPrevious = ref(false);
const loadingNext = ref(false);
const previewVisible = ref(false);
const previewUrl = ref("");
const zoom = ref(1);
const dragging = ref(false);
const current = reactive<any>({ date: "", data: null });
const editor = reactive<any>({
  entryId: null,
  legacyText: false,
  entryType: "TEXT",
  content: "",
  noteMode: "EXISTING",
  noteId: undefined,
  newNoteTitle: "",
  newNoteSummary: "",
  newNoteContent: "",
});
const uploadList = ref<any[]>([]);
const noteOptions = ref<any[]>([]);
const todayValue = dayjs().format("YYYY-MM-DD");
const entryTypeOptions = [
  { value: "TEXT", label: "文字备注" },
  { value: "IMAGE", label: "图片备注" },
  { value: "NOTE", label: "笔记备注" },
];
let resizeObserver: ResizeObserver | undefined;
let dragStartX = 0;
let dragStartScrollLeft = 0;
let dragMoved = false;
let positioning = false;

const baseCellWidth = computed(() =>
  Math.max(280, (viewportWidth.value - 40 - GAP * 6) / 7),
);
const cellWidth = computed(() => baseCellWidth.value * zoom.value);
const dayStyle = computed(() => ({
  width: `${cellWidth.value}px`,
  flexBasis: `${cellWidth.value}px`,
}));
const stepWidth = computed(() => cellWidth.value + GAP);
const gridWidth = computed(() => Math.max(1200, days.value.length * stepWidth.value));
const gridHeight = computed(() => LINE_Y + 3 * 152 + 120);
const days = computed(() => {
  const recordMap = new Map(
    records.value.map((item) => [item.timeline.timelineDate, item]),
  );
  const count = loadedTo.value.diff(loadedFrom.value, "day") + 1;
  return Array.from({ length: count }, (_, index) => {
    const date = loadedFrom.value.add(index, "day").format("YYYY-MM-DD");
    return { date, data: recordMap.get(date) };
  });
});

type LayoutCard = { key: string; entry: any; lane: number; left: number; width: number; height: number };

const layoutEntries = computed<LayoutCard[]>(() => {
  const laneEnds = Array.from({ length: MAX_CONTENT_ROWS }, () => -Infinity);
  const source = days.value.flatMap((day, index) =>
    (day.data?.entries || []).map((entry: any) => ({ entry, index })),
  );
  source.sort((a, b) => a.index - b.index || String(a.entry.created_at || "").localeCompare(String(b.entry.created_at || "")));
  const result: LayoutCard[] = [];
  source.forEach(({ entry, index }) => {
    const width = cardWidth(entry);
    const center = index * stepWidth.value + cellWidth.value / 2;
    const left = Math.max(0, center - width / 2);
    const right = left + width;
    const preferred = [0, 3, 1, 4, 2, 5];
    const lane = preferred.find((candidate) => left > laneEnds[candidate] + 10) ?? 5;
    laneEnds[lane] = right;
    result.push({ key: `${entry.entryType}-${entry.id}`, entry, lane, left, width, height: cardHeight(entry) });
  });
  return result;
});

function cardWidth(entry: any) {
  const length = String(entry.content || entry.note?.title || "").length;
  const desired = entry.entryType === "IMAGE" ? cellWidth.value * 1.35 : 280 + length * 4;
  return Math.min(cellWidth.value * 2.4, Math.max(cellWidth.value * 0.86, desired));
}

function cardHeight(entry: any) {
  if (entry.entryType === "IMAGE") return 126;
  const length = String(entry.content || entry.note?.summary || entry.note?.title || "").length;
  return Math.min(132, Math.max(82, 70 + Math.ceil(length / 42) * 18));
}

function entryStyle(card: LayoutCard) {
  const top = card.lane < 3
    ? LINE_Y - (card.lane + 1) * 152 + 10
    : LINE_Y + (card.lane - 3) * 152 + 24;
  return { left: `${card.left}px`, width: `${card.width}px`, height: `${card.height}px`, top: `${top}px` };
}

async function fetchRange(from: dayjs.Dayjs, to: dayjs.Dayjs) {
  const incoming: any[] = await http.get("/timelines", {
    params: { from: from.format("YYYY-MM-DD"), to: to.format("YYYY-MM-DD") },
  });
  const merged = new Map(
    records.value.map((item) => [item.timeline.timelineDate, item]),
  );
  incoming.forEach((item) => merged.set(item.timeline.timelineDate, item));
  records.value = Array.from(merged.values());
}

async function initialLoad() {
  await fetchRange(loadedFrom.value, loadedTo.value);
  await nextTick();
  centerToday(false);
}

async function loadPrevious() {
  if (loadingPrevious.value) return;
  loadingPrevious.value = true;
  const oldScrollLeft = shell.value?.scrollLeft || 0;
  const previousTo = loadedFrom.value.subtract(1, "day");
  const previousFrom = loadedFrom.value.subtract(EDGE_LOAD_DAYS, "day");
  try {
    await fetchRange(previousFrom, previousTo);
    loadedFrom.value = previousFrom;
    positioning = true;
    await nextTick();
    if (shell.value) shell.value.scrollLeft = oldScrollLeft + EDGE_LOAD_DAYS * stepWidth.value;
    requestAnimationFrame(() => (positioning = false));
  } finally {
    loadingPrevious.value = false;
  }
}

async function loadNext() {
  if (loadingNext.value) return;
  loadingNext.value = true;
  const nextFrom = loadedTo.value.add(1, "day");
  const nextTo = loadedTo.value.add(EDGE_LOAD_DAYS, "day");
  try {
    await fetchRange(nextFrom, nextTo);
    loadedTo.value = nextTo;
  } finally {
    loadingNext.value = false;
  }
}

function handleScroll() {
  if (!shell.value || positioning || dragging.value) return;
  if (shell.value.scrollLeft <= 1) loadPrevious();
  const distanceToRight =
    shell.value.scrollWidth - shell.value.clientWidth - shell.value.scrollLeft;
  if (distanceToRight <= 1) loadNext();
}

function startDrag(event: PointerEvent) {
  if (!shell.value || event.button !== 0) return;
  // 按钮和表单控件需要保留原生点击行为，否则 setPointerCapture 会把 click 改投给外层容器。
  if (isTimelineInteractiveTarget(event.target)) return;
  dragging.value = true;
  dragMoved = false;
  dragStartX = event.clientX;
  dragStartScrollLeft = shell.value.scrollLeft;
  shell.value.setPointerCapture(event.pointerId);
}

function dragTimeline(event: PointerEvent) {
  if (!dragging.value || !shell.value) return;
  const distance = event.clientX - dragStartX;
  if (Math.abs(distance) > 4) dragMoved = true;
  shell.value.scrollLeft = dragStartScrollLeft - distance;
}

function endDrag(event: PointerEvent) {
  if (!dragging.value || !shell.value) return;
  dragging.value = false;
  if (shell.value.hasPointerCapture(event.pointerId)) {
    shell.value.releasePointerCapture(event.pointerId);
  }
  handleScroll();
}

function scrollHorizontally(event: WheelEvent) {
  if (!shell.value || Math.abs(event.deltaX) > Math.abs(event.deltaY)) return;
  event.preventDefault();
  shell.value.scrollLeft += event.deltaY;
}

async function adjustZoom(delta: number) {
  if (!shell.value) return;
  const oldStep = stepWidth.value;
  const centerIndex = (shell.value.scrollLeft + shell.value.clientWidth / 2) / oldStep;
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number((zoom.value + delta).toFixed(2))));
  positioning = true;
  await nextTick();
  shell.value.scrollLeft = centerIndex * stepWidth.value - shell.value.clientWidth / 2;
  requestAnimationFrame(() => (positioning = false));
}

function centerToday(smooth = true) {
  if (!shell.value) return;
  const index = dayjs(todayValue).diff(loadedFrom.value, "day");
  if (index < 0 || index >= days.value.length) return;
  positioning = true;
  shell.value.scrollTo({
    left: index * stepWidth.value - (shell.value.clientWidth - cellWidth.value) / 2,
    behavior: smooth ? "smooth" : "auto",
  });
  window.setTimeout(() => (positioning = false), smooth ? 500 : 0);
}

function weekday(date: string) {
  return `周${["日", "一", "二", "三", "四", "五", "六"][dayjs(date).day()]}`;
}

function openEditor(date: string) {
  if (dragMoved) {
    dragMoved = false;
    return;
  }
  current.date = date;
  current.data = records.value.find((item) => item.timeline.timelineDate === date) || null;
  Object.assign(editor, {
    entryId: null,
    legacyText: false,
    entryType: "TEXT",
    content: "",
    noteMode: "EXISTING",
    noteId: undefined,
    newNoteTitle: "",
    newNoteSummary: "",
    newNoteContent: "",
  });
  uploadList.value = [];
  editorOpen.value = true;
}

function handleEntryClick(entry: any) {
  if (dragMoved) {
    dragMoved = false;
    return;
  }
  if (entry.entryType === "IMAGE") {
    previewImage(entry.file);
    return;
  }
  if (entry.entryType === "NOTE") {
    openNote(entry.noteId || entry.note?.id);
    return;
  }
  const source = days.value.find((day) =>
    day.data?.entries?.some((item: any) => String(item.id) === String(entry.id)),
  );
  current.date = source?.date || current.date;
  current.data = source?.data || null;
  Object.assign(editor, {
    entryId: entry.legacy ? null : entry.id,
    legacyText: Boolean(entry.legacy),
    entryType: "TEXT",
    content: entry.content || "",
  });
  editorOpen.value = true;
}

function beforeUpload(file: any) {
  uploadList.value.push(file);
  return false;
}

async function saveEntry() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (editor.entryType === "TEXT") {
      if (!editor.content?.trim()) {
        message.warning("文字备注不能为空");
        return;
      }
      if (editor.entryId) {
        await http.put(`/timelines/${current.date}/entries/${editor.entryId}`, { content: editor.content });
      } else if (editor.legacyText) {
        await http.put(`/timelines/${current.date}`, {
          dailyContent: editor.content,
          noteIds: (current.data?.notes || []).map((note: any) => note.id),
          version: current.data?.timeline?.version,
        });
      } else {
        await http.post(`/timelines/${current.date}/entries`, { entryType: "TEXT", content: editor.content });
      }
    } else if (editor.entryType === "IMAGE") {
      if (!uploadList.value.length) {
        message.warning("请选择至少一张图片");
        return;
      }
      for (const file of uploadList.value) {
        const data = new FormData();
        data.append("file", file.originFileObj || file);
        await http.post(`/timelines/${current.date}/entries/image`, data, {
          headers: { "Content-Type": "multipart/form-data" },
        });
      }
    } else {
      let noteId = editor.noteId;
      if (editor.noteMode === "NEW") {
        if (!editor.newNoteTitle?.trim()) {
          message.warning("请输入新笔记标题");
          return;
        }
        const created: any = await http.post("/notes", {
          title: editor.newNoteTitle.trim(),
          noteType: "NORMAL",
          businessDate: current.date,
          summary: editor.newNoteSummary,
          manualContent: editor.newNoteContent,
          pinned: 0,
        });
        noteId = created.id;
      }
      if (!noteId) {
        message.warning("请选择已有笔记或填写新笔记");
        return;
      }
      await http.post(`/timelines/${current.date}/entries`, { entryType: "NOTE", noteId });
    }
    message.success("时间线备注已保存");
    closeEditor();
    await fetchRange(dayjs(current.date), dayjs(current.date));
  } finally {
    saving.value = false;
  }
}

function removeCurrentEntry() {
  Modal.confirm({
    title: "删除这条文字备注？",
    content: "删除后不可恢复，但不会影响笔记模块中的原始数据。",
    onOk: async () => {
      await http.delete(`/timelines/${current.date}/entries/${editor.entryId}`);
      message.success("备注已删除");
      closeEditor();
      await fetchRange(dayjs(current.date), dayjs(current.date));
    },
  });
}

function closeEditor() {
  editorOpen.value = false;
  uploadList.value = [];
}

function previewImage(file: any) {
  if (dragMoved || !file) return;
  previewUrl.value = file.originalUrl || file.thumbnailUrl;
  previewVisible.value = true;
}

function changePreview(visible: boolean) {
  previewVisible.value = visible;
}

function openNote(noteId: string) {
  if (dragMoved) return;
  router.push({ path: `/notes/${noteId}`, query: { mode: "view" } });
}

onMounted(async () => {
  if (shell.value) {
    viewportWidth.value = shell.value.clientWidth;
    resizeObserver = new ResizeObserver(([entry]) => {
      viewportWidth.value = entry.contentRect.width;
    });
    resizeObserver.observe(shell.value);
  }
  await Promise.all([
    initialLoad(),
    http.get("/notes", { params: { page: 1, size: 100 } }).then((data: any) => {
      noteOptions.value = data.records
        .filter((note: any) => note.noteType !== "GOLD_DAILY_SUMMARY")
        .map((note: any) => ({ value: String(note.id), label: note.title }));
    }),
  ]);
});

onBeforeUnmount(() => resizeObserver?.disconnect());
</script>

<style scoped>
.zoom-label {
  min-width: 48px;
  color: #595959;
  text-align: center;
}

.timeline-frame {
  position: relative;
}
.timeline-actions { justify-content: flex-start; }

.timeline-shell {
  background: #fff;
  border-radius: 10px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 18px 20px 26px;
  overscroll-behavior-x: contain;
  cursor: grab;
  scrollbar-width: thin;
  touch-action: pan-y;
}

.timeline-shell.dragging {
  cursor: grabbing;
  user-select: none;
}

.edge-button {
  position: absolute;
  z-index: 5;
  top: 86px;
  box-shadow: 0 2px 10px #00000024;
}

.edge-left {
  left: 6px;
}

.edge-right {
  right: 6px;
}

.timeline-grid {
  position: relative;
  display: block;
  --date-line-top: 258px;
  min-width: max-content;
}

.timeline-grid::before {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 460px;
  height: 2px;
  background: linear-gradient(90deg, #d4a72c, #f0d98f);
}

.timeline-grid .day-column {
  position: absolute;
  top: 0;
  height: 100%;
  flex: none;
}

.day-column {
  position: relative;
  flex-grow: 0;
  flex-shrink: 0;
}

.date-button {
  position: absolute;
  z-index: 3;
  top: 408px;
  left: 0;
  width: 100%;
  height: 42px;
  display: flex;
  justify-content: center;
  align-items: baseline;
  gap: 8px;
  color: #6b5517;
  background: transparent;
  border: 0;
  cursor: pointer;
  background: #fff;
}

.date-button strong {
  font-size: 18px;
}

.date-button span {
  color: #8c8c8c;
}

.timeline-dot {
  position: absolute;
  z-index: 2;
  top: 454px;
  left: calc(50% - 6px);
  width: 12px;
  height: 12px;
  margin: 0;
  border: 3px solid #fff;
  border-radius: 50%;
  background: #d4a72c;
  box-shadow: 0 0 0 1px #d4a72c;
}

.date-add-button {
  position: absolute;
  z-index: 5;
  top: 449px;
  left: calc(50% + 12px);
  width: 24px;
  height: 24px;
  border: 1px solid #d4a72c;
  border-radius: 50%;
  background: #fffdf6;
  color: #9c7615;
  cursor: pointer;
}

.day-add-hint {
  position: absolute;
  top: 496px;
  left: 4%;
  width: 92%;
  height: 90px;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  background: #fff;
  color: #8a6b18;
  cursor: pointer;
}

.day-add-hint span {
  display: block;
  margin-top: 8px;
  color: #9aa3ad;
  font-size: 12px;
}

.timeline-entry-card {
  position: absolute;
  z-index: 3;
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 5px;
  padding: 12px 14px;
  overflow: hidden;
  border: 1px solid #e0e5ec;
  border-radius: 10px;
  background: #f8fbff;
  box-shadow: 0 2px 8px #1f293710;
  color: #243447;
  text-align: left;
  cursor: pointer;
}

.timeline-entry-card:hover {
  z-index: 6;
  border-color: #d4a72c;
  box-shadow: 0 6px 18px #1f293526;
}

.entry-text { background: #f8fbff; }
.entry-image { padding: 6px; background: #fffdf6; }
.entry-note { background: #fff8df; }

.timeline-entry-card img {
  width: 100%;
  height: 100%;
  border-radius: 6px;
  object-fit: cover;
}

.entry-type {
  color: #8a6b18;
  font-size: 12px;
}

.entry-content {
  display: -webkit-box;
  overflow: hidden;
  color: #536172;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.today .date-button strong {
  color: #1677ff;
}

.today .timeline-dot {
  background: #1677ff;
  box-shadow: 0 0 0 1px #1677ff;
}

.day-content {
  min-height: 480px;
  padding: 12px;
  border: 1px solid #ececec;
  border-radius: 9px;
  background: #fafafa;
  cursor: pointer;
  transition: 0.15s;
}

.day-content:hover {
  border-color: #d4a72c;
  box-shadow: 0 5px 16px #00000012;
}

.daily-summary {
  display: -webkit-box;
  margin: 0 0 8px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: #595959;
}

/* 日期线居中，资料分布在日期线上下两侧；每行由算法分配到当前较矮的行。 */
.day-assets {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-assets-top {
  justify-content: flex-end;
  min-height: 200px;
  padding-bottom: 30px;
}

.day-assets-bottom {
  min-height: 200px;
  padding-top: 30px;
}

.asset-row {
  display: flex;
  gap: 8px;
  min-height: 72px;
}

.asset {
  min-width: 0;
  flex: 1;
  min-height: 72px;
  padding: 0;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
  cursor: pointer;
}

.image-asset img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.note-asset {
  padding: 6px;
  text-align: left;
  background: #fff8df;
  white-space: normal;
}

.note-asset strong,
.note-asset span {
  display: block;
  overflow: hidden;
  white-space: normal;
  text-overflow: ellipsis;
}

.note-asset span {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.more-line {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 12px;
  text-align: center;
}

.empty {
  width: 100%;
  height: 165px;
  color: #999;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9px;
  color: #8a6b18;
}
.empty span { color: #9aa3ad; font-size: 12px; }
.editor-section { border: 1px solid #e7ebf0; border-radius: 8px; padding: 14px 16px 4px; margin-bottom: 14px; }
.editor-section-title { font-weight: 600; margin-bottom: 8px; }
.text-note-section { border-left: 3px solid #3b82f6; }
.image-note-section { border-left: 3px solid #d4a72c; }
.linked-note-section { border-left: 3px solid #8b5cf6; }
.editor-control { margin-top: 14px; }
.editor-help { margin-top: 10px; font-size: 12px; }
.day-content:has(.day-assets) { background: #fffdf6; }
.day-content:has(.daily-summary) { background: #f8fbff; }
.day-content:has(.day-assets):hover { border-color: #d4a72c; }

.existing {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

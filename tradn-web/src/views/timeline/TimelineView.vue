<template>
  <div class="page timeline-page">
    <div class="page-header">
      <div>
        <div class="page-title">黄金时间线</div>
        <div class="muted">
          默认显示今天前后各 3 天；按住鼠标拖动浏览，到达边缘时继续加载
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
        <div class="timeline-grid">
          <article
            v-for="item in days"
            :key="item.date"
            class="day-column"
            :class="{ today: item.date === todayValue }"
            :style="dayStyle"
          >
            <button class="date-button" type="button" @click="edit(item)">
              <strong>{{ item.date.slice(5) }}</strong>
              <span>{{ weekday(item.date) }}</span>
            </button>
            <div class="timeline-dot" />

            <div class="day-content" @click="edit(item)">
              <template v-if="item.data">
                <p v-if="item.data.timeline.dailyContent" class="daily-summary">
                  {{ item.data.timeline.dailyContent }}
                </p>
                <div class="asset-grid">
                  <button
                    v-for="file in visibleFiles(item.data.files)"
                    :key="`file-${file.id}`"
                    class="asset image-asset"
                    type="button"
                    title="查看当天图片"
                    @click.stop="previewImage(file)"
                  >
                    <img :src="file.thumbnailUrl" loading="lazy" alt="黄金走势图缩略图" />
                  </button>
                  <button
                    v-for="note in visibleNotes(item.data.notes, item.data.files)"
                    :key="`note-${note.id}`"
                    class="asset note-asset"
                    type="button"
                    :title="note.summary || note.title"
                    @click.stop="openNote(note.id)"
                  >
                    <strong>{{ note.title }}</strong>
                    <span>{{ note.summary || "暂无摘要" }}</span>
                  </button>
                </div>
                <div v-if="hiddenCount(item.data) > 0" class="more-line">
                  另有 {{ hiddenCount(item.data) }} 项，点击日期查看
                </div>
              </template>
              <button v-else class="empty" type="button" @click.stop="edit(item)">
                <strong>+ 添加当天记录</strong>
                <span>文字备注 · 图片备注 · 笔记备注</span>
              </button>
            </div>
          </article>
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
      :title="`${current.date} 黄金时间线`"
      width="760px"
      :confirm-loading="saving"
      @ok="save"
    >
      <a-form layout="vertical">
        <div class="editor-section text-note-section">
          <div class="editor-section-title">文字备注</div>
          <a-form-item label="当天市场观察、价格走势和复盘摘要">
            <a-textarea v-model:value="editor.dailyContent" :rows="5" placeholder="记录当天看到的行情、消息和自己的判断……" />
          </a-form-item>
        </div>
        <div class="editor-section image-note-section">
          <div class="editor-section-title">图片备注</div>
          <a-form-item label="上传当天走势图或其他资料图片">
            <a-upload
              :before-upload="beforeUpload"
              :file-list="uploadList"
              list-type="picture"
              multiple
            >
              <a-button>选择图片</a-button>
            </a-upload>
          </a-form-item>
        </div>
        <div class="editor-section linked-note-section">
          <div class="editor-section-title">笔记备注</div>
          <a-form-item label="关联已有笔记">
            <a-select
              v-model:value="editor.noteIds"
              mode="multiple"
              :options="noteOptions"
              placeholder="选择笔记模块中的已有笔记"
            />
          </a-form-item>
          <a-divider orientation="left">或在保存时新建一篇笔记并关联</a-divider>
          <a-form-item label="新建笔记标题">
            <a-input v-model:value="editor.newNoteTitle" placeholder="例如：黄金每日复盘 - 2026-09-20" />
          </a-form-item>
          <a-form-item label="新建笔记摘要">
            <a-input v-model:value="editor.newNoteSummary" placeholder="可选，作为笔记列表摘要" />
          </a-form-item>
          <a-form-item v-if="editor.newNoteTitle" label="新建笔记正文">
            <a-textarea v-model:value="editor.newNoteContent" :rows="4" placeholder="可选，支持 Markdown" />
          </a-form-item>
        </div>
      </a-form>
      <div v-if="current.data?.files?.length" class="existing">
        <a-image
          v-for="file in current.data.files"
          :key="file.id"
          :src="file.thumbnailUrl"
          :preview="{ src: file.originalUrl }"
          width="88px"
        />
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import dayjs from "dayjs";
import { useRouter } from "vue-router";
import http from "../../api/http";

const INITIAL_SIDE_DAYS = 10;
const EDGE_LOAD_DAYS = 10;
const MAX_CONTENT_ROWS = 5;
const ASSETS_PER_ROW = 2;
const MAX_VISIBLE_ASSETS = MAX_CONTENT_ROWS * ASSETS_PER_ROW;
const MIN_ZOOM = 0.7;
const MAX_ZOOM = 1.6;
const ZOOM_STEP = 0.15;
const GAP = 14;

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
const editor = reactive<any>({ dailyContent: "", noteIds: [], version: null, newNoteTitle: "", newNoteSummary: "", newNoteContent: "" });
const uploadList = ref<any[]>([]);
const noteOptions = ref<any[]>([]);
const todayValue = dayjs().format("YYYY-MM-DD");
let resizeObserver: ResizeObserver | undefined;
let dragStartX = 0;
let dragStartScrollLeft = 0;
let dragMoved = false;
let positioning = false;

const baseCellWidth = computed(() =>
  Math.max(150, (viewportWidth.value - 40 - GAP * 6) / 7),
);
const cellWidth = computed(() => baseCellWidth.value * zoom.value);
const dayStyle = computed(() => ({
  width: `${cellWidth.value}px`,
  flexBasis: `${cellWidth.value}px`,
}));
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

function stepWidth() {
  return cellWidth.value + GAP;
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
    if (shell.value) shell.value.scrollLeft = oldScrollLeft + EDGE_LOAD_DAYS * stepWidth();
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
  const oldStep = stepWidth();
  const centerIndex = (shell.value.scrollLeft + shell.value.clientWidth / 2) / oldStep;
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number((zoom.value + delta).toFixed(2))));
  positioning = true;
  await nextTick();
  shell.value.scrollLeft = centerIndex * stepWidth() - shell.value.clientWidth / 2;
  requestAnimationFrame(() => (positioning = false));
}

function centerToday(smooth = true) {
  if (!shell.value) return;
  const index = dayjs(todayValue).diff(loadedFrom.value, "day");
  if (index < 0 || index >= days.value.length) return;
  positioning = true;
  shell.value.scrollTo({
    left: index * stepWidth() - (shell.value.clientWidth - cellWidth.value) / 2,
    behavior: smooth ? "smooth" : "auto",
  });
  window.setTimeout(() => (positioning = false), smooth ? 500 : 0);
}

function weekday(date: string) {
  return `周${["日", "一", "二", "三", "四", "五", "六"][dayjs(date).day()]}`;
}

function visibleFiles(files: any[]) {
  return files.slice(0, MAX_VISIBLE_ASSETS);
}

function visibleNotes(notes: any[], files: any[]) {
  return notes.slice(0, Math.max(0, MAX_VISIBLE_ASSETS - files.length));
}

function hiddenCount(data: any) {
  return Math.max(0, data.files.length + data.notes.length - MAX_VISIBLE_ASSETS);
}

function edit(item: any) {
  if (dragMoved) {
    dragMoved = false;
    return;
  }
  current.date = item.date;
  current.data = item.data || null;
  editor.dailyContent = item.data?.timeline.dailyContent || "";
  editor.noteIds = item.data?.notes.map((note: any) => String(note.id)) || [];
  editor.version = item.data?.timeline.version ?? null;
  editor.newNoteTitle = "";
  editor.newNoteSummary = "";
  editor.newNoteContent = "";
  uploadList.value = [];
  editorOpen.value = true;
}

function beforeUpload(file: any) {
  uploadList.value.push(file);
  return false;
}

async function save() {
  saving.value = true;
  try {
    const noteIds = [...editor.noteIds];
    // 新建笔记只在用户点击弹窗“保存”时提交，取消弹窗不会产生孤立笔记。
    if (editor.newNoteTitle.trim()) {
      const created: any = await http.post("/notes", {
        title: editor.newNoteTitle.trim(),
        noteType: "NORMAL",
        businessDate: current.date,
        summary: editor.newNoteSummary,
        manualContent: editor.newNoteContent,
        pinned: 0,
      });
      noteIds.push(String(created.id));
    }
    const saved: any = await http.put(`/timelines/${current.date}`, {
      dailyContent: editor.dailyContent,
      noteIds,
      version: editor.version,
    });
    for (const file of uploadList.value) {
      const data = new FormData();
      data.append("file", file.originFileObj || file);
      await http.post("/files", data, {
        params: {
          businessType: "TIMELINE",
          businessId: saved.id,
          usageType: "CHART",
        },
        headers: { "Content-Type": "multipart/form-data" },
      });
    }
    if (uploadList.value.length > 0) {
      // 文件关系建立后再次同步时间线，使每日汇总笔记立即包含新上传的图片。
      await http.put(`/timelines/${current.date}`, {
        dailyContent: editor.dailyContent,
        noteIds,
        version: saved.version,
      });
    }
    editorOpen.value = false;
    const date = dayjs(current.date);
    await fetchRange(date, date);
  } finally {
    saving.value = false;
  }
}

function previewImage(file: any) {
  if (dragMoved) return;
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
  --date-line-top: 58px;
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: max-content;
}

.timeline-grid::before {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: var(--date-line-top);
  height: 2px;
  background: linear-gradient(90deg, #d4a72c, #f0d98f);
}

.day-column {
  position: relative;
  flex-grow: 0;
  flex-shrink: 0;
}

.date-button {
  width: 100%;
  height: 46px;
  display: flex;
  justify-content: center;
  align-items: baseline;
  gap: 8px;
  color: #6b5517;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.date-button strong {
  font-size: 18px;
}

.date-button span {
  color: #8c8c8c;
}

.timeline-dot {
  position: relative;
  z-index: 2;
  width: 12px;
  height: 12px;
  margin: 6px auto 12px;
  border: 3px solid #fff;
  border-radius: 50%;
  background: #d4a72c;
  box-shadow: 0 0 0 1px #d4a72c;
}

.today .date-button strong {
  color: #1677ff;
}

.today .timeline-dot {
  background: #1677ff;
  box-shadow: 0 0 0 1px #1677ff;
}

.day-content {
  min-height: 190px;
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

/* 单日图片和笔记在宽度不足时自动换行，最多展示五行。 */
.asset-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(5, 62px);
  grid-auto-flow: row;
  gap: 6px;
  max-height: 334px;
  overflow: hidden;
}

.asset {
  min-width: 0;
  height: 62px;
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
}

.note-asset strong,
.note-asset span {
  display: block;
  overflow: hidden;
  white-space: nowrap;
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
.day-content:has(.asset-grid) { background: #fffdf6; }
.day-content:has(.daily-summary) { background: #f8fbff; }
.day-content:has(.asset-grid):hover { border-color: #d4a72c; }

.existing {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

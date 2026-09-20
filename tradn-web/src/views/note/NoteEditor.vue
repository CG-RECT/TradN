<template>
  <div v-if="note" class="page note-page">
    <div class="page-header">
      <div>
        <a @click="back">← 返回笔记</a>
        <div class="page-title">
          {{ isReadOnly ? "查看" : isNew ? "新建" : "编辑" }}笔记 ·
          {{ form.title || "未命名笔记" }}
        </div>
      </div>
      <a-button @click="back">关闭</a-button>
    </div>

    <div class="page-actions">
      <a-button v-if="!isNew && !isReadOnly && note.syncStatus === 'AUTO'" danger @click="detach">解除自动同步</a-button>
      <a-button v-if="!isNew" @click="download">导出 Markdown</a-button>
      <a-button v-if="isReadOnly" type="primary" @click="switchToEdit">进入编辑</a-button>
      <template v-else><a-button @click="back">取消</a-button><a-button type="primary" @click="save">保存</a-button></template>
    </div>

    <div class="content-card">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="标题" required>
            <a-input v-model:value="form.title" :disabled="isReadOnly" />
          </a-form-item>
        </a-col>
        <a-col :span="6">
          <a-form-item label="类型">
            <a-select
              v-model:value="form.noteType"
              :disabled="isReadOnly || note.syncStatus === 'AUTO'"
              :options="types"
            />
          </a-form-item>
        </a-col>
        <a-col :span="6">
          <a-form-item label="业务日期">
            <a-date-picker
              v-model:value="form.businessDate"
              class="full-width"
              :disabled="isReadOnly"
            />
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="摘要">
            <a-input v-model:value="form.summary" :disabled="isReadOnly" />
          </a-form-item>
        </a-col>
      </a-row>

      <template v-if="note.generatedContent">
        <h3>系统自动汇总区（只读）</h3>
        <div class="generated">
          <MdPreview :model-value="note.generatedContent" />
        </div>
        <h3>人工补充区</h3>
      </template>
      <MdPreview v-if="isReadOnly" :model-value="form.manualContent || ''" />
      <MdEditor
        v-else
        v-model="form.manualContent"
        language="zh-CN"
        @on-upload-img="uploadImages"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { message, Modal } from "ant-design-vue";
import { MdEditor, MdPreview } from "md-editor-v3";
import dayjs from "dayjs";
import { useRoute, useRouter } from "vue-router";
import http from "../../api/http";
import { loadDictionary, toSelectOptions } from "../../api/dictionary";

const route = useRoute();
const router = useRouter();
const isNew = computed(() => route.params.id === "new");
const isReadOnly = computed(() => route.query.mode === "view");
const note = ref<any>();
const form = reactive<any>({});
const types = ref<any[]>([]);

async function load() {
  if (isNew.value) {
    note.value = {
      title: "",
      noteType: "NORMAL",
      generatedContent: "",
      syncStatus: "NONE",
      version: 0,
    };
  } else {
    note.value = await http.get(`/notes/${route.params.id}`);
  }
  Object.assign(form, note.value, {
    businessDate: note.value.businessDate ? dayjs(note.value.businessDate) : null,
    manualContent: note.value.manualContent || "",
  });
}

// 自动生成区由时间线维护，页面仅提交人工编辑区和允许修改的元数据。
async function save() {
  const data = {
    ...form,
    businessDate: form.businessDate?.format("YYYY-MM-DD"),
    version: note.value.version,
  };
  if (isNew.value) {
    const created: any = await http.post("/notes", data);
    message.success("笔记已创建");
    await router.replace({ path: `/notes/${created.id}`, query: { mode: "edit" } });
    await load();
    return;
  }
  await http.put(`/notes/${note.value.id}`, data);
  message.success("笔记已保存");
  await load();
}

function detach() {
  Modal.confirm({
    title: "解除自动同步？",
    content: "解除后，时间线变化不再更新此笔记，当前自动内容会合并到人工内容。",
    onOk: async () => {
      await http.post(`/notes/${note.value.id}/detach-sync`);
      await load();
    },
  });
}

// 通过统一请求实例下载 Blob，确保导出请求仍携带 Bearer 令牌。
async function download() {
  const blob = (await http.get(`/notes/${note.value.id}/export-markdown`, {
    responseType: "blob",
  })) as Blob;
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${note.value.title || "笔记"}.md`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function uploadImages(files: File[], callback: (urls: string[]) => void) {
  if (isNew.value) {
    message.warning("请先保存笔记，再上传正文图片");
    callback([]);
    return;
  }
  const urls: string[] = [];
  for (const file of files) {
    const data = new FormData();
    data.append("file", file);
    const object: any = await http.post("/files", data, {
      params: {
        businessType: "NOTE",
        businessId: note.value.id,
        usageType: "EMBEDDED_IMAGE",
      },
      headers: { "Content-Type": "multipart/form-data" },
    });
    const access: any = await http.get(`/files/${object.id}/access-url`);
    urls.push(access.url);
  }
  callback(urls);
}

function back() {
  router.push("/notes");
}

function switchToEdit() {
  router.replace({ path: route.path, query: { mode: "edit" } });
}

onMounted(async () => {
  types.value = toSelectOptions(await loadDictionary("NOTE_TYPE"));
  await load();
});
</script>

<style scoped>
.generated {
  border: 1px solid #eee;
  border-radius: 8px;
  margin-bottom: 18px;
  max-height: 480px;
  overflow: auto;
}
</style>

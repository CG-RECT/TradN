<template>
  <div class="page note-page">
    <div class="page-header">
      <div>
        <div class="page-title">笔记</div>
        <div class="muted">交易心得、每日汇总和复盘资料</div>
      </div>
    </div>
    <div class="page-actions"><a-button type="primary" @click="create">新建笔记</a-button></div>
    <div class="content-card">
      <div class="toolbar">
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索标题或摘要"
          style="width: 300px"
          @search="search"
        />
        <a-select
          v-model:value="type"
          allow-clear
          placeholder="全部类型"
          style="width: 180px"
          :options="typeSelectOptions"
          @change="search"
        />
      </div>
      <a-list
        :data-source="rows"
        :loading="loading"
        item-layout="horizontal"
        style="margin-top: 12px"
      >
        <template #renderItem="{ item }">
          <a-list-item>
            <template #actions>
              <a @click="open(item.id, 'view')">查看</a>
              <a @click="open(item.id, 'edit')">编辑</a>
            </template>
            <a-list-item-meta :description="item.summary || '暂无摘要'">
              <template #title>
                <a @click="open(item.id, 'view')">{{ item.title }}</a>
                <a-tag :color="dictionaryColor(typeOptions, item.noteType)">
                  {{ dictionaryLabel(typeOptions, item.noteType) }}
                </a-tag>
                <a-tag v-if="item.syncStatus === 'AUTO'" color="blue">自动同步</a-tag>
              </template>
            </a-list-item-meta>
            <span class="muted">
              {{ item.businessDate || formatDateTime(item.updatedAt) }}
            </span>
          </a-list-item>
        </template>
      </a-list>
      <a-pagination
        v-model:current="page"
        :total="total"
        :page-size="20"
        @change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import http from "../../api/http";
import {
  dictionaryColor,
  dictionaryLabel,
  loadDictionary,
  toSelectOptions,
} from "../../api/dictionary";
import { formatDateTime } from "../../utils/format";

const router = useRouter();
const rows = ref<any[]>([]);
const loading = ref(false);
const keyword = ref("");
const type = ref<string>();
const page = ref(1);
const total = ref(0);
const typeOptions = ref<any[]>([]);
const typeSelectOptions = computed(() => toSelectOptions(typeOptions.value));

async function load() {
  loading.value = true;
  try {
    const data: any = await http.get("/notes", {
      params: {
        page: page.value,
        size: 20,
        keyword: keyword.value,
        type: type.value,
      },
    });
    rows.value = data.records;
    total.value = Number(data.total);
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  load();
}

function create() {
  // 新建页先在浏览器中编辑，只有点击保存才创建数据库记录。
  router.push({ path: "/notes/new", query: { mode: "edit" } });
}

function open(id: string, mode: "view" | "edit") {
  router.push({ path: `/notes/${id}`, query: { mode } });
}

onMounted(async () => {
  typeOptions.value = await loadDictionary("NOTE_TYPE");
  await load();
});
</script>

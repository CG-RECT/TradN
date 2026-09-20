<template>
  <div class="page note-page">
    <SearchPanel @search="search" @reset="reset">
      <div class="search-field">
        <span class="search-field-label">关键词</span>
        <a-input
          v-model:value="keyword"
          allow-clear
          placeholder="标题或摘要"
          style="width: 220px"
          @pressEnter="search"
        />
      </div>
      <div class="search-field">
        <span class="search-field-label">类型</span>
        <a-select
          v-model:value="type"
          allow-clear
          placeholder="全部类型"
          style="width: 180px"
          :options="typeSelectOptions"
        />
      </div>
      <div class="search-field">
        <span class="search-field-label">标签</span>
        <a-select
          v-model:value="tagId"
          allow-clear
          placeholder="全部标签"
          style="width: 180px"
          :options="tagSelectOptions"
        />
      </div>
    </SearchPanel>

    <div class="content-card">
      <ListSectionHeader title="笔记" subtitle="交易心得、每日汇总和复盘资料">
        <template #actions>
          <a-button type="primary" @click="create">新建笔记</a-button>
        </template>
      </ListSectionHeader>
      <StandardTable
        :data-source="rows"
        :columns="columns"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        @change="change"
      >
        <template #bodyCell="{ column, record, text }">
          <template v-if="column.key === 'title'">
            <a @click="open(record.id, 'view')">{{ text }}</a>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag :color="dictionaryColor(typeOptions, text)">
              {{ dictionaryLabel(typeOptions, text) }}
            </a-tag>
            <a-tag v-if="record.syncStatus === 'AUTO'" color="blue">自动同步</a-tag>
          </template>
          <template v-else-if="column.key === 'tags'">
            <a-space wrap>
              <a-tag v-for="tag in record.tags || []" :key="tag.id" color="purple">
                {{ tag.tagName }}
              </a-tag>
              <span v-if="!record.tags?.length" class="muted">未设置</span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(text) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a @click="open(record.id, 'view')">查看</a>
              <a @click="open(record.id, 'edit')">编辑</a>
            </a-space>
          </template>
        </template>
      </StandardTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import http from "../../api/http";
import {
  dictionaryColor,
  dictionaryLabel,
  loadDictionary,
  toSelectOptions,
} from "../../api/dictionary";
import { formatDateTime } from "../../utils/format";
import SearchPanel from "../../components/list/SearchPanel.vue";
import StandardTable from "../../components/list/StandardTable.vue";
import ListSectionHeader from "../../components/list/ListSectionHeader.vue";

const router = useRouter();
const rows = ref<any[]>([]);
const loading = ref(false);
const keyword = ref("");
const type = ref<string>();
const tagId = ref<string>();
const typeOptions = ref<any[]>([]);
const tags = ref<any[]>([]);
const pagination = reactive({ current: 1, pageSize: 20, total: 0 });
const typeSelectOptions = computed(() => toSelectOptions(typeOptions.value));
const tagSelectOptions = computed(() =>
  tags.value.map((tag) => ({ value: String(tag.id), label: tag.tagName })),
);

const columns = [
  { title: "标题", key: "title", dataIndex: "title" },
  { title: "类型", key: "type", dataIndex: "noteType" },
  { title: "标签", key: "tags", dataIndex: "tags" },
  { title: "业务日期", dataIndex: "businessDate" },
  { title: "更新时间", key: "updatedAt", dataIndex: "updatedAt" },
  { title: "操作", key: "action" },
];

async function load() {
  loading.value = true;
  try {
    const data: any = await http.get("/notes", {
      params: {
        page: pagination.current,
        size: pagination.pageSize,
        keyword: keyword.value || undefined,
        type: type.value,
        tagId: tagId.value,
      },
    });
    rows.value = data.records;
    pagination.total = Number(data.total);
  } finally {
    loading.value = false;
  }
}

function search() {
  pagination.current = 1;
  load();
}

function reset() {
  keyword.value = "";
  type.value = undefined;
  tagId.value = undefined;
  search();
}

function change(pager: any) {
  pagination.current = pager.current;
  pagination.pageSize = pager.pageSize;
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
  tags.value = await http.get("/notes/tags");
  await load();
});
</script>

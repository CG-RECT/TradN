<template>
  <div class="page trade-page">
    <SearchPanel @search="search" @reset="reset">
      <div class="search-field">
        <span class="search-field-label">状态</span>
        <a-select
          v-model:value="status"
          allow-clear
          placeholder="全部状态"
          style="width: 180px"
          :options="statusSelectOptions"
        />
      </div>
    </SearchPanel>
    <div class="content-card">
      <ListSectionHeader title="开仓记录" subtitle="开仓前检查、持仓复盘与实际盈亏">
        <template #actions>
          <a-button type="primary" @click="create">新建开仓问卷</a-button>
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
          <template v-if="column.key === 'status'">
            <a-tag :color="dictionaryColor(statusOptions, text)">
              {{ dictionaryLabel(statusOptions, text) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(text) }}
          </template>
          <template v-else-if="column.key === 'profitLoss'">
            <span :class="Number(record.profitLoss) >= 0 ? 'profit' : 'loss'">
              {{ record.profitLoss ?? "-" }}
            </span>
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
const status = ref<string>();
const statusOptions = ref<any[]>([]);
const pagination = reactive({ current: 1, pageSize: 20, total: 0 });
const statusSelectOptions = computed(() => toSelectOptions(statusOptions.value));

const columns = [
  { title: "编号", dataIndex: "recordNo" },
  { title: "状态", key: "status", dataIndex: "status" },
  { title: "方向", dataIndex: "direction" },
  { title: "开仓价", dataIndex: "openPrice" },
  { title: "平仓价", dataIndex: "closePrice" },
  { title: "盈亏（元）", key: "profitLoss", dataIndex: "profitLoss" },
  { title: "更新时间", key: "updatedAt", dataIndex: "updatedAt" },
  { title: "操作", key: "action" },
];

async function load() {
  loading.value = true;
  try {
    const data: any = await http.get("/trades", {
      params: {
        page: pagination.current,
        size: pagination.pageSize,
        status: status.value,
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
  status.value = undefined;
  search();
}

function change(pager: any) {
  pagination.current = pager.current;
  pagination.pageSize = pager.pageSize;
  load();
}

function create() {
  // 新建页只加载问卷模板，用户保存前不会向数据库写入交易记录。
  router.push({ path: "/trades/new", query: { mode: "edit" } });
}

function open(id: string, mode: "view" | "edit") {
  router.push({ path: `/trades/${id}`, query: { mode } });
}

onMounted(async () => {
  statusOptions.value = await loadDictionary("TRADE_STATUS");
  await load();
});
</script>

<style scoped>
.toolbar {
  justify-content: flex-end;
}

.profit {
  color: #cf1322;
}

.loss {
  color: #389e0d;
}
</style>

<template>
  <a-table
    class="standard-table"
    :data-source="dataSource"
    :columns="columns"
    :loading="loading"
    :row-key="rowKey"
    :pagination="pagination"
    :scroll="scroll"
    @change="$emit('change', $event)"
  >
    <template #bodyCell="slotData">
      <slot name="bodyCell" v-bind="slotData" />
    </template>
  </a-table>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    dataSource: unknown[];
    columns: unknown[];
    loading?: boolean;
    rowKey?: string | ((record: unknown) => string);
    pagination?: object | false;
    scroll?: object;
  }>(),
  { loading: false, rowKey: "id", pagination: false },
);

defineEmits<{ (event: "change", value: unknown): void }>();
</script>

<style scoped>
.standard-table :deep(.ant-table-thead > tr > th) {
  color: #243447;
  font-weight: 600;
  background: #eef3fa;
  border-bottom: 1px solid #d7e0ec;
}

.standard-table :deep(.ant-table-tbody > tr:nth-child(even) > td) {
  background: #f8fafd;
}

.standard-table :deep(.ant-table-tbody > tr:hover > td) {
  background: #eaf3ff;
}
</style>

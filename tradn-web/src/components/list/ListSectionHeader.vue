<template>
  <div class="list-section-header">
    <div>
      <div class="list-section-title">{{ title }}</div>
      <div v-if="subtitle" class="muted">{{ subtitle }}</div>
    </div>
    <div class="list-section-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 列表模块名称。 */
  title: string;
  /** 列表模块说明。 */
  subtitle?: string;
}>();
</script>

<style scoped>
.list-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: -2px 0 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--tradn-border);
}

.list-section-title {
  color: #172b4d;
  font-size: 17px;
  font-weight: 650;
}

.list-section-actions {
  display: flex;
  flex: none;
  gap: 8px;
}
</style>

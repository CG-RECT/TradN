<template>
  <section class="search-panel" :class="{ collapsed }">
    <div class="search-panel-main">
      <div class="search-fields">
        <slot />
      </div>
      <div class="search-actions">
        <a-button type="primary" @click="$emit('search')">查询</a-button>
        <a-button @click="$emit('reset')">重置</a-button>
      </div>
    </div>
    <button
      v-if="collapsible"
      class="search-toggle"
      type="button"
      @click="collapsed = !collapsed"
    >
      {{ collapsed ? "展开更多条件" : "收起条件" }}
      <span>{{ collapsed ? "⌄" : "⌃" }}</span>
    </button>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";

withDefaults(
  defineProps<{
    /** 是否启用折叠。默认只展示查询条件的第一行。 */
    collapsible?: boolean;
  }>(),
  { collapsible: false },
);

defineEmits<{
  (event: "search"): void;
  (event: "reset"): void;
}>();

const collapsed = ref(true);
</script>

<style scoped>
.search-panel {
  margin-bottom: 14px;
  padding: 16px 18px 0;
  border: 1px solid var(--tradn-border);
  border-radius: 8px;
  background: #fff;
}

.search-panel-main {
  display: flex;
  align-items: center;
  gap: 18px;
}

.search-fields {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  min-height: 38px;
  gap: 12px 16px;
  min-width: 0;
}

.search-fields :deep(.search-field) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 220px;
}

.search-fields :deep(.search-field-label) {
  color: #536172;
  white-space: nowrap;
}

.search-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
}

.search-toggle {
  display: block;
  margin: 8px auto 0;
  padding: 3px 10px 7px;
  border: 0;
  color: var(--tradn-primary);
  background: transparent;
  cursor: pointer;
}

.search-toggle span {
  margin-left: 4px;
}

.search-panel.collapsed .search-fields :deep(.search-field:nth-child(n + 4)) {
  display: none;
}
</style>

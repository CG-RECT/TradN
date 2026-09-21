<template>
  <div class="editor-page">
    <div class="editor-page-header">
      <div>
        <div class="editor-page-title">{{ title }}</div>
        <div v-if="subtitle" class="muted">{{ subtitle }}</div>
      </div>
      <slot name="header-extra" />
    </div>

    <main class="editor-page-content">
      <slot />
    </main>

    <footer class="editor-page-footer">
      <div class="editor-page-footer-inner">
        <slot name="actions" />
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 编辑页左侧主标题。 */
  title: string;
  /** 编辑页标题下的补充说明。 */
  subtitle?: string;
}>();
</script>

<style scoped>
.editor-page {
  min-height: calc(100vh - 106px);
  padding: 24px 28px 96px;
}

.editor-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 0 0 18px;
  border-bottom: 1px solid var(--tradn-border);
}

.editor-page-title {
  font-size: 24px;
  font-weight: 650;
  color: #172b4d;
}

.editor-page-content {
  padding-top: 18px;
}

.editor-page-footer {
  position: fixed;
  z-index: 20;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 12px 28px;
  border-top: 1px solid var(--tradn-border);
  background: #fffffff2;
  box-shadow: 0 -4px 16px #1f293710;
  backdrop-filter: blur(8px);
}

.editor-page-footer-inner {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
  box-sizing: border-box;
}
</style>

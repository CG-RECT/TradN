<template>
  <div>
    <div class="query-card">
      <a-form layout="inline">
        <a-form-item label="菜单名称/权限码">
          <a-input
            v-model:value="query.keyword"
            allow-clear
            placeholder="请输入关键字"
            @pressEnter="load"
          />
        </a-form-item>
        <a-form-item label="节点类型">
          <a-select
            v-model:value="query.menuType"
            allow-clear
            placeholder="全部类型"
            :options="menuTypeOptions"
            class="type-select"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="load">查询</a-button>
            <a-button @click="resetQuery">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </div>

    <div class="menu-layout">
      <section class="tree-panel">
        <div class="panel-head">
          <strong>菜单树</strong>
          <a-button type="primary" size="small" @click="startCreate(null, false)">
            新增根节点
          </a-button>
        </div>
        <a-spin :spinning="loading">
          <a-tree
            v-if="treeData.length"
            draggable
            block-node
            default-expand-all
            :tree-data="treeData"
            :selected-keys="selectedKeys"
            :field-names="{ title: 'menu_name', key: 'id', children: 'children' }"
            @select="selectNode"
            @drop="dropNode"
          >
            <template #title="node">
              <div class="tree-title">
                <span>
                  <a-tag :color="menuTypeColor(node.menu_type)">
                    {{ menuTypeLabel(node.menu_type) }}
                  </a-tag>
                  {{ node.menu_name }}
                </span>
                <a-dropdown :trigger="['click']">
                  <a class="node-action" @click.stop>操作</a>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="startEdit(node)">编辑</a-menu-item>
                      <a-menu-item @click="startCreate(node, false)">
                        新增同级
                      </a-menu-item>
                      <a-menu-item @click="startCreate(node, true)">
                        新增子级
                      </a-menu-item>
                      <a-menu-item
                        :disabled="Number(node.built_in) === 1"
                        danger
                        @click="removeNode(node)"
                      >
                        删除节点
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>
            </template>
          </a-tree>
          <a-empty v-else description="暂无菜单" />
        </a-spin>
      </section>

      <section class="detail-panel">
        <div class="panel-head">
          <strong>{{ panelTitle }}</strong>
          <a-button
            v-if="selected && editorMode === 'view'"
            size="small"
            @click="startEdit(selected)"
          >
            编辑
          </a-button>
        </div>

        <a-descriptions v-if="selected && editorMode === 'view'" bordered :column="2">
          <a-descriptions-item label="菜单名称">
            {{ selected.menu_name }}
          </a-descriptions-item>
          <a-descriptions-item label="节点类型">
            {{ menuTypeLabel(selected.menu_type) }}
          </a-descriptions-item>
          <a-descriptions-item label="路由地址">
            {{ selected.route_path || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="组件标识">
            {{ selected.component_path || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="权限码">
            {{ selected.permission_code || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="排序号">
            {{ selected.sort_no }}
          </a-descriptions-item>
          <a-descriptions-item label="是否显示">
            {{ Number(selected.visible) === 1 ? "是" : "否" }}
          </a-descriptions-item>
          <a-descriptions-item label="内置节点">
            {{ Number(selected.built_in) === 1 ? "是" : "否" }}
          </a-descriptions-item>
        </a-descriptions>

        <a-form v-else-if="editorMode !== 'view'" layout="vertical">
          <a-form-item label="父级节点">
            <a-tree-select
              v-model:value="form.parentId"
              allow-clear
              tree-default-expand-all
              :tree-data="parentOptions"
              :field-names="{ label: 'menu_name', value: 'id', children: 'children' }"
              placeholder="不选择表示根节点"
            />
          </a-form-item>
          <a-form-item label="节点名称" required>
            <a-input v-model:value="form.menuName" />
          </a-form-item>
          <a-form-item label="节点类型" required>
            <a-select v-model:value="form.menuType" :options="menuTypeOptions" />
          </a-form-item>
          <a-form-item label="路由地址">
            <a-input v-model:value="form.routePath" placeholder="例如 /system/users" />
          </a-form-item>
          <a-form-item label="组件标识">
            <a-input v-model:value="form.componentPath" />
          </a-form-item>
          <a-form-item label="权限码">
            <a-input
              v-model:value="form.permissionCode"
              placeholder="例如 system:user:list"
            />
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="排序号">
                <a-input-number v-model:value="form.sortNo" :min="0" class="full-width" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="是否显示">
                <a-switch v-model:checked="form.visible" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-space>
            <a-button type="primary" :loading="saving" @click="saveNode">
              保存
            </a-button>
            <a-button @click="cancelEdit">取消</a-button>
          </a-space>
        </a-form>

        <a-empty v-else description="请从左侧选择一个菜单节点" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { message, Modal } from "ant-design-vue";
import http from "../../../api/http";
import { loadDictionary, toSelectOptions } from "../../../api/dictionary";

interface MenuNode {
  id: string;
  parent_id: string;
  menu_type: string;
  menu_name: string;
  route_path?: string;
  component_path?: string;
  permission_code?: string;
  icon?: string;
  sort_no: number;
  visible: number;
  built_in: number;
  children?: MenuNode[];
}

type EditorMode = "view" | "create" | "edit";

const loading = ref(false);
const saving = ref(false);
const rows = ref<MenuNode[]>([]);
const selectedKeys = ref<string[]>([]);
const editingId = ref<string>();
const editorMode = ref<EditorMode>("view");
const menuTypes = ref<any[]>([]);
const query = reactive({ keyword: "", menuType: undefined as string | undefined });
const form = reactive({
  parentId: undefined as string | undefined,
  menuType: "MENU",
  menuName: "",
  routePath: "",
  componentPath: "",
  permissionCode: "",
  icon: "",
  sortNo: 0,
  visible: true,
});

// BUTTON 是接口鉴权资源，不属于菜单树维护范围。
const menuTypeOptions = computed(() =>
  toSelectOptions(menuTypes.value.filter((item) => item.value !== "BUTTON")),
);
const treeData = computed(() => buildTree(rows.value));
const parentOptions = computed(() => buildTree(rows.value));
const selected = computed(() =>
  rows.value.find((row) => String(row.id) === String(selectedKeys.value[0])),
);
const panelTitle = computed(() => {
  if (editorMode.value === "create") return "新增菜单节点";
  if (editorMode.value === "edit") return "编辑菜单节点";
  return "节点详细信息";
});

function buildTree(source: MenuNode[]): MenuNode[] {
  const map = new Map<string, MenuNode>();
  source.forEach((row) => map.set(String(row.id), { ...row, children: [] }));
  const roots: MenuNode[] = [];
  map.forEach((node) => {
    const parent = map.get(String(node.parent_id));
    if (parent && String(node.parent_id) !== "0") {
      parent.children?.push(node);
    } else {
      roots.push(node);
    }
  });
  const sort = (nodes: MenuNode[]) => {
    nodes.sort((left, right) => Number(left.sort_no) - Number(right.sort_no));
    nodes.forEach((node) => sort(node.children || []));
  };
  sort(roots);
  return roots;
}

async function load() {
  loading.value = true;
  try {
    rows.value = await http.get("/menus", { params: query });
    if (selectedKeys.value.length && !selected.value) selectedKeys.value = [];
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = "";
  query.menuType = undefined;
  load();
}

function selectNode(keys: Array<string | number>) {
  selectedKeys.value = keys.map(String);
  editorMode.value = "view";
  editingId.value = undefined;
}

function startCreate(reference: MenuNode | null, asChild: boolean) {
  editorMode.value = "create";
  editingId.value = undefined;
  if (reference) selectedKeys.value = [String(reference.id)];
  Object.assign(form, {
    parentId: reference
      ? asChild
        ? String(reference.id)
        : String(reference.parent_id) === "0"
          ? undefined
          : String(reference.parent_id)
      : undefined,
    menuType: "MENU",
    menuName: "",
    routePath: "",
    componentPath: "",
    permissionCode: "",
    icon: "",
    sortNo: reference ? Number(reference.sort_no) + 1 : 0,
    visible: true,
  });
}

function startEdit(node: MenuNode) {
  selectedKeys.value = [String(node.id)];
  editorMode.value = "edit";
  editingId.value = String(node.id);
  Object.assign(form, {
    parentId: String(node.parent_id) === "0" ? undefined : String(node.parent_id),
    menuType: node.menu_type,
    menuName: node.menu_name,
    routePath: node.route_path || "",
    componentPath: node.component_path || "",
    permissionCode: node.permission_code || "",
    icon: node.icon || "",
    sortNo: Number(node.sort_no),
    visible: Number(node.visible) === 1,
  });
}

function cancelEdit() {
  editorMode.value = "view";
  editingId.value = undefined;
}

async function saveNode() {
  if (!form.menuName.trim()) {
    message.warning("请输入节点名称");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      ...form,
      parentId: form.parentId || "0",
      visible: form.visible ? 1 : 0,
    };
    let savedId = editingId.value;
    if (editingId.value) {
      await http.put(`/menus/${editingId.value}`, payload);
    } else {
      savedId = await http.post("/menus", payload);
    }
    message.success("菜单节点已保存");
    await load();
    selectedKeys.value = savedId ? [String(savedId)] : selectedKeys.value;
    cancelEdit();
  } finally {
    saving.value = false;
  }
}

function removeNode(node: MenuNode) {
  Modal.confirm({
    title: `删除菜单“${node.menu_name}”？`,
    content: "存在子节点或属于系统内置菜单时，后端会拒绝删除。",
    onOk: async () => {
      await http.delete(`/menus/${node.id}`);
      selectedKeys.value = [];
      message.success("菜单节点已删除");
      await load();
    },
  });
}

/** 拖到节点上表示成为其子级，拖到节点间隙表示调整同级顺序。 */
async function dropNode(info: any) {
  const drag = info.dragNode;
  const drop = info.node;
  const parentId = info.dropToGap ? drop.parent_id || "0" : drop.id;
  const sortNo = info.dropToGap ? Number(drop.sort_no) + 1 : 0;
  await http.put(`/menus/${drag.id}/move`, { parentId, sortNo });
  message.success("菜单顺序已更新");
  await load();
}

function menuTypeLabel(value: string) {
  return menuTypes.value.find((item) => item.value === value)?.label || value;
}

function menuTypeColor(value: string) {
  return menuTypes.value.find((item) => item.value === value)?.color || "default";
}

onMounted(async () => {
  menuTypes.value = await loadDictionary("MENU_TYPE");
  await load();
});
</script>

<style scoped>
.query-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.type-select {
  width: 150px;
}

.menu-layout {
  display: grid;
  grid-template-columns: minmax(360px, 42%) 1fr;
  gap: 16px;
}

.tree-panel,
.detail-panel {
  min-height: 580px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 14px;
  margin-bottom: 14px;
  border-bottom: 1px solid #f0f0f0;
}

.tree-title {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-right: 8px;
}

.node-action {
  opacity: 0;
  transition: opacity 0.15s;
}

.tree-title:hover .node-action {
  opacity: 1;
}

@media (max-width: 1000px) {
  .menu-layout {
    grid-template-columns: 1fr;
  }
}
</style>

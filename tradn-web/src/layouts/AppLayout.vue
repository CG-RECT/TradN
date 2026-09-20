<template>
  <a-layout class="shell">
    <a-layout-sider v-model:collapsed="collapsed" :trigger="null" theme="dark">
      <div class="logo">{{ collapsed ? "TN" : "TradN" }}</div>
      <a-menu
        theme="dark"
        mode="inline"
        :selected-keys="[selected]"
        :open-keys="collapsed ? [] : openKeys"
        @click="navigate"
        @openChange="changeOpenKeys"
      >
        <template v-for="item in visibleItems" :key="item.key">
          <a-sub-menu v-if="item.children" :key="item.key">
            <template #title>
              <component :is="item.icon" />
              <span>{{ item.label }}</span>
            </template>
            <template v-for="group in item.children" :key="group.key">
              <a-sub-menu v-if="group.children" :key="group.key">
                <template #title>{{ group.label }}</template>
                <a-menu-item v-for="child in group.children" :key="child.key">
                  {{ child.label }}
                </a-menu-item>
              </a-sub-menu>
              <a-menu-item v-else :key="group.key">
                {{ group.label }}
              </a-menu-item>
            </template>
          </a-sub-menu>
          <a-menu-item v-else :key="item.key">
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
          </a-menu-item>
        </template>
      </a-menu>
      <button class="sidebar-toggle" type="button" :title="collapsed ? '展开菜单' : '收起菜单'" @click="collapsed = !collapsed">
        <MenuUnfoldOutlined v-if="collapsed" />
        <MenuFoldOutlined v-else />
        <span v-if="!collapsed">收起菜单</span>
      </button>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="header">
        <div class="header-title-wrap">
          <span class="header-title">黄金交易复盘工作台</span>
          <span class="header-subtitle">记录每一次判断，复盘每一次交易</span>
        </div>
        <div class="header-tools">
          <a-popover placement="bottomRight" trigger="click">
            <template #content>
              <div class="theme-panel">
                <div class="theme-title">界面主题</div>
                <button v-for="item in themes" :key="item.key" type="button" class="theme-option" :class="{ active: themeKey === item.key }" @click="applyTheme(item.key)">
                  <i :style="{ background: item.color }" />{{ item.label }}
                </button>
              </div>
            </template>
            <a-button type="text" class="theme-trigger"><BgColorsOutlined /> 主题</a-button>
          </a-popover>
          <a-dropdown>
          <span class="user">
            {{ auth.profile?.nickname || auth.profile?.username }} ▾
          </span>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="logout">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        </div>
      </a-layout-header>
      <div class="breadcrumb-bar">
        <a-breadcrumb>
          <a-breadcrumb-item>工作台</a-breadcrumb-item>
          <a-breadcrumb-item>{{ currentLabel }}</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
      <a-layout-content>
        <a-spin v-if="sessionLoading" class="session-state" tip="正在验证登录状态…" />
        <a-result v-else-if="sessionError" status="warning" title="登录信息加载失败">
          <template #extra>
            <a-button type="primary" @click="loadSession">重试</a-button>
          </template>
        </a-result>
        <router-view v-else />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  BookOutlined,
  CalendarOutlined,
  BgColorsOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LineChartOutlined,
  SettingOutlined,
  SwapOutlined,
} from "@ant-design/icons-vue";
import { useAuthStore } from "../stores/auth";

interface NavigationItem {
  key: string;
  label: string;
  permission?: string;
  icon?: unknown;
  children?: NavigationItem[];
}

const collapsed = ref(false);
const openKeys = ref<string[]>(["system"]);
const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const sessionLoading = ref(true);
const sessionError = ref(false);
const themeKey = ref(localStorage.getItem("tradn-theme") || "ocean");
const themes = [
  { key: "ocean", label: "海洋蓝", color: "#1677ff" },
  { key: "forest", label: "森林绿", color: "#248a5a" },
  { key: "violet", label: "雅致紫", color: "#7251b5" },
  { key: "sunset", label: "暖阳橙", color: "#d97732" },
];

const routeLabels: Record<string, string> = {
  "/trades": "开仓记录",
  "/notes": "笔记",
  "/timeline": "黄金时间线",
  "/statistics": "盈亏统计",
  "/system": "系统设置",
};
const currentLabel = computed(() => {
  const key = Object.keys(routeLabels).find((path) => route.path.startsWith(path));
  return key ? routeLabels[key] : "工作台";
});

function applyTheme(key: string) {
  themeKey.value = key;
  localStorage.setItem("tradn-theme", key);
  document.documentElement.dataset.theme = key;
}

const items: NavigationItem[] = [
  {
    key: "/trades",
    label: "开仓记录",
    permission: "trade:record:list",
    icon: SwapOutlined,
  },
  {
    key: "/notes",
    label: "笔记",
    permission: "note:note:list",
    icon: BookOutlined,
  },
  {
    key: "/timeline",
    label: "黄金时间线",
    permission: "timeline:daily:view",
    icon: CalendarOutlined,
  },
  {
    key: "/statistics",
    label: "盈亏统计",
    permission: "trade:statistics:view",
    icon: LineChartOutlined,
  },
  {
    key: "system",
    label: "系统设置",
    permission: "system:settings:view",
    icon: SettingOutlined,
    children: [
      {
        key: "system-personnel",
        label: "人员管理",
        children: [
          {
            key: "/system/users",
            label: "账号管理",
            permission: "system:user:list",
          },
          {
            key: "/system/roles",
            label: "角色管理",
            permission: "system:role:list",
          },
          {
            key: "/system/menus",
            label: "菜单管理",
            permission: "system:menu:list",
          },
        ],
      },
      {
        key: "system-basic",
        label: "基础管理",
        children: [
          {
            key: "/system/dictionaries",
            label: "数据字典",
            permission: "system:dict:list",
          },
          {
            key: "/system/parameters",
            label: "系统参数",
            permission: "system:parameter:list",
          },
          {
            key: "/system/jobs",
            label: "系统调度",
            permission: "system:scheduler:list",
          },
          {
            key: "/system/caches",
            label: "缓存管理",
            permission: "system:cache:view",
          },
          {
            key: "/system/exceptions",
            label: "异常日志",
            permission: "system:exception:view",
          },
        ],
      },
      {
        key: "system-audit",
        label: "审计管理",
        children: [
          {
            key: "/system/audit-login",
            label: "登录审计",
            permission: "system:audit:login:view",
          },
          {
            key: "/system/audit-access",
            label: "访问审计",
            permission: "system:audit:access:view",
          },
        ],
      },
    ],
  },
];

/** 递归过滤没有权限的叶子菜单，同时移除过滤后为空的分组。 */
function filterItems(source: NavigationItem[]): NavigationItem[] {
  return source
    .filter((item) => !item.permission || auth.can(item.permission))
    .map((item) => ({
      ...item,
      children: item.children ? filterItems(item.children) : undefined,
    }))
    .filter((item) => !item.children || item.children.length > 0);
}

const visibleItems = computed(() => filterItems(items));
const selected = computed(() => {
  if (route.path.startsWith("/trades")) {
    return "/trades";
  }
  if (route.path.startsWith("/notes")) {
    return "/notes";
  }
  return route.path;
});

function navigate(event: { key: string }) {
  if (event.key.startsWith("/")) {
    router.push(event.key);
  }
}

function changeOpenKeys(keys: string[]) {
  openKeys.value = keys;
}

async function logout() {
  await auth.logout();
  router.push("/login");
}

async function loadSession() {
  sessionLoading.value = true;
  sessionError.value = false;
  try {
    // 验证成功后才加载业务页，避免过期登录同时触发多个失败请求和空菜单。
    await auth.load();
  } catch {
    sessionError.value = true;
  } finally {
    sessionLoading.value = false;
  }
}

onMounted(() => {
  applyTheme(themeKey.value);
  loadSession();
});
</script>

<style scoped>
.session-state {
  display: block;
  margin: 80px auto;
}
:deep(.ant-layout-sider) { background: var(--tradn-sidebar) !important; }
:deep(.ant-menu-dark) { background: var(--tradn-sidebar); }
:deep(.ant-menu-dark .ant-menu-item-selected) { background: var(--tradn-primary) !important; }
:deep(.ant-menu-dark .ant-menu-item:hover), :deep(.ant-menu-dark .ant-menu-submenu-title:hover) { background: var(--tradn-sidebar-hover) !important; }

.shell {
  min-height: 100%;
}

.logo {
  height: 64px;
  color: #d6b34c;
  display: grid;
  place-items: center;
  font-size: 24px;
  font-weight: 700;
}

.header {
  height: 64px;
  background: #fff;
  padding: 0 28px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--tradn-border);
}

.header-title-wrap { display: flex; align-items: baseline; gap: 14px; }
.header-subtitle { color: #8a94a6; font-size: 12px; }
.header-tools { display: flex; align-items: center; gap: 10px; }
.theme-trigger { color: #526173; }
.sidebar-toggle { position: absolute; left: 12px; right: 12px; bottom: 14px; height: 36px; border: 0; border-radius: 8px; color: #dce9f7; background: #ffffff12; cursor: pointer; text-align: left; padding: 0 12px; }
.sidebar-toggle:hover { background: #ffffff24; }
.sidebar-toggle span { margin-left: 10px; font-size: 12px; }
.theme-panel { min-width: 150px; }
.theme-title { color: #8c8c8c; font-size: 12px; margin-bottom: 8px; }
.theme-option { display: block; width: 100%; padding: 7px 4px; border: 0; background: transparent; text-align: left; cursor: pointer; border-radius: 5px; }
.theme-option:hover, .theme-option.active { background: #f0f5ff; }
.theme-option i { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 8px; vertical-align: -1px; }
.breadcrumb-bar { height: 42px; padding: 0 28px; display: flex; align-items: center; background: var(--tradn-breadcrumb); border-bottom: 1px solid var(--tradn-border); }

.header-title {
  font-size: 17px;
  font-weight: 500;
}

.user {
  cursor: pointer;
}
</style>

<template>
  <a-layout class="shell">
    <a-layout-sider v-model:collapsed="collapsed" collapsible theme="dark">
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
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="header">
        <span class="header-title">黄金交易复盘工作台</span>
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
      </a-layout-header>
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

onMounted(loadSession);
</script>

<style scoped>
.session-state {
  display: block;
  margin: 80px auto;
}

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
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 5px #00000010;
}

.header-title {
  font-size: 17px;
  font-weight: 500;
}

.user {
  cursor: pointer;
}
</style>

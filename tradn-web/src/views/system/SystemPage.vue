<template>
  <div class="page system-page">
    <div class="page-header">
      <div>
        <div class="page-title">{{ config.title }}</div>
        <div class="muted">{{ config.description }}</div>
      </div>
    </div>
    <div class="page-actions" v-if="section !== 'menus'">
      <a-space>
        <a-button v-if="section === 'users'" type="primary" @click="openUserEditor">
          新增账号
        </a-button>
        <template v-if="section === 'roles'">
          <a-button type="primary" @click="openRoleEditor()">新增角色</a-button>
        </template>
        <template v-if="section === 'dictionaries'">
          <a-button @click="openTypeManager">字典类型管理</a-button>
          <a-button type="primary" @click="openDictionaryItem()">新增字典项</a-button>
        </template>
        <a-button
          v-if="section === 'parameters'"
          type="primary"
          @click="openParameterEditor()"
        >
          新增参数
        </a-button>
        <a-button v-if="section === 'jobs'" type="primary" @click="openJobEditor()">
          新增调度
        </a-button>
        <a-button v-if="section === 'caches'" type="primary" @click="openCacheEditor()">
          新增缓存
        </a-button>
      </a-space>
    </div>

    <MenuManager v-if="section === 'menus'" />

    <template v-else>
      <div class="query-card">
        <a-form layout="inline">
          <a-form-item
            v-for="field in queryFields"
            :key="field.name"
            :label="field.label"
          >
            <a-input
              v-if="field.type === 'input'"
              v-model:value="query[field.name]"
              allow-clear
              :placeholder="field.placeholder || `请输入${field.label}`"
              @pressEnter="search"
            />
            <a-select
              v-else-if="field.type === 'select'"
              v-model:value="query[field.name]"
              allow-clear
              :placeholder="`全部${field.label}`"
              :options="field.options"
              style="width: 160px"
            />
            <a-range-picker
              v-else-if="field.type === 'range'"
              v-model:value="query[field.name]"
              show-time
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="search">查询</a-button>
              <a-button @click="resetQuery">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </div>

      <div class="content-card">
        <a-table
          :data-source="rows"
          :columns="columns"
          :loading="loading"
          :pagination="pagination"
          :row-key="rowKey"
          :scroll="{ x: tableWidth }"
          @change="changeTable"
        >
          <template #bodyCell="{ column, record, text }">
            <template v-if="column.dict">
              <a-tag :color="dictColor(column.dict, text)">
                {{ dictLabel(column.dict, text) }}
              </a-tag>
            </template>
            <template v-else-if="column.time">
              {{ formatDateTime(text) }}
            </template>
            <template v-else-if="column.boolean">
              {{ formatBoolean(text) }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space wrap>
                <template v-if="section === 'users'">
                  <a @click="showUserRoles(record)">查询关联角色</a>
                  <a
                    v-if="record.status === 'ENABLED'"
                    class="danger-text"
                    @click="changeUserStatus(record, 'DISABLED')"
                  >
                    禁用
                  </a>
                  <a v-else @click="changeUserStatus(record, 'ENABLED')">启用</a>
                  <a @click="forceLogout(record.id)">强制下线</a>
                  <a @click="resetPassword(record.id)">重置密码</a>
                </template>
                <template v-else-if="section === 'roles'">
                  <a @click="openAuthorization(record)">授权</a>
                  <a
                    :class="{ disabled: Number(record.built_in) === 1 }"
                    @click="Number(record.built_in) !== 1 && openRoleEditor(record)"
                  >
                    编辑
                  </a>
                  <a-popconfirm
                    title="确定删除该角色？"
                    :disabled="Number(record.built_in) === 1"
                    @confirm="deleteRole(record)"
                  >
                    <a
                      class="danger-text"
                      :class="{ disabled: Number(record.built_in) === 1 }"
                    >
                      删除
                    </a>
                  </a-popconfirm>
                </template>
                <template v-else-if="section === 'dictionaries'">
                  <a v-if="record.id" @click="openDictionaryItem(record)">编辑</a>
                  <a-popconfirm
                    v-if="record.id"
                    title="确定删除该字典项？"
                    :disabled="Number(record.built_in) === 1"
                    @confirm="deleteDictionaryItem(record)"
                  >
                    <a
                      class="danger-text"
                      :class="{ disabled: Number(record.built_in) === 1 }"
                    >
                      删除
                    </a>
                  </a-popconfirm>
                </template>
                <template v-else-if="section === 'parameters'">
                  <a @click="openParameterEditor(record)">修改</a>
                  <a-popconfirm title="确定删除该系统参数？" @confirm="deleteParameter(record)">
                    <a class="danger-text">删除</a>
                  </a-popconfirm>
                </template>
                <template v-else-if="section === 'jobs'">
                  <a @click="openJobEditor(record)">修改</a>
                  <a @click="trigger(record.id)">立即执行</a>
                  <a
                    v-if="record.status === 'ENABLED'"
                    @click="jobAction(record.id, 'pause')"
                  >
                    暂停
                  </a>
                  <a v-else @click="jobAction(record.id, 'resume')">恢复</a>
                  <a-popconfirm title="确定删除该调度任务？" @confirm="deleteJob(record)">
                    <a class="danger-text">删除</a>
                  </a-popconfirm>
                </template>
                <template v-else-if="section === 'caches'">
                  <a @click="openCacheEditor(record)">修改</a>
                  <a-popconfirm
                    title="确定清理该命名空间？"
                    @confirm="clearCache(record.namespace)"
                  >
                    <a class="danger-text">清理</a>
                  </a-popconfirm>
                  <a-popconfirm
                    title="确定删除该缓存命名空间？"
                    :disabled="Number(record.built_in) === 1"
                    @confirm="deleteCache(record)"
                  >
                    <a
                      class="danger-text"
                      :class="{ disabled: Number(record.built_in) === 1 }"
                    >
                      删除
                    </a>
                  </a-popconfirm>
                </template>
                <template v-else-if="section === 'exceptions'">
                  <a @click="handleException(record)">处理</a>
                </template>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </template>

    <a-modal v-model:open="userEditorOpen" title="新增账号" @ok="createUser">
      <a-form layout="vertical">
        <a-form-item label="用户名" required>
          <a-input v-model:value="userForm.username" />
        </a-form-item>
        <a-form-item label="昵称" required>
          <a-input v-model:value="userForm.nickname" />
        </a-form-item>
        <a-form-item label="初始密码" required>
          <a-input-password
            v-model:value="userForm.password"
            placeholder="至少10位"
          />
        </a-form-item>
        <a-form-item label="角色">
          <a-select
            v-model:value="userForm.roleIds"
            mode="multiple"
            :options="roleOptions"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="userRolesOpen" title="账号关联角色" :footer="null">
      <a-table
        size="small"
        :data-source="currentUserRoles"
        :pagination="false"
        row-key="id"
      >
        <a-table-column title="角色编码" data-index="role_code" />
        <a-table-column title="角色名称" data-index="role_name" />
        <a-table-column title="状态" data-index="status">
          <template #default="{ text }">
            <a-tag :color="dictColor('COMMON_STATUS', text)">
              {{ dictLabel("COMMON_STATUS", text) }}
            </a-tag>
          </template>
        </a-table-column>
      </a-table>
    </a-modal>

    <a-modal
      v-model:open="roleEditorOpen"
      :title="roleForm.id ? '编辑角色' : '新增角色'"
      @ok="saveRole"
    >
      <a-form layout="vertical">
        <a-form-item label="角色编码" required>
          <a-input v-model:value="roleForm.roleCode" />
        </a-form-item>
        <a-form-item label="角色名称" required>
          <a-input v-model:value="roleForm.roleName" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="roleForm.status" :options="statusOptions" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="authorizationOpen"
      :title="`角色授权 - ${authorizationRole?.role_name || ''}`"
      width="900px"
      :ok-button-props="{ disabled: authorizationRole?.role_code === 'ADMIN' }"
      @ok="saveAuthorization"
    >
      <a-alert
        v-if="authorizationRole?.role_code === 'ADMIN'"
        type="info"
        show-icon
        message="系统管理员默认拥有全部菜单权限，授权不能修改。"
        style="margin-bottom: 16px"
      />
      <div class="authorization-grid">
        <section>
          <h3>可选择</h3>
          <a-tree
            checkable
            :disabled="authorizationRole?.role_code === 'ADMIN'"
            default-expand-all
            :tree-data="authorizationTree"
            :checked-keys="checkedMenuIds"
            :field-names="{ title: 'menu_name', key: 'id', children: 'children' }"
            @check="checkMenus"
          />
        </section>
        <section>
          <h3>已选择（{{ checkedMenuIds.length }} 项）</h3>
          <a-tree
            default-expand-all
            :tree-data="selectedAuthorizationTree"
            :field-names="{ title: 'menu_name', key: 'id', children: 'children' }"
          />
        </section>
      </div>
    </a-modal>

    <a-modal
      v-model:open="dictionaryItemOpen"
      :title="dictionaryItemForm.id ? '编辑字典项' : '新增字典项'"
      @ok="saveDictionaryItem"
    >
      <a-form layout="vertical">
        <a-form-item label="字典类型" required>
          <a-select
            v-model:value="dictionaryItemForm.typeId"
            :options="dictionaryTypeOptions"
          />
        </a-form-item>
        <a-form-item label="项编码" required>
          <a-input v-model:value="dictionaryItemForm.itemCode" />
        </a-form-item>
        <a-form-item label="业务值" required>
          <a-input v-model:value="dictionaryItemForm.itemValue" />
        </a-form-item>
        <a-form-item label="显示文本" required>
          <a-input v-model:value="dictionaryItemForm.itemLabel" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="排序号">
              <a-input-number
                v-model:value="dictionaryItemForm.sortNo"
                :min="0"
                class="full-width"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-select
                v-model:value="dictionaryItemForm.status"
                :options="statusOptions"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="标签颜色">
          <a-input
            v-model:value="dictionaryItemForm.displayStyle"
            placeholder="default/blue/green/orange/red"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="typeManagerOpen"
      title="字典类型管理"
      width="900px"
      :footer="null"
    >
      <div class="modal-toolbar">
        <a-button type="primary" @click="openDictionaryType()">新增类型</a-button>
      </div>
      <a-table
        :data-source="dictionaryTypes"
        row-key="id"
        :pagination="{ pageSize: 10 }"
      >
        <a-table-column title="类型编码" data-index="type_code" />
        <a-table-column title="类型名称" data-index="type_name" />
        <a-table-column title="说明" data-index="description" />
        <a-table-column title="操作" key="action" width="130">
          <template #default="{ record }">
            <a-space>
              <a @click="openDictionaryType(record)">编辑</a>
              <a-popconfirm
                title="确定删除该字典类型？"
                :disabled="Number(record.built_in) === 1"
                @confirm="deleteDictionaryType(record)"
              >
                <a
                  class="danger-text"
                  :class="{ disabled: Number(record.built_in) === 1 }"
                >
                  删除
                </a>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-modal>

    <a-modal
      v-model:open="dictionaryTypeOpen"
      :title="dictionaryTypeForm.id ? '编辑字典类型' : '新增字典类型'"
      @ok="saveDictionaryType"
    >
      <a-form layout="vertical">
        <a-form-item label="类型编码" required>
          <a-input v-model:value="dictionaryTypeForm.typeCode" />
        </a-form-item>
        <a-form-item label="类型名称" required>
          <a-input v-model:value="dictionaryTypeForm.typeName" />
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="dictionaryTypeForm.description" :rows="3" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select
            v-model:value="dictionaryTypeForm.status"
            :options="statusOptions"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="parameterEditorOpen"
      :title="parameterForm.id ? '修改系统参数' : '新增系统参数'"
      @ok="saveParameter"
    >
      <a-form layout="vertical">
        <a-form-item label="参数编码" required>
          <a-input v-model:value="parameterForm.paramKey" />
        </a-form-item>
        <a-form-item label="参数名称" required>
          <a-input v-model:value="parameterForm.paramName" />
        </a-form-item>
        <a-form-item label="参数类型" required>
          <a-select v-model:value="parameterForm.paramType" :options="parameterTypeOptions" />
        </a-form-item>
        <a-form-item label="当前值" required>
          <a-input
            v-model:value="parameterForm.paramValue"
            :placeholder="parameterForm.isSensitive ? '敏感值不会回显，请输入新值' : ''"
          />
        </a-form-item>
        <a-form-item label="默认值">
          <a-input v-model:value="parameterForm.defaultValue" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="敏感参数">
              <a-switch v-model:checked="parameterForm.isSensitive" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="动态生效">
              <a-switch v-model:checked="parameterForm.dynamicEffect" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="说明">
          <a-textarea v-model:value="parameterForm.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="jobEditorOpen"
      :title="jobForm.id ? '修改调度任务' : '新增调度任务'"
      @ok="saveJob"
    >
      <a-form layout="vertical">
        <a-form-item label="任务编码" required>
          <a-select v-model:value="jobForm.jobCode" :options="jobCodeOptions" />
        </a-form-item>
        <a-form-item label="任务名称" required>
          <a-input v-model:value="jobForm.jobName" />
        </a-form-item>
        <a-form-item label="Cron 表达式" required>
          <a-input v-model:value="jobForm.cronExpression" placeholder="例如 0 0 2 * * ?" />
        </a-form-item>
        <a-form-item label="时区">
          <a-input v-model:value="jobForm.timezone" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="jobForm.status" :options="jobStatusOptions" />
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="jobForm.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="cacheEditorOpen"
      :title="cacheForm.id ? '修改缓存命名空间' : '新增缓存命名空间'"
      @ok="saveCache"
    >
      <a-form layout="vertical">
        <a-form-item label="命名空间" required>
          <a-input v-model:value="cacheForm.namespace" placeholder="例如 market-data" />
        </a-form-item>
        <a-form-item label="显示名称" required>
          <a-input v-model:value="cacheForm.displayName" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="cacheForm.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="cacheForm.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from "vue";
import { message, Modal } from "ant-design-vue";
import { useRoute } from "vue-router";
import http from "../../api/http";
import {
  dictionaryColor,
  dictionaryLabel,
  loadDictionary,
  toSelectOptions,
} from "../../api/dictionary";
import { formatBoolean, formatDateTime } from "../../utils/format";
import MenuManager from "./components/MenuManager.vue";

interface QueryField {
  name: string;
  label: string;
  type: "input" | "select" | "range";
  placeholder?: string;
  options?: Array<{ value: string | number; label: string }>;
}

interface TableColumn {
  title: string;
  dataIndex?: string;
  key?: string;
  width?: number;
  ellipsis?: boolean;
  dict?: string;
  time?: boolean;
  boolean?: boolean;
}

const route = useRoute();
const rows = ref<any[]>([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const dictionaries = reactive<Record<string, any[]>>({});
const query = reactive<Record<string, any>>({});

const userEditorOpen = ref(false);
const userRolesOpen = ref(false);
const currentUserRoles = ref<any[]>([]);
const roleEditorOpen = ref(false);
const authorizationOpen = ref(false);
const authorizationRole = ref<any>();
const checkedMenuIds = ref<string[]>([]);
const allMenus = ref<any[]>([]);
const roleOptions = ref<any[]>([]);
const dictionaryItemOpen = ref(false);
const dictionaryTypes = ref<any[]>([]);
const typeManagerOpen = ref(false);
const dictionaryTypeOpen = ref(false);
const parameterEditorOpen = ref(false);
const jobEditorOpen = ref(false);
const cacheEditorOpen = ref(false);

const userForm = reactive({
  username: "",
  nickname: "",
  password: "",
  roleIds: [] as string[],
});
const roleForm = reactive({
  id: "",
  roleCode: "",
  roleName: "",
  status: "ENABLED",
});
const dictionaryItemForm = reactive({
  id: "",
  typeId: undefined as string | undefined,
  itemCode: "",
  itemValue: "",
  itemLabel: "",
  sortNo: 0,
  status: "ENABLED",
  displayStyle: "default",
});
const dictionaryTypeForm = reactive({
  id: "",
  typeCode: "",
  typeName: "",
  description: "",
  status: "ENABLED",
});
const parameterForm = reactive({
  id: "",
  paramKey: "",
  paramName: "",
  paramType: "STRING",
  paramValue: "",
  defaultValue: "",
  isSensitive: false,
  dynamicEffect: true,
  description: "",
});
const jobForm = reactive({
  id: "",
  jobCode: "AUDIT_LOG_CLEANUP",
  jobName: "",
  cronExpression: "0 0 2 * * ?",
  timezone: "Asia/Shanghai",
  status: "PAUSED",
  description: "",
});
const cacheForm = reactive({
  id: "",
  namespace: "",
  displayName: "",
  description: "",
  status: "ENABLED",
});

const section = computed(() => String(route.params.section));
const pagedSections = ["audit-login", "audit-access", "exceptions"];

const configs: Record<string, { title: string; description: string; endpoint: string }> = {
  users: {
    title: "账号管理",
    description: "维护账号、关联角色、启停状态和登录会话",
    endpoint: "/users",
  },
  roles: {
    title: "角色管理",
    description: "角色只有获得菜单授权后，关联账号才会显示对应菜单",
    endpoint: "/roles",
  },
  menus: {
    title: "菜单管理",
    description: "使用树形结构维护目录、页面菜单和显示顺序",
    endpoint: "/menus",
  },
  dictionaries: {
    title: "数据字典",
    description: "业务表保存稳定编码，页面通过字典显示中文名称",
    endpoint: "/dictionaries",
  },
  parameters: {
    title: "系统参数",
    description: "维护运行参数；敏感参数不会回显旧值",
    endpoint: "/system-parameters",
  },
  jobs: {
    title: "系统调度",
    description: "新增、维护、删除和执行代码白名单任务",
    endpoint: "/system-jobs",
  },
  caches: {
    title: "缓存管理",
    description: "维护 Redis 缓存命名空间并按范围安全清理缓存",
    endpoint: "/caches",
  },
  exceptions: {
    title: "异常日志",
    description: "按类型、接口、处理状态和发生时间分页查询",
    endpoint: "/exception-logs",
  },
  "audit-login": {
    title: "登录审计",
    description: "查看登录成功、失败原因、IP 和请求链路",
    endpoint: "/audits/login",
  },
  "audit-access": {
    title: "访问审计",
    description: "查看关键接口访问结果、耗时和请求链路",
    endpoint: "/audits/access",
  },
};

const config = computed(() => configs[section.value] || configs.users);
const statusOptions = computed(() => toSelectOptions(dictionaries.COMMON_STATUS || []));
const parameterTypeOptions = ["STRING", "INTEGER", "BOOLEAN"].map((value) => ({
  value,
  label: value,
}));
const jobStatusOptions = [
  { value: "ENABLED", label: "运行中" },
  { value: "PAUSED", label: "已暂停" },
];
const jobCodeOptions = [
  { value: "AUDIT_LOG_CLEANUP", label: "审计日志清理" },
  { value: "ORPHAN_FILE_CLEANUP", label: "孤立文件清理" },
  { value: "TIMELINE_SUMMARY_REPAIR", label: "时间线摘要修复" },
];
const dictionaryTypeOptions = computed(() =>
  dictionaryTypes.value.map((item) => ({
    value: String(item.id),
    label: `${item.type_name}（${item.type_code}）`,
  })),
);

const queryFields = computed<QueryField[]>(() => {
  const status = statusOptions.value;
  const fields: Record<string, QueryField[]> = {
    users: [
      { name: "username", label: "用户名", type: "input" },
      { name: "nickname", label: "昵称", type: "input" },
      { name: "status", label: "状态", type: "select", options: status },
    ],
    roles: [
      { name: "roleCode", label: "角色编码", type: "input" },
      { name: "roleName", label: "角色名称", type: "input" },
      { name: "status", label: "状态", type: "select", options: status },
    ],
    dictionaries: [
      { name: "typeCode", label: "类型编码", type: "input" },
      { name: "typeName", label: "类型名称", type: "input" },
      { name: "itemLabel", label: "显示文本", type: "input" },
      { name: "status", label: "状态", type: "select", options: status },
    ],
    parameters: [
      { name: "key", label: "参数键", type: "input" },
      { name: "name", label: "参数名称", type: "input" },
      {
        name: "type",
        label: "参数类型",
        type: "select",
        options: ["STRING", "INTEGER", "BOOLEAN"].map((value) => ({ value, label: value })),
      },
    ],
    jobs: [
      { name: "keyword", label: "任务名称/编码", type: "input" },
      {
        name: "status",
        label: "状态",
        type: "select",
        options: [
          { value: "ENABLED", label: "运行中" },
          { value: "PAUSED", label: "已暂停" },
        ],
      },
    ],
    caches: [{ name: "keyword", label: "命名空间", type: "input" }],
    exceptions: [
      { name: "exceptionType", label: "异常类型", type: "input" },
      { name: "endpoint", label: "接口", type: "input" },
      {
        name: "handleStatus",
        label: "处理状态",
        type: "select",
        options: toSelectOptions(dictionaries.EXCEPTION_STATUS || []),
      },
      { name: "timeRange", label: "发生时间", type: "range" },
    ],
    "audit-login": [
      { name: "username", label: "用户名", type: "input" },
      {
        name: "success",
        label: "是否成功",
        type: "select",
        options: (dictionaries.LOGIN_SUCCESS || []).map((item) => ({
          value: Number(item.value),
          label: item.label,
        })),
      },
      {
        name: "reasonCode",
        label: "登录结果",
        type: "select",
        options: toSelectOptions(dictionaries.LOGIN_REASON || []),
      },
      { name: "timeRange", label: "登录时间", type: "range" },
    ],
    "audit-access": [
      { name: "username", label: "用户名", type: "input" },
      { name: "endpoint", label: "接口", type: "input" },
      {
        name: "result",
        label: "访问结果",
        type: "select",
        options: toSelectOptions(dictionaries.ACCESS_RESULT || []),
      },
      { name: "responseStatus", label: "状态码", type: "input" },
      { name: "timeRange", label: "访问时间", type: "range" },
    ],
  };
  return fields[section.value] || [];
});

const columns = computed<TableColumn[]>(() => {
  const map: Record<string, TableColumn[]> = {
    users: [
      { title: "用户名", dataIndex: "username", width: 150 },
      { title: "昵称", dataIndex: "nickname", width: 150 },
      { title: "状态", dataIndex: "status", dict: "COMMON_STATUS", width: 100 },
      { title: "最后登录", dataIndex: "last_login_at", time: true, width: 180 },
      { title: "更新时间", dataIndex: "updated_at", time: true, width: 180 },
      { title: "操作", key: "action", width: 330 },
    ],
    roles: [
      { title: "角色编码", dataIndex: "role_code", width: 160 },
      { title: "角色名称", dataIndex: "role_name", width: 160 },
      { title: "状态", dataIndex: "status", dict: "COMMON_STATUS", width: 100 },
      { title: "内置角色", dataIndex: "built_in", boolean: true, width: 100 },
      { title: "更新时间", dataIndex: "updated_at", time: true, width: 180 },
      { title: "操作", key: "action", width: 180 },
    ],
    dictionaries: [
      { title: "类型编码", dataIndex: "type_code", width: 180 },
      { title: "类型名称", dataIndex: "type_name", width: 160 },
      { title: "项编码", dataIndex: "item_code", width: 180 },
      { title: "业务值", dataIndex: "item_value", width: 160 },
      { title: "显示文本", dataIndex: "item_label", width: 160 },
      { title: "状态", dataIndex: "status", dict: "COMMON_STATUS", width: 100 },
      { title: "操作", key: "action", width: 120 },
    ],
    parameters: [
      { title: "参数键", dataIndex: "param_key", width: 250 },
      { title: "参数名称", dataIndex: "param_name", width: 190 },
      { title: "类型", dataIndex: "param_type", width: 100 },
      { title: "当前值", dataIndex: "param_value", width: 180, ellipsis: true },
      { title: "动态生效", dataIndex: "dynamic_effect", boolean: true, width: 100 },
      { title: "操作", key: "action", width: 130 },
    ],
    jobs: [
      { title: "任务名称", dataIndex: "job_name", width: 180 },
      { title: "任务编码", dataIndex: "job_code", width: 210 },
      { title: "Cron", dataIndex: "cron_expression", width: 150 },
      { title: "状态", dataIndex: "status", width: 100 },
      { title: "上次执行", dataIndex: "last_run_at", time: true, width: 180 },
      { title: "下次执行", dataIndex: "next_run_at", time: true, width: 180 },
      { title: "操作", key: "action", width: 260 },
    ],
    caches: [
      { title: "命名空间", dataIndex: "namespace", width: 180 },
      { title: "显示名称", dataIndex: "display_name", width: 160 },
      { title: "状态", dataIndex: "status", dict: "COMMON_STATUS", width: 100 },
      { title: "键模式", dataIndex: "pattern", width: 300 },
      { title: "键数量估算", dataIndex: "keyCount", width: 130 },
      { title: "操作", key: "action", width: 170 },
    ],
    "audit-login": [
      { title: "用户名", dataIndex: "username_snapshot", width: 150 },
      { title: "是否成功", dataIndex: "success", dict: "LOGIN_SUCCESS", width: 100 },
      { title: "登录结果", dataIndex: "reason_code", dict: "LOGIN_REASON", width: 160 },
      { title: "IP", dataIndex: "ip_address", width: 150 },
      { title: "发生时间", dataIndex: "occurred_at", time: true, width: 180 },
      { title: "requestId", dataIndex: "request_id", width: 190 },
    ],
    "audit-access": [
      { title: "用户名", dataIndex: "username", width: 150 },
      { title: "方法", dataIndex: "http_method", width: 90 },
      { title: "接口", dataIndex: "endpoint", width: 280, ellipsis: true },
      { title: "结果", dataIndex: "result", dict: "ACCESS_RESULT", width: 100 },
      { title: "状态码", dataIndex: "response_status", width: 90 },
      { title: "耗时(ms)", dataIndex: "duration_ms", width: 100 },
      { title: "发生时间", dataIndex: "occurred_at", time: true, width: 180 },
      { title: "requestId", dataIndex: "request_id", width: 190 },
    ],
    exceptions: [
      { title: "异常类型", dataIndex: "exception_type", width: 260, ellipsis: true },
      { title: "接口", dataIndex: "endpoint", width: 220, ellipsis: true },
      { title: "消息", dataIndex: "message_summary", width: 280, ellipsis: true },
      { title: "次数", dataIndex: "occurrence_count", width: 80 },
      {
        title: "处理状态",
        dataIndex: "handle_status",
        dict: "EXCEPTION_STATUS",
        width: 110,
      },
      { title: "最近发生", dataIndex: "last_occurred_at", time: true, width: 180 },
      { title: "requestId", dataIndex: "request_id", width: 190 },
      { title: "操作", key: "action", width: 90 },
    ],
  };
  return map[section.value] || [];
});

const pagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}));
const tableWidth = computed(() =>
  columns.value.reduce((sum, column) => sum + Number(column.width || 140), 0),
);

const authorizationTree = computed(() => buildMenuTree(allMenus.value));
const selectedAuthorizationTree = computed(() =>
  filterSelectedTree(authorizationTree.value, new Set(checkedMenuIds.value)),
);

function rowKey(record: any) {
  return String(record.id || record.namespace || record.param_key || Math.random());
}

function buildMenuTree(source: any[]) {
  const map = new Map<string, any>();
  source.forEach((row) => map.set(String(row.id), { ...row, id: String(row.id), children: [] }));
  const roots: any[] = [];
  map.forEach((node) => {
    const parent = map.get(String(node.parent_id));
    if (parent && String(node.parent_id) !== "0") {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

function filterSelectedTree(nodes: any[], selectedIds: Set<string>): any[] {
  return nodes
    .map((node) => ({
      ...node,
      children: filterSelectedTree(node.children || [], selectedIds),
    }))
    .filter((node) => selectedIds.has(String(node.id)) || node.children.length > 0);
}

async function load() {
  if (section.value === "menus") {
    return;
  }
  loading.value = true;
  try {
    const params: Record<string, any> = { ...query };
    if (params.timeRange) {
      params.startTime = params.timeRange[0];
      params.endTime = params.timeRange[1];
      delete params.timeRange;
    }
    if (pagedSections.includes(section.value)) {
      params.page = currentPage.value;
      params.size = pageSize.value;
    }
    const data: any = await http.get(config.value.endpoint, { params });
    const source = pagedSections.includes(section.value) ? data.records : data;
    rows.value = applyClientFilters(source || []);
    total.value = pagedSections.includes(section.value) ? Number(data.total) : rows.value.length;
    if (["users", "roles"].includes(section.value)) {
      await loadRoleOptions();
    }
    if (section.value === "dictionaries") {
      await loadDictionaryTypes();
    }
  } finally {
    loading.value = false;
  }
}

function applyClientFilters(source: any[]) {
  if (section.value === "jobs") {
    const keyword = String(query.keyword || "").toLowerCase();
    return source.filter(
      (row) =>
        (!keyword || `${row.job_name} ${row.job_code}`.toLowerCase().includes(keyword)) &&
        (!query.status || row.status === query.status),
    );
  }
  if (section.value === "caches") {
    const keyword = String(query.keyword || "").toLowerCase();
    return source.filter((row) => !keyword || row.namespace.toLowerCase().includes(keyword));
  }
  return source;
}

function search() {
  currentPage.value = 1;
  load();
}

function resetQuery() {
  Object.keys(query).forEach((key) => delete query[key]);
  search();
}

function changeTable(pager: any) {
  currentPage.value = pager.current;
  pageSize.value = pager.pageSize;
  if (pagedSections.includes(section.value)) {
    load();
  }
}

async function loadRoleOptions() {
  const roles: any[] = await http.get("/roles");
  roleOptions.value = roles.map((role) => ({
    value: String(role.id),
    label: `${role.role_name}（${role.role_code}）`,
  }));
}

function openUserEditor() {
  Object.assign(userForm, { username: "", nickname: "", password: "", roleIds: [] });
  userEditorOpen.value = true;
}

async function createUser() {
  await http.post("/users", userForm);
  userEditorOpen.value = false;
  message.success("账号已创建");
  await load();
}

async function showUserRoles(record: any) {
  currentUserRoles.value = await http.get(`/users/${record.id}/roles`);
  userRolesOpen.value = true;
}

function changeUserStatus(record: any, status: string) {
  const label = status === "ENABLED" ? "启用" : "禁用";
  Modal.confirm({
    title: `确定${label}账号“${record.username}”？`,
    content: "状态变更后，该账号现有登录会话会立即失效。",
    onOk: async () => {
      await http.put(`/users/${record.id}/status`, { status });
      message.success(`账号已${label}`);
      await load();
    },
  });
}

function forceLogout(id: string) {
  Modal.confirm({
    title: "强制该账号下线？",
    onOk: async () => {
      await http.post(`/users/${id}/force-logout`);
      message.success("账号已强制下线");
    },
  });
}

function resetPassword(id: string) {
  let password = "";
  Modal.confirm({
    title: "重置密码",
    content: () =>
      h("input", {
        type: "password",
        class: "ant-input",
        placeholder: "请输入至少10位的新密码",
        onInput: (event: any) => (password = event.target.value),
      }),
    onOk: async () => {
      await http.post(`/users/${id}/reset-password`, { password });
      message.success("密码已重置");
    },
  });
}

function openRoleEditor(record?: any) {
  Object.assign(roleForm, {
    id: record ? String(record.id) : "",
    roleCode: record?.role_code || "",
    roleName: record?.role_name || "",
    status: record?.status || "ENABLED",
  });
  roleEditorOpen.value = true;
}

async function saveRole() {
  const payload = { ...roleForm };
  if (roleForm.id) {
    await http.put(`/roles/${roleForm.id}`, payload);
  } else {
    await http.post("/roles", payload);
  }
  roleEditorOpen.value = false;
  message.success("角色已保存");
  await load();
}

async function deleteRole(record: any) {
  await http.delete(`/roles/${record.id}`);
  message.success("角色已删除");
  await load();
}

async function openAuthorization(record: any) {
  authorizationRole.value = record;
  const [menus, checked] = (await Promise.all([
    http.get("/menus"),
    http.get(`/roles/${record.id}/menus`),
  ])) as unknown as [any[], Array<string | number>];
  allMenus.value = menus;
  checkedMenuIds.value = checked.map(String);
  authorizationOpen.value = true;
}

function checkMenus(keys: any) {
  checkedMenuIds.value = (Array.isArray(keys) ? keys : keys.checked).map(String);
}

async function saveAuthorization() {
  await http.put(`/roles/${authorizationRole.value.id}/menus`, {
    ids: checkedMenuIds.value,
  });
  authorizationOpen.value = false;
  message.success("角色授权已保存，相关账号需要重新登录");
}

async function loadDictionaryTypes() {
  dictionaryTypes.value = await http.get("/dictionary-types");
}

function openDictionaryItem(record?: any) {
  Object.assign(dictionaryItemForm, {
    id: record?.id ? String(record.id) : "",
    typeId: record?.type_id ? String(record.type_id) : undefined,
    itemCode: record?.item_code || "",
    itemValue: record?.item_value || "",
    itemLabel: record?.item_label || "",
    sortNo: Number(record?.sort_no || 0),
    status: record?.status || "ENABLED",
    displayStyle: record?.display_style || "default",
  });
  dictionaryItemOpen.value = true;
}

async function saveDictionaryItem() {
  if (dictionaryItemForm.id) {
    await http.put(`/dictionary-items/${dictionaryItemForm.id}`, dictionaryItemForm);
  } else {
    await http.post("/dictionary-items", dictionaryItemForm);
  }
  dictionaryItemOpen.value = false;
  message.success("字典项已保存");
  await load();
}

async function deleteDictionaryItem(record: any) {
  await http.delete(`/dictionary-items/${record.id}`);
  message.success("字典项已删除");
  await load();
}

async function openTypeManager() {
  await loadDictionaryTypes();
  typeManagerOpen.value = true;
}

function openDictionaryType(record?: any) {
  Object.assign(dictionaryTypeForm, {
    id: record ? String(record.id) : "",
    typeCode: record?.type_code || "",
    typeName: record?.type_name || "",
    description: record?.description || "",
    status: record?.status || "ENABLED",
  });
  dictionaryTypeOpen.value = true;
}

async function saveDictionaryType() {
  if (dictionaryTypeForm.id) {
    await http.put(`/dictionary-types/${dictionaryTypeForm.id}`, dictionaryTypeForm);
  } else {
    await http.post("/dictionary-types", dictionaryTypeForm);
  }
  dictionaryTypeOpen.value = false;
  message.success("字典类型已保存");
  await loadDictionaryTypes();
  await load();
}

async function deleteDictionaryType(record: any) {
  await http.delete(`/dictionary-types/${record.id}`);
  message.success("字典类型已删除");
  await loadDictionaryTypes();
  await load();
}

function openParameterEditor(record?: any) {
  Object.assign(parameterForm, {
    id: record ? String(record.id) : "",
    paramKey: record?.param_key || "",
    paramName: record?.param_name || "",
    paramType: record?.param_type || "STRING",
    paramValue: record?.param_value || "",
    defaultValue: record?.default_value || "",
    isSensitive: Number(record?.is_sensitive || 0) === 1,
    dynamicEffect: record ? Number(record.dynamic_effect) === 1 : true,
    description: record?.description || "",
  });
  parameterEditorOpen.value = true;
}

async function saveParameter() {
  const payload = {
    ...parameterForm,
    isSensitive: parameterForm.isSensitive ? 1 : 0,
    dynamicEffect: parameterForm.dynamicEffect ? 1 : 0,
  };
  if (parameterForm.id) {
    await http.put(`/system-parameters/${parameterForm.id}`, payload);
  } else {
    await http.post("/system-parameters", payload);
  }
  parameterEditorOpen.value = false;
  message.success("系统参数已保存");
  await load();
}

async function deleteParameter(record: any) {
  await http.delete(`/system-parameters/${record.id}`);
  message.success("系统参数已删除");
  await load();
}

function openJobEditor(record?: any) {
  Object.assign(jobForm, {
    id: record ? String(record.id) : "",
    jobCode: record?.job_code || "AUDIT_LOG_CLEANUP",
    jobName: record?.job_name || "",
    cronExpression: record?.cron_expression || "0 0 2 * * ?",
    timezone: record?.timezone || "Asia/Shanghai",
    status: record?.status || "PAUSED",
    description: record?.description || "",
  });
  jobEditorOpen.value = true;
}

async function saveJob() {
  if (jobForm.id) {
    await http.put(`/system-jobs/${jobForm.id}`, jobForm);
  } else {
    await http.post("/system-jobs", jobForm);
  }
  jobEditorOpen.value = false;
  message.success("调度任务已保存");
  await load();
}

async function deleteJob(record: any) {
  await http.delete(`/system-jobs/${record.id}`);
  message.success("调度任务已删除");
  await load();
}

async function trigger(id: string) {
  await http.post(`/system-jobs/${id}/trigger`);
  message.success("任务已触发");
  await load();
}

async function jobAction(id: string, action: string) {
  await http.post(`/system-jobs/${id}/${action}`);
  message.success(action === "pause" ? "任务已暂停" : "任务已恢复");
  await load();
}

async function clearCache(namespace: string) {
  const count: number = await http.post(`/caches/${namespace}/clear`);
  message.success(`已清理 ${count} 个缓存键`);
  await load();
}

function openCacheEditor(record?: any) {
  Object.assign(cacheForm, {
    id: record ? String(record.id) : "",
    namespace: record?.namespace || "",
    displayName: record?.display_name || "",
    description: record?.description || "",
    status: record?.status || "ENABLED",
  });
  cacheEditorOpen.value = true;
}

async function saveCache() {
  if (cacheForm.id) {
    await http.put(`/caches/${cacheForm.id}`, cacheForm);
  } else {
    await http.post("/caches", cacheForm);
  }
  cacheEditorOpen.value = false;
  message.success("缓存命名空间已保存");
  await load();
}

async function deleteCache(record: any) {
  await http.delete(`/caches/${record.id}`);
  message.success("缓存命名空间已删除");
  await load();
}

function handleException(record: any) {
  let remark = record.handle_remark || "";
  Modal.confirm({
    title: "处理异常日志",
    content: () =>
      h("textarea", {
        class: "ant-input",
        rows: 4,
        placeholder: "请输入处理说明",
        value: remark,
        onInput: (event: any) => (remark = event.target.value),
      }),
    onOk: async () => {
      await http.put(`/exception-logs/${record.id}/status`, {
        status: "HANDLED",
        remark,
      });
      message.success("异常已标记为处理完成");
      await load();
    },
  });
}

function dictLabel(typeCode: string, value: unknown) {
  return dictionaryLabel(dictionaries[typeCode] || [], value);
}

function dictColor(typeCode: string, value: unknown) {
  return dictionaryColor(dictionaries[typeCode] || [], value);
}

async function loadDictionaries() {
  const typeCodes = [
    "COMMON_STATUS",
    "LOGIN_SUCCESS",
    "LOGIN_REASON",
    "ACCESS_RESULT",
    "EXCEPTION_STATUS",
  ];
  await Promise.all(
    typeCodes.map(async (typeCode) => {
      dictionaries[typeCode] = await loadDictionary(typeCode);
    }),
  );
}

watch(section, () => {
  Object.keys(query).forEach((key) => delete query[key]);
  currentPage.value = 1;
  load();
});

onMounted(async () => {
  await loadDictionaries();
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

.authorization-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  min-height: 480px;
}

.authorization-grid section {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
}

.authorization-grid h3 {
  margin-top: 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.disabled {
  color: #bfbfbf !important;
  cursor: not-allowed;
}
</style>

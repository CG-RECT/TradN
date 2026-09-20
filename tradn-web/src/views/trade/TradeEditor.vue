<template>
  <EditorPageLayout
    v-if="trade"
    class="trade-page"
    :title="`${isReadOnly ? '查看' : isNew ? '新建' : '编辑'}开仓提示问卷 · ${trade.recordNo}`"
  >
    <template #header-extra>
      <a-tag v-if="!isNew" :color="dictionaryColor(statusOptions, trade.status)">
        {{ dictionaryLabel(statusOptions, trade.status) }}
      </a-tag>
    </template>

    <a-form layout="vertical">
      <div class="content-card">
        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="开仓方向" required>
              <a-radio-group v-model:value="form.direction" :disabled="isReadOnly">
                <a-radio-button value="LONG">多</a-radio-button>
                <a-radio-button value="SHORT">空</a-radio-button>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="计划止盈" required>
              <a-input-number
                v-model:value="form.plannedTakeProfitPrice"
                string-mode
                class="full-width"
                :disabled="isReadOnly"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="计划止损" required>
              <a-input-number
                v-model:value="form.plannedStopLossPrice"
                string-mode
                class="full-width"
                :disabled="isReadOnly"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="备注">
              <a-input v-model:value="form.remark" :disabled="isReadOnly" />
            </a-form-item>
          </a-col>
        </a-row>
      </div>

      <div class="content-card section">
        <h3>开仓前必问自己</h3>
        <div v-for="question in beforeQuestions" :key="question.id" class="question">
          <div class="question-title">
            {{ question.question_no }}、{{ question.title }}
            <span v-if="question.required_flag" class="danger-text">*</span>
          </div>
          <a-radio-group
            v-if="question.question_type === 'BOOLEAN'"
            v-model:value="answers[question.id]"
            :disabled="isReadOnly"
          >
            <a-radio :value="true">是</a-radio>
            <a-radio :value="false">否</a-radio>
          </a-radio-group>
          <a-textarea
            v-else
            v-model:value="answers[question.id]"
            :rows="3"
            :disabled="isReadOnly"
          />
        </div>
        <a-alert
          v-if="answers[1001] === true"
          type="warning"
          show-icon
          message="你标记了这是一笔随手单，请重新确认交易依据和风险。"
        />
      </div>

      <div v-if="!isNew" class="content-card section">
        <h3>开仓后复盘</h3>
        <a-alert
          v-if="trade.status === 'PLANNED'"
          type="info"
          message="当前仍是计划状态，可以提前记录或后续补充复盘。"
          class="review-alert"
        />
        <div v-for="question in afterQuestions" :key="question.id" class="question">
          <div class="question-title">
            {{ question.question_no }}、{{ question.title }}
          </div>
          <a-textarea
            v-model:value="answers[question.id]"
            :rows="4"
            :disabled="isReadOnly"
          />
        </div>
      </div>

      <div v-if="showPosition" class="content-card section">
        <h3>成交信息</h3>
        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="实际开仓价">
              <a-input-number
                v-model:value="form.openPrice"
                string-mode
                class="full-width"
                :disabled="isReadOnly"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="手数">
              <a-input-number
                v-model:value="form.lotSize"
                string-mode
                class="full-width"
                :disabled="isReadOnly"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="开仓时间">
              <a-date-picker
                v-model:value="form.openTime"
                show-time
                class="full-width"
                :disabled="isReadOnly"
              />
            </a-form-item>
          </a-col>
          <template v-if="trade.status === 'OPEN' || trade.status === 'CLOSED'">
            <a-col :span="6">
              <a-form-item label="实际平仓价">
                <a-input-number
                  v-model:value="form.closePrice"
                  string-mode
                  class="full-width"
                  :disabled="isReadOnly"
                />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="盈亏（元）">
                <a-input-number
                  v-model:value="form.profitLoss"
                  string-mode
                  class="full-width"
                  :disabled="isReadOnly"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="平仓时间">
                <a-date-picker
                  v-model:value="form.closeTime"
                  show-time
                  class="full-width"
                  :disabled="isReadOnly"
                />
              </a-form-item>
            </a-col>
          </template>
        </a-row>
      </div>
    </a-form>

    <template #actions>
      <a-button @click="back">关闭</a-button>
      <a-button v-if="isReadOnly" type="primary" @click="switchToEdit">进入编辑</a-button>
      <template v-else>
        <a-button @click="back">取消</a-button>
        <a-button type="primary" @click="savePlan">
          {{ isNew ? "保存并创建" : "保存开仓前问卷" }}
        </a-button>
        <a-button v-if="!isNew" @click="saveReview">保存开仓后复盘</a-button>
        <a-button v-if="!isNew && trade.status === 'PLANNED'" type="primary" @click="markOpen">标记已开仓</a-button>
        <a-button v-if="!isNew && trade.status === 'OPEN'" type="primary" danger @click="markClose">标记已平仓</a-button>
      </template>
    </template>
  </EditorPageLayout>
  <a-spin v-else class="loading" />
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import dayjs from "dayjs";
import { useRoute, useRouter } from "vue-router";
import http from "../../api/http";
import {
  dictionaryColor,
  dictionaryLabel,
  loadDictionary,
} from "../../api/dictionary";
import EditorPageLayout from "../../components/editor/EditorPageLayout.vue";

const route = useRoute();
const router = useRouter();
const isNew = computed(() => route.params.id === "new");
const isReadOnly = computed(() => route.query.mode === "view");
const trade = ref<any>();
const questions = ref<any[]>([]);
const statusOptions = ref<any[]>([]);
const answers = reactive<Record<string, any>>({});
const form = reactive<any>({});

const beforeQuestions = computed(() =>
  questions.value.filter((question) => question.phase === "BEFORE_OPEN"),
);
const afterQuestions = computed(() =>
  questions.value.filter((question) => question.phase === "AFTER_OPEN"),
);
const showPosition = computed(
  () =>
    !isNew.value &&
    ["PLANNED", "OPEN", "CLOSED"].includes(trade.value?.status),
);

async function load() {
  if (isNew.value) {
    const data: any = await http.get("/trades/questionnaire-template");
    trade.value = { recordNo: "尚未保存", status: "NEW", version: 0 };
    questions.value = data.questions;
    return;
  }
  const data: any = await http.get(`/trades/${route.params.id}`);
  trade.value = data.trade;
  questions.value = data.questions;
  Object.assign(form, data.trade, {
    openTime: data.trade.openTime ? dayjs(data.trade.openTime) : null,
    closeTime: data.trade.closeTime ? dayjs(data.trade.closeTime) : null,
  });
  data.answers.forEach((answer: any) => {
    answers[answer.question_id] =
      answer.boolean_answer !== null && answer.boolean_answer !== undefined
        ? Boolean(answer.boolean_answer)
        : (answer.number_answer ?? answer.text_answer ?? answer.option_answer);
  });
}

// version 随已有记录的写请求提交，后端用乐观锁阻止多个页面静默互相覆盖。
function payload() {
  return {
    ...form,
    version: trade.value.version,
    openTime: form.openTime?.format("YYYY-MM-DDTHH:mm:ss"),
    closeTime: form.closeTime?.format("YYYY-MM-DDTHH:mm:ss"),
    answers,
  };
}

async function savePlan() {
  if (isNew.value) {
    const created: any = await http.post("/trades", payload());
    message.success("开仓问卷已创建");
    await router.replace({ path: `/trades/${created.id}`, query: { mode: "edit" } });
    await load();
    return;
  }
  await http.put(`/trades/${trade.value.id}/pre-open-questionnaire`, payload());
  message.success("开仓前问卷已保存");
  await load();
}

async function saveReview() {
  await http.put(`/trades/${trade.value.id}/post-open-review`, payload());
  message.success("复盘已保存");
  await load();
}

async function markOpen() {
  await http.put(`/trades/${trade.value.id}/position`, payload());
  message.success("已进入持仓状态");
  await load();
}

async function markClose() {
  await http.put(`/trades/${trade.value.id}/close`, payload());
  message.success("平仓信息已保存");
  await load();
}

function back() {
  router.push("/trades");
}

function switchToEdit() {
  router.replace({ path: route.path, query: { mode: "edit" } });
}

onMounted(async () => {
  statusOptions.value = await loadDictionary("TRADE_STATUS");
  await load();
});
</script>

<style scoped>
.section {
  margin-top: 18px;
}

.question {
  margin: 18px 0;
}

.question-title {
  font-weight: 500;
  margin-bottom: 8px;
}

.section-actions {
  margin-top: 18px;
}

.review-alert {
  margin-bottom: 16px;
}

.loading {
  display: block;
  margin: 100px auto;
}
</style>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">盈亏统计</div>
        <div class="muted">默认按照平仓日期统计已实现盈亏</div>
      </div>
      <a-range-picker v-model:value="range" @change="load" />
    </div>
    <a-row :gutter="16"
      ><a-col :span="6" v-for="item in cards" :key="item.label"
        ><a-card
          ><a-statistic
            :title="item.label"
            :value="item.value"
            :precision="item.precision"
            :suffix="item.suffix" /></a-card></a-col></a-row
    ><a-alert
      style="margin-top: 18px"
      type="info"
      message="未平仓记录不进入已实现盈亏；盈亏以用户录入值为准。"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import dayjs from "dayjs";
import http from "../api/http";
const range = ref<any>([dayjs().startOf("month"), dayjs()]),
  data = ref<any>({});
const cards = computed(() => [
  {
    label: "总盈亏（元）",
    value: Number(data.value.total_profit_loss || 0),
    precision: 2,
  },
  { label: "盈利笔数", value: Number(data.value.win_count || 0) },
  { label: "亏损笔数", value: Number(data.value.loss_count || 0) },
  {
    label: "胜率",
    value: Number(data.value.winRate || 0),
    precision: 2,
    suffix: "%",
  },
]);
async function load() {
  data.value = await http.get("/trades/statistics/profit-loss", {
    params: {
      from: range.value[0].format("YYYY-MM-DD"),
      to: range.value[1].format("YYYY-MM-DD"),
    },
  });
}
onMounted(load);
</script>

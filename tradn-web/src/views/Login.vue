<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="brand">TradN</div>
      <div class="subtitle">黄金交易记录与复盘系统</div>
      <a-alert
        v-if="route.query.reason === 'expired'"
        type="warning"
        show-icon
        message="登录已失效，请重新登录"
        description="登录可能已超时，或会话已被清理。重新登录后将返回之前的页面。"
        class="session-notice"
      />
      <a-form :model="form" layout="vertical" @finish="submit">
        <a-form-item
          label="用户名"
          name="username"
          :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input
            v-model:value="form.username"
            size="large"
            autocomplete="username"
          />
        </a-form-item>

        <a-form-item
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password
            v-model:value="form.password"
            size="large"
            autocomplete="current-password"
          />
        </a-form-item>

        <a-button
          type="primary"
          html-type="submit"
          size="large"
          block
          :loading="loading"
        >
          登录
        </a-button>
      </a-form>
    </div>
  </div>
</template>
<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
import { safeReturnPath } from "../utils/session";
const form = reactive({ username: "", password: "" });
const loading = ref(false);
const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
async function submit() {
  loading.value = true;
  try {
    await auth.login(form.username, form.password);
    await auth.load();
    router.replace(safeReturnPath(route.query.redirect));
  } catch {
    // 请求拦截器已经显示失败原因，留在登录页允许用户重试，避免页面抛出未处理异常。
  } finally {
    loading.value = false;
  }
}
</script>
<style scoped>
.session-notice {
  margin-bottom: 18px;
}

.login-page {
  height: 100%;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 20% 20%, #fff7d6 0, transparent 38%),
    linear-gradient(135deg, #16120b, #3a2d12);
}
.login-panel {
  width: 380px;
  background: #fff;
  padding: 38px;
  border-radius: 14px;
  box-shadow: 0 24px 70px #0008;
}
.brand {
  font-size: 34px;
  font-weight: 700;
  color: #8a6417;
}
.subtitle {
  color: #7b7b7b;
  margin: 6px 0 28px;
}
</style>

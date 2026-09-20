import { createApp } from "vue";
import { createPinia } from "pinia";
import Antd from "ant-design-vue";
import "ant-design-vue/dist/reset.css";
import "md-editor-v3/lib/style.css";
import App from "./App.vue";
import router from "./router";
import "./styles.css";
createApp(App).use(createPinia()).use(router).use(Antd).mount("#app");

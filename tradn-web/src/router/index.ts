import { createRouter, createWebHistory } from "vue-router";
import Login from "../views/Login.vue";
import AppLayout from "../layouts/AppLayout.vue";
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: Login },
    {
      path: "/",
      component: AppLayout,
      redirect: "/trades",
      children: [
        {
          path: "trades",
          component: () => import("../views/trade/TradeList.vue"),
          meta: { permission: "trade:record:list" },
        },
        {
          path: "trades/:id",
          component: () => import("../views/trade/TradeEditor.vue"),
          meta: { permission: "trade:record:list" },
        },
        {
          path: "notes",
          component: () => import("../views/note/NoteList.vue"),
          meta: { permission: "note:note:list" },
        },
        {
          path: "notes/:id",
          component: () => import("../views/note/NoteEditor.vue"),
          meta: { permission: "note:note:list" },
        },
        {
          path: "timeline",
          component: () => import("../views/timeline/TimelineView.vue"),
          meta: { permission: "timeline:daily:view" },
        },
        {
          path: "statistics",
          component: () => import("../views/Statistics.vue"),
          meta: { permission: "trade:statistics:view" },
        },
        {
          path: "system/:section",
          component: () => import("../views/system/SystemPage.vue"),
          meta: { permission: "system:settings:view" },
        },
      ],
    },
  ],
});
// 路由元信息声明页面所需权限，菜单使用同一权限码；后端仍会对每个 API 再次鉴权。
router.beforeEach((to) => {
  if (to.path != "/login" && !localStorage.getItem("tradn-token"))
    return "/login";
  if (to.path === "/login" && localStorage.getItem("tradn-token")) return "/";
});
export default router;

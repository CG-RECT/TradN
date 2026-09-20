import { defineStore } from "pinia";
import http from "../api/http";

export interface Profile {
  id: string;
  username: string;
  nickname: string;
  permissions: string[];
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    // profile/menus 每次刷新从后端重新加载，LocalStorage 只保存服务端可撤销的 JWT。
    profile: null as Profile | null,
    menus: [] as unknown[],
  }),
  getters: {
    // can 只控制前端可见性，后端 @PreAuthorize 仍是最终权限边界。
    can: (state) => (permission: string) =>
      !!state.profile?.permissions?.includes(permission),
  },
  actions: {
    async login(username: string, password: string) {
      const data = (await http.post("/auth/login", { username, password })) as {
        token: string;
        profile: Profile;
      };
      localStorage.setItem("tradn-token", data.token);
      this.profile = data.profile;
    },
    async load() {
      if (!localStorage.getItem("tradn-token")) return;
      this.profile = (await http.get("/auth/profile")) as Profile;
      this.menus = (await http.get("/auth/menus")) as unknown[];
    },
    async logout() {
      try {
        await http.post("/auth/logout");
      } finally {
        localStorage.removeItem("tradn-token");
        this.profile = null;
        this.menus = [];
      }
    },
  },
});

# TradN

黄金交易计划、复盘、Markdown 笔记和每日时间线管理系统。

## 目录

- `tradn-backend`：Java 8 + Spring Boot 2.7 + Spring Security + MyBatis-Plus。
- `tradn-web`：Vue 3 + TypeScript + Vite + Ant Design Vue。
- `docs`：业务与数据库真相文档。
- `deploy`：Docker、Windows 和 Linux 部署文件。

## 本地启动

1. 复制 `.env.example` 为 `.env` 并修改密码。
2. 执行 `docker compose --env-file .env -f deploy/docker/compose.yml up -d --build`。
3. 浏览器访问 `http://localhost`。

非 Docker 部署请参阅 `deploy/README.md`。

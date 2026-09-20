# 部署说明

## Docker Compose（推荐）

复制根目录 `.env.example` 为 `.env`，修改全部密码后运行：

```bash
docker compose --env-file .env -f deploy/docker/compose.yml up -d --build
```

MySQL、Redis、MinIO 使用独立持久化卷。正式升级前先执行 MySQL 与 MinIO 备份。

## 非 Docker

- Windows：以管理员 PowerShell 运行 `deploy/windows/deploy.ps1`。
- Linux：以具有 sudo 权限的账号运行 `deploy/linux/deploy.sh`。

非 Docker 脚本默认 MySQL 8、Redis 7 和 MinIO 已安装且可连接；脚本负责校验、构建和部署应用，不会静默安装数据库或修改防火墙。

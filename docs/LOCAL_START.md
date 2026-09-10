# 本地一键启动与登录失效处理

## 适合当前电脑的运行方式

MySQL、Redis 和 MinIO 继续使用现有 Docker 容器；后端运行打包后的 JAR，前端运行 Vite 本地服务。日常打开系统无需启动 IDEA；需要断点调试时才使用 IDEA。

项目现有 `deploy/windows/deploy.ps1` 是部署安装脚本，还需要手动配置 Nginx，不适合替代本地日常启动。全容器 Compose 适合后续服务器部署，但其默认配置会新建数据库持久化卷，不能直接当作当前已有数据库的启动快捷方式。

## 日常操作

1. 第一次切换时，手动停止 IDEA 启动的后端和终端启动的前端，避免旧服务占用端口。
2. 双击项目根目录 `start-local.cmd`。
3. 脚本检查工具、启动 Docker Desktop（如有需要），启动指定的已有依赖容器，构建并启动后端，启动前端，等待就绪后打开浏览器。
4. 用完后双击 `stop-local.cmd`，只停止由启动脚本启动的前后端。共享的 Docker 容器保持运行。

项目根目录必须有已配置好的 `.env`，用于数据库、Redis、MinIO、JWT 等连接配置。脚本不会输出这些密钥，也不会重建数据库或删除容器卷。首次后端启动仍会照常执行尚未应用的 Flyway 迁移。

默认使用当前电脑已经确认的容器名：`mysql8.0`、`redis01`、`my_minio`。其他电脑应通过 `-Containers` 指定实际容器名。

## 可选参数

在项目根目录 PowerShell 执行：

```powershell
# 仅检查配置和服务状态，不启动、不停止、不构建。
.\start-local.cmd -CheckOnly

# 使用已有后端 JAR 加速启动；修改后端代码后应去掉此参数重新构建。
.\start-local.cmd -SkipBuild

# 启动但不自动打开浏览器。
.\start-local.cmd -NoBrowser

# 数据库等依赖已安装为系统服务时，跳过 Docker 操作。
& .\deploy\windows\start-local.ps1 -Containers @()

# 其他电脑配置已有容器名称。
& .\deploy\windows\start-local.ps1 -Containers @('my-mysql', 'my-redis', 'my-minio')
```

后端默认 8080（可由 `.env` 的 `APP_PORT` 或 `-BackendPort` 覆盖），前端默认 3000（可由 `-WebPort` 覆盖）。支持 `-JavaHome` 指定 JDK；未指定时优先识别有效 JAVA_HOME，再从 javac.exe 定位 JDK，避免误用 JRE。

`.cmd` 仅对本次 PowerShell 进程设置脚本执行策略，不修改电脑的全局执行策略。前端依赖存在时直接复用，不反复安装；后端默认每次新启动前构建。已经就绪的服务会被复用，不会替换 IDEA 运行的进程；后端代码更新后需手动停止旧实例，再启动新实例。

这两个 Windows PowerShell 脚本使用带 BOM 的 UTF-8，兼容 Windows PowerShell 5.1 对中文注释的读取；Java 和 Vue 源码继续使用无 BOM 的 UTF-8。

## 日志和停止范围

日志与进程记录统一在项目 `deploy/runtime/local/` 下，Git 已忽略此目录：

- `backend-build.log`：后端构建结果。
- `backend.log`、`backend-error.log`：后端运行输出。
- `frontend-install.log`：首次前端依赖安装输出。
- `frontend.log`、`frontend-error.log`：前端运行输出。
- `backend.json`、`frontend.json`：脚本所启动进程的 PID、启动时间和入口路径。

停止脚本核对进程启动时间和完整入口路径，避免 PID 被重用时误关其他程序。它不停止 IDEA 的后端、手动启动的前端及数据库容器。启动部分失败时查阅对应日志，修复后重试，或用停止脚本关闭本脚本已启动的部分服务。

可用 `-Instance verification -BackendPort 8081 -WebPort 3001 -NoBrowser` 在其他端口验证脚本。对应日志在 `deploy/runtime/verification/`；停止时执行 `deploy/windows/stop-local.ps1 -Instance verification`。不应在服务器上把 Vite 开发服务作为生产部署方案。

## 登录失效后的行为

登录依旧有有效期，不通过延长或取消有效期解决问题。令牌过期、Redis 会话清理、强制下线等情况发生后，API 返回 401，页面自动回到登录页并说明原因；重新登录后恢复原页面。尚未保存的表单内容不会自动保存。

缺少角色权限返回 403 并提示联系管理员授权，不会把仍有效的登录态清掉。旧服务重启之前仍可能使用原来的 403 返回规则，因此部署本次修复后需要重启后端并刷新前端。

后端验证命令：`mvn test`。前端验证命令：`node --test tests/session.test.mjs`、`npm.cmd run typecheck`、`npm.cmd run build`。

## 本次验证记录（2026-09-10）

- 后端 14 项测试通过，包含 8 项安全过滤链回归：缺失/过期/损坏令牌、撤销会话、禁用账号、权限不足、授权成功以及登录入口可访问。
- 前端 4 项登录处理测试通过，TypeScript 检查及生产构建通过。
- 使用 8081/3001 独立端口完成实际启动、前端代理返回 401、停止、再次启动和最终停止验证。
- 测试实例已经关闭，数据库容器和原有服务未由测试停止。
- Docker Desktop 在测试时已运行；其冷启动分支未通过关闭用户的 Docker Desktop 实测。

# AI未来实践中心 - 项目驱动教学实验平台(IOEDU-New)

前后端一体的实验室设备借阅与项目驱动教学平台,复刻参考站学生端功能并新增管理员端。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot 2.6.13 + Java 8 + MySQL 8 + Spring Data JPA + JWT |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router 4 + ECharts |
| 小程序端 | uni-app (Vue 3 + Vite) 微信小程序,学生/教师/管理员三端,复用同一套后端接口 |

## 功能

- **学生端** `/app`:个人中心(实践统计/学习趋势/成就)、项目中心(浏览/报名/收藏/进度,详情支持富文本图文视频描述)、成果提交(整体或分阶段考核项提交/截图上传/查看评分评语/加权综合分)、设备图书馆(筛选/详情/三步借阅申请/心愿单跨端同步)、借阅管理(撤销/归还/到期前3天可续借一次)、技能评估(雷达图/测评/学习建议)、站内通知(含到期归还提醒)
- **教师端** `/teacher`:教学工作台(名下项目统计)、教学资源管理(真实附件上传,学生端可直接下载)、更换项目封面、学生报名进度查看
- **管理员端** `/admin`:数据看板(趋势图/设备利用率)、设备管理 CRUD(富文本描述/文档附件上传/CSV相关)、借阅审批(批准/拒绝/归还验收,联动库存)、项目管理 CRUD(指派讲师/富文本描述图片视频/成果考核项权重设置/BOM CSV导入)、报名进度管理、成果评审(支持分阶段考核项与AI预评审)、通知管理(群发/删除)、讨论管理(删帖级联回复)、用户管理(新建/CSV批量导入/重置密码/禁用/删除/三级角色)、站点设置(标题/LOGO/底部信息/注册开关/分页/分类)、AI 设置
- **落地页** `/` 与登录注册 `/auth`

## AI 功能(网页端与小程序端同步支持)

- **AI 学习规划师**(技能评估页):基于六维技能画像与项目库,后端先确定性计算项目匹配分,大模型在候选内生成学习画像总结、重点提升维度与「基础补强 → 综合实践 → 挑战提升」三阶段项目路线;AI 不可用时自动降级为智能匹配结果
- **AI 成果预评审**(管理端评分弹窗):AI 阅读学生成果说明,给出建议分、亮点/不足与评语草稿,自动填入评分表单,最终由教师确认修改
- **稳定性**:服务端代理调用(密钥不出后端)、结果结构校验与项目 ID 白名单、每用户限流(3次/小时)、10 分钟缓存、超时+熔断+规则降级

### AI 助手与 SKILL(`/app/ai`)

- **SKILL**:一段可复用的 AI 能力 = 系统提示词 + 输入结构(JSON Schema,可选表单)+ 工具白名单 + 模型参数,定义为一份 spec JSON。内置 SKILL 在 `src/main/resources/skills/*.json`(学习助手、设备借用顾问、项目推荐官、BOM 审查员),用户可「另存为」后自定义;教师/管理员可创建本站共享 SKILL,管理员可把个人 SKILL 提升为本站共享。
- **工具(Tool)**:模型可调用的平台能力,定义形状 `{name, description, inputSchema}` 与 MCP Tool / OpenAI function 一致(`ai/tool/AiTool.java`,实现类注册为 bean 即自动进入 `ToolRegistry`)。内置只读工具:`equipment.search/get`、`project.search/get`、`borrow.my_list`、`skill.my_scores`;写操作 `borrow.apply` 执行前必须经用户确认。管理员在「AI 中心 → 工具策略」可关闭工具、限制角色、强制确认、设每日次数。
- **对话**:`POST /api/ai/chat/stream` 为 SSE 流式(事件 delta / tool_call / tool_result / confirm_required / done / error),`POST /api/ai/chat` 为非流式;会话历史、运行审计(`ai_runs` / `ai_tool_invocations`)与每日用量(`IOEDU_AI_DAILY_RUNS`,默认 50 次/人/天)均入库。
- **为 Agent 预留**:`LlmGateway` 抽象了 OpenAI 兼容协议(含 tools 与 stream),日后升级 Boot 3 可换 Spring AI 实现;SKILL 的 inputSchema 即工具参数定义,可直接被 Agent 当工具编排;工具定义按 MCP 形状设计,加一个 MCP Server 端点即可对外暴露。
- 本地联调:`node scripts/mock-ai-server.mjs` 已支持流式与工具调用,后端设 `IOEDU_AI_BASE_URL=http://localhost:9281 IOEDU_AI_API_KEY=mock-key`。

### 配置(不配则 AI 自动降级,平台其余功能不受影响)

**推荐方式:管理后台在线配置(免重启)** —— 管理员登录 → 「AI 设置」页,填接口地址/模型/API Key,可调输出 Token 上限、温度、超时,支持 DeepSeek/通义千问一键预设与**连接测试**,保存后立即生效。配置存数据库,优先级高于环境变量。

环境变量方式(作为默认值,适合初始部署):

```bash
# 本地开发:设置环境变量后启动后端
IOEDU_AI_API_KEY=sk-xxx                        # DeepSeek 或通义千问的 API Key
IOEDU_AI_BASE_URL=https://api.deepseek.com     # 选填,默认 DeepSeek
IOEDU_AI_MODEL=deepseek-chat                   # 选填,默认 deepseek-chat

# Docker 部署:项目根目录 .env 文件写 AI_API_KEY=sk-xxx(可选 AI_BASE_URL / AI_MODEL)
```

通义千问填法:`https://dashscope.aliyuncs.com/compatible-mode` + `qwen-plus`。
本地联调可不买 Key:先 `node scripts/mock-ai-server.mjs` 起模拟模型,再在 AI 设置页填 `http://localhost:9281` + 任意 Key。

## 多租户(一套后端服务多个客户)

后端为单进程多租户:每个客户一个独立 MySQL 库 `ioedu_<编码>`,按请求 Host 的子域名(`c001.根域`)切库,数据、登录令牌、上传文件三层隔离;老部署的 `ioedu` 库自动成为默认租户。开通客户通过平台接口 `POST /api/platform/tenants`(静态令牌 `IOEDU_PLATFORM_TOKEN` 鉴权),本地联调可用 `X-Tenant-Id` 请求头切换租户,`smoke-test-tenant.ps1` 覆盖开通、隔离、停用、注销全流程。详见《部署指南.md》5.2 节。

## 项目商店(跨客户共享项目)

`hub/` 是独立的商店服务(Spring Boot 2.6 / Java 8,库 `ioedu_hub`,端口 8081)。客户站点管理员在「管理后台 → 项目商店」把本地项目发布到商店或安装商店里的项目(附件随之搬运、本地记住上游版本以便更新);平台管理员在 `/platform/login` 登录商店后台审核上架、设置可见范围与定向分享、为客户签发 API Key。本地开发:`cd hub && mvn spring-boot:run -Dspring-boot.run.profiles=dev`(平台管理员 `platform / platform123`),主后端 dev profile 已默认指向 `http://localhost:8081`。详见《部署指南.md》5.3 节。

## 快速启动

### 1. 数据库

本地安装 MySQL 8,确认 root 密码。默认配置连接 `localhost:3306`,首次启动自动建库 `ioedu`,由 Flyway 执行 `src/main/resources/db/migration/` 下的脚本建表(Hibernate 只做校验,不再自动改表;以后改表结构请新增 `V{n}__xxx.sql`)。

数据库密码默认 `123456789`,可用环境变量 `IOEDU_DB_PASSWORD` 覆盖(也可覆盖 `IOEDU_DB_URL` / `IOEDU_DB_USERNAME`)。

### 2. 后端(端口 8080)

本地开发请激活 **dev profile**:它提供内置开发 JWT 密钥、写入演示数据(10 个项目 + 12 台设备 + 演示账号)并把管理员设为 `admin@ioedu.cn / admin123`,`smoke-test.ps1` 依赖这些数据。

```bash
start-backend.bat
# 等价于 mvn spring-boot:run -Dspring-boot.run.profiles=dev
# IDEA 里运行 IoeduNewApplication 时在 Run Configuration 加 --spring.profiles.active=dev
```

不带 profile 启动就是生产模式:必须设置 `IOEDU_JWT_SECRET`(≥32 字符),不写演示数据,首个管理员密码来自 `IOEDU_ADMIN_PASSWORD`(留空则随机生成并打印到日志)。

### 3. 前端(端口 5173)

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173 ,开发代理已把 `/api` 转发到 8080。

### 4. 微信小程序端(可选)

```bash
cd miniprogram
npm install
npm run dev:mp-weixin
```

微信开发者工具导入 `miniprogram/dist/dev/mp-weixin`,本地设置勾选「不校验合法域名」即可联调,详见 `miniprogram/README.md`。

## Docker 一键部署(生产)

服务器装好 Docker 后,在项目根目录执行:

```bash
docker compose up -d --build
```

将启动 3 个容器:`ioedu-mysql`(数据卷持久化)、`ioedu-backend`(8080,仅内网)、`ioedu-frontend`(Nginx,对外 8093 端口,反代 /api)。浏览器访问 http://服务器IP:8093 即可。

- 首次部署先 `cp .env.example .env` 并填写:`JWT_SECRET`(必填,≥32 随机字符)、`DB_PASSWORD`、`ADMIN_PASSWORD`(可留空随机生成并打印到日志)等,详见《部署指南.md》
- 更新发版:`git pull && docker compose up -d --build`
- 查看日志:`docker compose logs -f backend`
- 国内服务器 npm 下载慢:取消 `frontend/Dockerfile` 中 npmmirror 注释

## 演示账号(仅 dev profile / `IOEDU_SEED_DEMO=true` 时存在)

| 角色 | 邮箱 | 密码 |
|---|---|---|
| 管理员 | admin@ioedu.cn | admin123(dev profile 默认;生产由 `IOEDU_ADMIN_PASSWORD` 指定或随机生成) |
| 学生 | zhang@stu.ioedu.cn | 123456 |
| 教师(陈老师) | chen@ioedu.cn | 123456 |
| 教师(李老师/王老师/赵老师) | li@ioedu.cn / wang@ioedu.cn / zhao@ioedu.cn | 123456 |

## 目录结构

```
IOEDU-New/
├── pom.xml
├── src/main/java/com/example/ioedunew/
│   ├── common/       # 统一响应、异常处理
│   ├── config/       # JWT、认证拦截器、CORS
│   ├── controller/   # 学生端 + 管理端 REST 接口
│   ├── dto/          # 请求/响应模型
│   ├── entity/       # JPA 实体(8 张表)
│   ├── repository/   # 数据仓库
│   ├── service/      # 业务服务(借阅状态机、库存控制等)
│   └── init/         # 启动种子数据
├── src/main/resources/
│   ├── application.properties
│   └── seed/         # 项目与设备种子 JSON
├── frontend/         # Vue 3 前端(学生端 + 管理端)
├── miniprogram/      # uni-app 微信小程序端(学生/教师/管理员)
└── IoeduFront/       # 旧 Vue2 脚手架(已废弃,可删除)
```

## 借阅状态机

```
PENDING(审批中) ─ 批准 → APPROVED(借用中,扣库存) ─ 学生申请归还 → RETURN_REQUESTED ─ 管理员验收 → RETURNED(回补库存)
      │                                     
      ├─ 拒绝 → REJECTED
      └─ 学生撤销 → CANCELLED
```

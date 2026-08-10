# AI未来实践中心 - 项目驱动教学实验平台(IOEDU-New)

前后端一体的实验室设备借阅与项目驱动教学平台,复刻参考站学生端功能并新增管理员端。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot 2.6.13 + Java 8 + MySQL 8 + Spring Data JPA + JWT |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router 4 + ECharts |

## 功能

- **学生端** `/app`:个人中心(实践统计/学习趋势/成就)、项目中心(浏览/报名/收藏/进度)、设备图书馆(筛选/详情/三步借阅申请)、借阅管理(撤销/归还)、技能评估(雷达图/测评/学习建议)、站内通知
- **教师端** `/teacher`:教学工作台(名下项目统计)、教学资源管理(真实附件上传,学生端可直接下载)、更换项目封面、学生报名进度查看
- **管理员端** `/admin`:数据看板(趋势图/设备利用率)、设备管理 CRUD、借阅审批(批准/拒绝/归还验收,联动库存)、项目管理 CRUD(指派讲师)、用户管理(禁用/三级角色)
- **落地页** `/` 与登录注册 `/auth`

## 快速启动

### 1. 数据库

本地安装 MySQL 8,确认 root 密码。默认配置连接 `localhost:3306`,首次启动自动建库 `ioedu` 并写入种子数据(10 个项目 + 12 台设备 + 演示账号)。已初始化过的老库再次启动时会自动回填新增字段(封面图、Fork 数、PCB 尺寸、分类对齐参考站),并补插种子中新增的设备。

密码配置二选一:

- 修改 `src/main/resources/application.properties` 中 `spring.datasource.password`
- 或设置环境变量 `IOEDU_DB_PASSWORD`

### 2. 后端(端口 8080)

```bash
mvn spring-boot:run
# 或在 IDEA 中直接运行 IoeduNewApplication
```

### 3. 前端(端口 5173)

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173 ,开发代理已把 `/api` 转发到 8080。

## Docker 一键部署(生产)

服务器装好 Docker 后,在项目根目录执行:

```bash
docker compose up -d --build
```

将启动 3 个容器:`ioedu-mysql`(数据卷持久化)、`ioedu-backend`(8080,仅内网)、`ioedu-frontend`(Nginx,对外 8093 端口,反代 /api)。浏览器访问 http://服务器IP:8093 即可。

- 修改数据库密码:项目根目录建 `.env` 文件写 `DB_PASSWORD=你的密码`(默认 123456789)
- 更新发版:`git pull && docker compose up -d --build`
- 查看日志:`docker compose logs -f backend`
- 国内服务器 npm 下载慢:取消 `frontend/Dockerfile` 中 npmmirror 注释

## 演示账号

| 角色 | 邮箱 | 密码 |
|---|---|---|
| 管理员 | admin@ioedu.cn | admin123 |
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
└── IoeduFront/       # 旧 Vue2 脚手架(已废弃,可删除)
```

## 借阅状态机

```
PENDING(审批中) ─ 批准 → APPROVED(借用中,扣库存) ─ 学生申请归还 → RETURN_REQUESTED ─ 管理员验收 → RETURNED(回补库存)
      │                                     
      ├─ 拒绝 → REJECTED
      └─ 学生撤销 → CANCELLED
```

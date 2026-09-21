# OSS 文件存储升级

## 本次范围

头像、项目封面、设备图片、富文本图片、教学资料、学生成果附件，以及项目商店附件，都已接入阿里云 OSS。采用平台统一配置；商户不用各自填写阿里云密钥。

- 主系统：`ioedu/tenants/{商户编码}/{年月}/{随机文件名}`。
- 公共项目商店：`ioedu/hub/{哈希前两位}/{文件哈希}.{扩展名}`，保持原有按内容去重。
- 数据库仍保存稳定的 `/uploads/...`、`/hub-assets/...` 链接，不保存会过期的签名 URL。
- OSS 文件位置和大小写入各商户自己的 `stored_files` 表；商店位置写入 `hub_assets`。
- 本次不改变 KiCad 编辑器的运行工作区。KiCad CLI 需要文件系统操作，其工程工作目录、数据库和日志仍按原部署保留。

## 上传和访问

启用 OSS 后，不把新文件永久保存在服务器上传目录。默认由原上传接口校验登录、大小和商户容量，再写入 OSS；配置跨域并启用 `OSS_DIRECT_UPLOAD=true` 后，网页和小程序先向后端申请仅用于单个文件的 V4 POST 凭证，直接把文件发送给 OSS，再调用后端确认。直传内容不经过业务服务器。

直传凭证约束租户目录、文件大小、私有权限和有效期。后端确认所有者及 OSS 元数据后，在 OSS 内部复制为正式对象；重复确认不会重复生成业务记录。配额通过数据库行锁和待上传预留量约束。每用户最多 10 个未完成凭证。确认短请求失败可以重试，不会因此自动重传大文件。密钥仅在服务器保管。

暂存目录为 `ioedu/staging/`，需配置一天后删除的生命周期规则；不要对 `ioedu/tenants/`、`ioedu/hub/` 或 `ioedu/hls/` 设置这条规则。服务暂不自动删除暂存对象，避免凭证尚未失效时被重放覆盖。商店发布/导入的文件仍走后端 OSS 上传。

文件默认使用私有对象权限，密钥只放服务端。站内文件链接沿用原有“知道链接即可读取”的访问方式；私有 Bucket 并不代表站内附件新增了按师生角色授权。

访问按以下优先级选择：

1. **配置 CDN 域名**：本站重定向到 CDN Type A 临时鉴权地址，由 CDN 私有回源读取 OSS，视频、图片等流量不经过业务服务器。CDN 配置必须按下文完成，不能只填写域名。
2. **只配置 OSS 自定义 HTTPS 域名**：本站重定向到 OSS 临时签名地址，客户端直接从 OSS 下载。
3. **两个域名都未配置**：本站读取 OSS 并流式返回，兼容图片、小程序、报告下载和视频拖动，下载流量仍经过业务服务器。

签名链接不持久化，重定向禁止缓存。两份 Nginx 配置取消了上传路径固定 expires，避免缓存失效的签名地址；部署时须同步更新。

阿里云对部分默认域名的在线预览有限制，因此这里仅在配置已绑定 OSS 的自定义域名后启用直连；绑定步骤见[阿里云自定义域名说明](https://help.aliyun.com/zh/oss/user-guide/access-buckets-via-custom-domain-names)。此域名须直接绑定 OSS；不能把普通 CDN 域名当作已兼容 OSS 签名的域名。

AI 处理时，从 OSS 下载有大小限制的临时副本，交给 MinerU/FFmpeg，正常结束或失败后清理。上传服务的 multipart、商店哈希计算也可能使用短期临时文件，因此仍应给容器 `/tmp` 留处理空间。

## 配置

Docker Compose 的 `.env` 示例：

```dotenv
STORAGE_TYPE=oss
OSS_ENDPOINT=https://oss-cn-beijing.aliyuncs.com
OSS_REGION=cn-beijing
OSS_BUCKET=ioedu-files-prod
OSS_PREFIX=ioedu
OSS_ACCESS_KEY_ID=填写RAM用户AccessKeyId
OSS_ACCESS_KEY_SECRET=填写RAM用户AccessKeySecret
OSS_SESSION_TOKEN=
# 可选，未配置时通过后端流式预览
OSS_PUBLIC_DOMAIN=
OSS_SIGNED_URL_SECONDS=3600
# Bucket 跨域和暂存生命周期配置并验证后再启用
OSS_DIRECT_UPLOAD=false
OSS_UPLOAD_ENDPOINT=https://oss-cn-beijing.aliyuncs.com
OSS_UPLOAD_SECONDS=900
# CDN 私有回源、HTTPS 和 Type A 鉴权配置完成后填写
CDN_DOMAIN=
CDN_AUTH_KEY=
CDN_AUTH_SECONDS=7200
# IMM 和 CDN 边转边播配置完成后再开启
VIDEO_HLS=false
```

当前线上已经完成 `files.labcloud.com.cn` 的 CDN 接入：CNAME、HTTPS 证书、私有 Bucket 回源、Type A 鉴权和 Range 回源均已配置，后端的 `CDN_DOMAIN` 已填写为 `https://files.labcloud.com.cn`。未签名地址会被 CDN 拒绝，签名地址和 Range 请求已用迁移后的真实对象验证。`ioedu/staging/` 的生命周期规则也已在 OSS 控制台设置为最后修改后 1 天删除；正式对象前缀没有配置删除规则。Type A 密钥不写入文档，只保存在服务器 `.env` 中。

2026-09-21 已完成 IMM 和 CDN 边转边播授权及联调，主站后端已设 `VIDEO_HLS=true`。360p/720p 应用播放列表、CDN 分片、主站 HTTPS CORS 和完整视频解码通过；详情见《部署记录-20260921》。上面的 false 是新环境模板默认值，更新现有服务器时需保留已经验证的线上配置。

这里使用当前 Bucket 的北京地域。服务器如果不在北京，不可改用北京内网 Endpoint；内网地址只能供同地域云服务器访问。主系统与商店都在 Compose 中接收这些变量，两者使用不同对象前缀。

非 Docker 部署对应：`IOEDU_STORAGE_TYPE`、`IOEDU_OSS_ENDPOINT`、`IOEDU_OSS_REGION`、`IOEDU_OSS_BUCKET`、`IOEDU_OSS_PREFIX`、`IOEDU_OSS_PUBLIC_DOMAIN`、`IOEDU_OSS_SIGNED_URL_SECONDS`。凭据变量仍为 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_SESSION_TOKEN`。

使用与服务器相同地域的 Bucket。OSS SDK 使用 V4 签名，服务地址要求 HTTPS。Bucket 设为私有，给专用 RAM 用户配置所需前缀下的 `oss:PutObject`、`oss:GetObject`、`oss:DeleteObject` 权限；DeleteObject 用于新上传记录保存失败后的补偿清理。不要在前端、小程序或聊天中填写密钥。

这些配置影响已有文件定位，投入使用后不要随意更换 Bucket、前缀或阿里云账号。切回 `local` 只改变新文件写入位置；读取已经在 OSS 的文件仍需保留原 OSS 配置。

### 跨域与微信合法域名

在 OSS 控制台进入 **Bucket 列表 → ioedu-files-prod**，在 Bucket 内侧菜单顶部搜索框搜索「跨域」，进入「跨域设置」并创建规则。不同版本控制台的菜单分组可能不同。来源填写实际网页 Origin（含协议和非标准端口），例如：

```text
https://www.labcloud.com.cn:8443
http://www.labcloud.com.cn:8093
http://139.224.189.163:8093
```

各商户分站与自定义域名也须添加自己的 Origin；只配置主站不会自动覆盖分站。允许方法为 POST、GET、HEAD；允许 Headers 为 `*`；暴露 Headers 为 `ETag`、`Content-Range`、`Accept-Ranges`；缓存时间可填 600 秒。Bucket 保持私有并继续阻止公共访问。跨域只允许浏览器读取响应，不代替 OSS 签名鉴权。

小程序需在微信公众平台加入 OSS 上传域名 `https://ioedu-files-prod.oss-cn-beijing.aliyuncs.com` 的 uploadFile 合法域名，以及实际 CDN/OSS 自定义下载域名。小程序包需要重新上传发布，更新服务器不会自动更新已发布的小程序。

### CDN 与 HLS（可选，需云端配置）

1. 选择一个已备案的子域名，例如 `files.labcloud.com.cn`；添加 CDN 加速域名，源站选择北京的私有 OSS Bucket，并启用私有 Bucket 回源授权。按 CDN 给出的地址添加 DNS CNAME，并配置有效 HTTPS 证书。
2. 开启 **Type A URL 鉴权**，在控制台和服务器使用相同的 `CDN_AUTH_KEY`，鉴权有效期与 `CDN_AUTH_SECONDS` 一致（默认 7200 秒）。代码使用当前签发时间，CDN 以签发时间加控制台有效期校验；鉴权参数不会用作文件缓存键。
3. 开启 Range 回源，缓存图片和 `.ts` 分片（对象名不可变，可设置一天或更久），并配置实际网页来源的 GET/HEAD 跨域、Range 支持。确认签名地址能访问、无签名地址被拒绝后，再填写服务器 `CDN_DOMAIN=https://实际域名`。
4. 需要分段与自适应清晰度时，在北京开通 IMM 并绑定本 Bucket。RAM 调用方保留对象读写权限，补充 `oss:ProcessImm`、`oss:PostProcessTask`、`imm:GenerateVideoPlaylist`、`imm:LiveTranscoding` 和必要的 `ram:PassRole`。项目用 POST 和 `sys/saveas` 保存播放列表，缺少 `oss:PostProcessTask` 会返回 403；现有 GetObject/PutObject/DeleteObject 三项权限只能支持文件存储。
5. 参照官方边转边播配置，为 CDN 私有回源角色配置 `oss:GetObject`、`oss:PostProcessTask`、`oss:ProcessImm`、`imm:GenerateVideoPlaylist`、`imm:LiveTranscoding` 与所需 `ram:PassRole`。仅对 `ioedu/hls/` 下 `.ts` 回源添加 `x-oss-process=if_status_eq_404{hls/ts}`，实际授权资源限定到本 Bucket、项目及 IMM 服务角色。
6. 用真实视频确认 IMM 能生成列表、CDN 能返回分片，再设 `VIDEO_HLS=true` 并重启后端。未配置或测试未通过时保留 false，播放器可正常播放原文件。

HLS 采用 360p/720p、5 秒分片和 10 秒初始转码。服务器只签发少量播放列表，转码由 IMM 执行，分片从 CDN 下载。网页从低码率开始、按网络与播放器大小调整，限制缓冲并在暂停时停止后续分片请求；原生小程序由微信播放器负责缓冲和码率选择。播放失败会回退原视频。所有播放器点击后才加载。

参考：[CDN Type A 鉴权](https://help.aliyun.com/zh/cdn/user-guide/type-a-signing)、[OSS 生成视频播放列表](https://help.aliyun.com/zh/oss/user-guide/generate-video-playlist)、[IMM 通过 CDN 边转边播](https://help.aliyun.com/zh/imm/user-guide/trigger-play-while-ttranscoding-through-cdn)。

## 上线和旧文件迁移

1. 备份全部数据库、原配置和上传卷，再发布主系统与项目商店。主系统执行 V6 评审升级、`V7__oss_storage.sql` 和 `V8__direct_uploads.sql`；商店执行 `V3__oss_assets.sql`。
2. 配置 OSS 并重启这两个服务。先验证一张头像、一个视频、一个报告及一次商店项目发布/安装。
3. 保留原上传卷。新文件直接写 OSS，旧文件继续从本地读取。
4. 使用平台接口分批迁移；默认是预览，不写 OSS。接口需要原有平台管理员权限。

主系统（平台令牌，替换域名与商户编码）：

```bash
# 查看下一批，不执行迁移
curl -X POST 'https://平台域名/api/platform/tenants/c001/storage/migrate?dryRun=true&limit=10' \
  -H "Authorization: Bearer $PLATFORM_TOKEN"
# 实际复制下一批；原文件保留
curl -X POST 'https://平台域名/api/platform/tenants/c001/storage/migrate?dryRun=false&limit=10' \
  -H "Authorization: Bearer $PLATFORM_TOKEN"
```

商店（平台管理后台的 JWT，经原 `/hub-api` 代理）：

```bash
curl -X POST 'https://平台域名/hub-api/hub-admin/assets/migrate?dryRun=true&limit=10' \
  -H "Authorization: Bearer $HUB_ADMIN_JWT"
curl -X POST 'https://平台域名/hub-api/hub-admin/assets/migrate?dryRun=false&limit=10' \
  -H "Authorization: Bearer $HUB_ADMIN_JWT"
```

返回 `files`（本批成功或待迁移文件）、`failures`、`pending`。成功位置写库，下次不会重复迁移。失败记录需要处理后再重试；大文件建议 `limit=1`。此接口同步执行，应与代理超时配合。

迁移不会删除旧文件。确认 OSS 中文件及备份完整、页面可读、AI 可解析后，再安排旧上传卷的清理。当前租户注销也不会自动清空 OSS 对象，平台需按保留策略另行处理。商户容量按 OSS 记录与未迁移的本地文件合计，保留的迁移副本不会重复计费。

## UI 调整与预览

- 上传区沿用圆角虚线框与蓝紫配色，显示文件类型、大小、上传进度和失败重试。
- 附件以卡片展示，图片可放大，视频可播放，报告可打开原件。
- 评分细则改为逐项卡片，显示总分和超出/不足提示。
- 教师评审区突出建议总分、分项依据和需核实事项；长弹窗内容独立滚动，底部操作固定可见。
- MinerU 用两张选项卡切换，仅显示当前方式的配置；另一套参数保留。
- 小程序同步调整附件入口、材料卡片与评审结果。

本地开发预览：在 `frontend` 运行 `npm run dev`，打开 `/dev/review-preview.html`。它使用实际组件和示例数据，不访问后端、不保存成绩，不作为生产页面打包。

## 验证边界

主系统 44 项业务测试、商店 2 项测试通过，网页、微信小程序和设计助手前端生产构建通过。完整 Spring 上下文测试在本机因缺少生产 JWT 配置无法启动，不能据此声称整个测试套件通过；上线时还须检查实际数据库迁移和应用健康状态。现有依赖弃用与网页分包大小提示仍存在。

本地自动化测试覆盖租户对象目录隔离、旧文件兼容、临时文件清理、迁移重入、失败清理、容量去重、视频 Range/HEAD、签名重定向和原 AI 评审链路。网页组件另用浏览器检查手机宽度和教师建议填入。

已验证当前私有 Bucket 的真实 Put/Get/Delete，测试对象已清理。直传签名、配额预留、确认幂等、跨租户拒绝、CDN 鉴权摘要和 HLS 列表约束均有测试覆盖。CDN/IMM 需要真实云端联调，不能以单元测试替代。详细线上状态以部署记录为准。

参考：[官方 Java SDK](https://help.aliyun.com/zh/oss/developer-reference/oss-java-sdk/)、[签名下载](https://www.alibabacloud.com/help/en/oss/developer-reference/download-using-a-presigned-url)。

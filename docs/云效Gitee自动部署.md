# Gitee + 云效自动部署

当前仓库已经推送到 Gitee：

`https://gitee.com/wjc1238765/future-learning-platform.git`

仓库根目录的 `yunxiao.yml` 是 Flow Pipeline-as-code 模板。它监听 `main` 分支 push，打包源码后使用云效 VMDeploy 主机组执行 `scripts/cloud-effect/deploy.sh`。

在云效控制台完成一次性配置：

1. 在「服务连接」中添加 Gitee OAuth/访问授权，复制服务连接 ID。
2. 创建主机组，把生产服务器加入主机组。服务器需要安装 Docker、Docker Compose，并允许云效 Runner 连接；复制主机组 ID。
3. 新建流水线并导入 `yunxiao.yml`，将两个占位符替换为服务连接 ID 和主机组 ID。
4. 打开 Gitee 代码源的 push 触发，默认分支选择 `main`。
5. 首次运行建议手动执行并检查部署日志；后续向 Gitee 的 `main` 推送即可自动部署。

部署脚本会把每次发布解压到独立的 `/opt/ioedu-releases/cloud-时间戳`，生成独立镜像标签，备份生产 `.env` 和 MySQL 数据库，并只重建后端、Hub、前端。数据库、上传卷、Hub 资源卷和 KiCad 容器不会被删除或重置。生产 `.env` 只从服务器读取，仓库中不放密钥。

云效主机部署是全量下载制品，Compose 文件不会自动同步到服务器，所以模板把 Compose 文件和构建所需源码一起打进 `package.tgz`，这符合云效的主机部署方式。详见[云效主机部署文档](https://help.aliyun.com/zh/yunxiao/user-guide/host-deployment-1)和[云效 Gitee 代码源文档](https://help.aliyun.com/zh/yunxiao/user-guide/pipeline-sources)。

回滚时保留对应的 `/opt/ioedu-backups/cloud-时间戳/images.txt` 与 `live.env`，先恢复上一个 release 的镜像变量，再执行同一 Compose 启动命令；不要删除数据库卷。


# 本地镜像更新与运行验证

## 授权及提交

工程师明确要求“推送提交，更新镜像”，并在看到V29空策略影响后明确选择“仅配置platform-gateway为employee，另一个资源服务器保持空集合，凭据不变”。本轮仅更新System、IAM、Admin；不改Boot或Gateway，不重建其他服务/数据卷，不合并PR。

代码提交：673b252aa345d60e38ade0823a921f0ba166c4b9。分支：feat/iam-introspection-user-types。PR：https://github.com/macula-projects/macula-cloud/pull/38（OPEN）。源码与已通过验证和审查的39文件快照一致。

## 构建

标准Compose build尝试因Docker Hub docker/dockerfile:1.7匿名认证接口EOF失败。本轮采用现有本地镜像的已验证运行层，加本地当前源码构建的后端JAR和Docker模式前端dist离线构建；临时Dockerfile仅在私有临时目录，不修改仓库Dockerfile。

本地后端package和Admin Docker-mode build均退出0。离线镜像逐项验证User、Entrypoint、Cmd、WorkingDir、Env、ExposedPorts、Volumes、Healthcheck与原运行层一致；不引入新凭据。

| 服务 | 新镜像ID | 构建产物SHA256 |
|---|---|---|
| macula-cloud-system | `sha256:e283273015a888d3f13e70e2a775ebc9ece81088560168c2971577f03feaa8d8` | `91e0740716542fcda2cb97843546f1fcdad1b6fd7f2ba4cb5e4415b8e35b5a78` |
| macula-cloud-iam | `sha256:e9bb406620a95574d98c9fdd3daa10db534b186352bedb6b908ba5a3113d6287` | `a05449424a9c491295451727f7f9dd9568a6b0284c849d4a8dd992eea543e180` |
| macula-cloud-admin | `sha256:aa2714190f18671d115c80c5cc33c01a6650fa2ce66e7f56ac83a307ffe2a389` | `1aaeae1b8f52c593cc0c7e8f368844fd29a0577af85252ccd12d8eb60696a447` |

镜像标签沿用本地macula/macula-cloud-{system,iam,admin}:6.1.0-SNAPSHOT，未发布到远端镜像仓库。

## 数据库与策略

更新前运行库为V28，已用单事务mysqldump完成非仓库私有备份，文件权限600。System重建后由既有Flyway成功执行V29新增列，未改历史迁移或进行Flyway repair。

策略更新仅作用于managed_key=platform-gateway且purpose=RESOURCE_SERVER的一行；使用主键、原authorization_version和allowed_user_types=[]条件保护，设置["employee"]并将authorization_version递增一次。受影响行数必须为1。另一个资源服务器保持[]；没有轮换任何凭据。

System迁移后、更新策略前核对六张受保护表指纹与升级前一致。策略更新后及全部重建后，归一化本次明确授权的单行版本递增，再次核对六表原有列指纹全部一致；新列另行核对gateway仅employee、另一资源服务器为空。没有隐式填历史用户类型或更改用户/来源/绑定。

## 定向重建

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180 macula-cloud-system
# V29就绪、保护指纹核对及用户批准的单行策略更新后：
docker compose --env-file deploy/.env -f deploy/docker-compose.yml --profile apps up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180 macula-cloud-iam macula-cloud-admin
```

三个服务均running/healthy。容器中application.jar、Admin index.html的SHA256与构建输入完全一致；实际HTTP提供的Admin index也一致。保留原运行时Admin配置和deploy/.env指纹。

## HTTP冒烟

| 检查 | 结果 |
|---|---|
| Admin首页 | 200 |
| IAM登录页 | 200 |
| IAM OIDC Discovery | 200 |
| System受保护菜单路由（无Token） | 401 |
| Gateway无效Token请求 | 401 |
| 使用现有Gateway身份调用IAM内省无效Token | 200，响应仅active:false |

未输出Basic凭据、真实Token或用户数据；本轮不声称完成真实用户登录/第三方设备联调。旧用户Token/主体不做兼容，现有用户应退出旧会话后重新登录。

## 范围与限制

除这三个目标容器外，其他31个容器ID保持不变，包括Gateway、中间件及其他工作区容器。SnailJob在更新前已unhealthy，不属于本次刷新范围；未尝试修复或重启。Boot工作区为空。

这份运行证据与既有718个后端测试（717通过、1条件跳过）、224个Admin单测、52个Cypress及V29隔离MySQL2例分开；不把仅打包/镜像健康视为业务全量验收。

原始备份、镜像构建日志、运行日志及指纹只保留在本机私有临时目录，未提交凭据、SQL备份或生成物。PR保持OPEN，未执行合并。

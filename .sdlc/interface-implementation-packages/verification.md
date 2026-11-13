# 包结构整理验证

日期：2026-11-13

## 结论

本次包结构整理的本地验证通过：相关模块 clean test 和全仓 test 均 BUILD SUCCESS，Checkstyle 和正文等价检查通过。外部 System 迁移测试依既有环境条件跳过，不据此声明真实迁移、生产授权或部署验证完成。

## 执行命令及真实输出

### 相关模块干净构建

```bash
mvn -pl macula-cloud-api/macula-cloud-system-api,macula-cloud-iam,macula-cloud-system,macula-cloud-gateway -am clean test -Plocal
```

六个 reactor 项目全部 SUCCESS。汇总：674 个测试，0 failures，0 errors，1 skipped（实际通过 673 个）。

### 全仓后端测试

```bash
mvn test -Plocal
```

十一个 reactor 项目全部 SUCCESS；Maven 原始各模块汇总：

```text
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 442, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 202, Failures: 0, Errors: 0, Skipped: 1
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

合计 710 个测试，0 failures，0 errors，1 skipped，即实际通过 709 个。TinyID 的 Testcontainers MySQL 集成测试已实际执行，使用测试创建的临时数据库，不连接运行业务库。

### 代码规范

使用本机已有 Checkstyle classpath，执行 com.puppycrawl.tools.checkstyle.Main，配置为本仓库 .config/checkstyle.xml，header 为 .config/checkstyle-header.txt；输入为 git diff 和 untracked 的所有现存 Java 文件：

```text
Java files: 83 Checkstyle exit: 0
开始检查……
检查完成。
```

`git diff --check` 退出 0。

## Proof 对照

- 16 个生产类型与 8 个对应测试均已位于目标包，旧文件不存在；无生产接口和其直接实现同包的扫描结果，无 Impl 后缀类型遗留在 impl 包外。
- Java 源码旧 FQCN 引用残留为 0；源码/测试改动均在 plan.md 精确清单中。
- 747 个原 Java 文件，去掉 package/import 和空白、归一化本次批准的 FQCN 变化后正文一致；方法体没有业务改动。历史 V18 只调整 import，SQL 无改动。
- API 协议注册表、JSON、IAM Bean 装配/密钥回退/授权持久化、验证码、可信租户和缓存现有单测随本次 clean/test 实际执行并通过。
- 四个模块 README 同步新包约定及外部 Java import 兼容要求；稳定 JSON kind 和字段保持。

## 条件跳过和未执行范围

SysUserUsernameUniqueMigrationTest 受既有 @EnabledIfEnvironmentVariable(named="SYSTEM_MIGRATION_IT", matches="true") 控制，本次未开启，跳过 1 个。未执行 Maven Failsafe *IT、外部 System MySQL 迁移验证、真实第三方登录、浏览器或部署验证；本次为 Java 包调整，前端及 SQL 未变更，计划未要求运行这些套件。

## 配置及 eval

没有修改 AGENTS.md、CLAUDE.md、技能、hook、REVIEW.md、reviewer 或运行配置，本次不触发配置 eval。Plan 独立审查 Round 1/3 为 NO_FINDINGS，详见 review-findings.md；没有自批 PR 或发布门。

## 原始日志

本机原始日志：/private/tmp/macula-package-targeted.log、/private/tmp/macula-package-all-tests.log、/private/tmp/macula-package-checkstyle.log。未将构建产物加入仓库。

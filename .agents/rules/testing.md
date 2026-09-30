# 测试与验证

编写测试、修改测试命名或决定验证范围时加载。

## 后端测试

- 纯业务逻辑优先使用 JUnit 5 与 Mockito，不启动 Spring，也不连接外部服务。
- Web、Security、配置绑定和数据访问按目标选择 slice 测试；只有验证完整装配或服务集成时使用 `@SpringBootTest`。
- 连接 MySQL、Redis、Nacos、RocketMQ、Seata、SnailJob、远程 HTTP 或其他外部设施的测试属于集成测试，必须记录运行条件。
- 修复缺陷先添加可复现测试；新行为覆盖正常路径、关键边界和拒绝/失败路径。
- 认证、授权、租户和网关改动至少覆盖允许与拒绝两类场景，防止测试只证明成功路径。
- 测试数据不得包含真实凭据或个人信息；测试应可重复、与执行顺序无关，并清理自己创建的外部状态。
- 新测试类遵守仓库版权头和类级 Javadoc 约定；失败必须通过断言或未处理异常反馈，不能只打印结果。

## 前端测试

- 工具函数、store 和组件行为使用 Vitest；页面级关键流程、路由与权限交互使用 Cypress。
- 测试用户可观察行为，不绑定不稳定的内部实现；选择器优先使用稳定语义或专用测试属性。
- API 请求使用可控 mock 或测试环境，不在单元测试中访问真实后端。
- UI 改动至少验证 loading、空态、错误态和成功态；表单还需覆盖校验与重复提交。

## 验证顺序

1. 运行最小相关后端测试：`mvn -pl <module-path> -am test -P<profile>`。
2. `macula-cloud-system` 改动连同 API 契约验证：`mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal`。
3. 跨服务 Java 改动后扩大到 `mvn test -Plocal`；如果外部依赖不具备，明确报告未运行范围和所需条件。
4. 前端改动先运行相关 Vitest，再运行 `npm run build`；关键流程变化运行 `npm run test:e2e:ci`。
5. POM、profile、资源过滤或打包插件变化时，增加受影响模块的 `package` 验证。

## 结果报告

- 报告实际执行的命令、成功/失败结果和未验证范围，不把仅编译或 `-DskipTests=true` 描述为测试通过。
- 区分代码失败与环境失败；外部服务未就绪时保留原始错误摘要，并说明需要的服务、地址或初始化数据。
- Maven 测试报告通常位于各模块 `target/surefire-reports`；Cypress 失败时检查控制台以及生成的截图/视频，但不要提交这些产物。

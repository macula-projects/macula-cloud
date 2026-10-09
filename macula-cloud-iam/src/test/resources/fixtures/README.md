# 历史授权序列化样本

`legacy-authorization.base64` 是 JDK 序列化字节的 Base64 文本，仅含虚构测试数据。

- 来源：提交 `d32fc5f692a12e798050cac1ffa8e38405f31d1d` 中 `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/pojo/dto/Authorization.java`，不含本次新增字段与显式 serialVersionUID。
- 源文件 SHA-256：`6fe455f89cd7c17b9877c8e1070122c7228217bf69b7543f1d6c0fa57aee382f`。
- 历史 DTO 由 javac/Lombok 1.18.46 编译，原计算 UID 为 `-3691068652995760622`；使用独立 classpath 装载历史类并通过 ObjectOutputStream 生成，不能用当前 DTO 重生成以替代历史兼容证据。
- 数据：id=`legacy-binary-fixture`、registeredClientId=`test-client`、principalName=`fixture-user`、grant=`password`、scope=`openid`、access token=`legacy-binary-access`；签发时间 2020-01-01，过期时间 2099-01-01，供确定性测试使用，不是可用凭据。
- attributes/accessTokenMetadata 为带 `java.util.HashMap` 类型标记的空对象；其他未使用字段为空。历史嵌套用户对象不在此固定样本中，由独立 Principal/授权请求往返测试覆盖。

测试直接把历史字节写入隔离 Redis 旧键，再用当前实现读取、迁移、删除并确认不能复活；没有先用当前 DTO 重新序列化。

/*
 * Copyright (c) 2023 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.macula.cloud.tinyid.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.tinyid.config.DynamicDataSource;
import dev.macula.cloud.tinyid.converter.TinyIdManagementConverter;
import dev.macula.cloud.tinyid.mapper.TinyIdAuditLogMapper;
import dev.macula.cloud.tinyid.mapper.TinyIdInfoMapper;
import dev.macula.cloud.tinyid.pojo.bo.*;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdAuditLog;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdInfo;
import dev.macula.cloud.tinyid.pojo.form.CreateBusinessForm;
import dev.macula.cloud.tinyid.pojo.query.AuditLogPageQuery;
import dev.macula.cloud.tinyid.pojo.query.BusinessPageQuery;
import dev.macula.cloud.tinyid.pojo.vo.*;
import dev.macula.cloud.tinyid.service.TinyIdManagementService;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 编排 TinyID 管理业务，并通过 MyBatis-Plus Mapper 在全部物理数据源执行读写。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public class TinyIdManagementServiceImpl implements TinyIdManagementService {

    /** 发号业务 Mapper。 */
    private final TinyIdInfoMapper infoMapper;
    /** 管理审计日志 Mapper。 */
    private final TinyIdAuditLogMapper auditLogMapper;
    /** 发号随机路由与管理显式路由共用的数据源。 */
    private final DynamicDataSource routingDataSource;
    /** 单个物理数据源本地事务模板。 */
    private final TransactionTemplate transactionTemplate;
    /** 管理领域对象到对外视图对象的转换器。 */
    private final TinyIdManagementConverter converter;

    /**
     * 创建 TinyID 管理服务。
     *
     * @param infoMapper        发号业务 Mapper
     * @param auditLogMapper    管理审计日志 Mapper
     * @param routingDataSource 动态路由数据源
     * @param converter         管理领域对象转换器
     */
    public TinyIdManagementServiceImpl(TinyIdInfoMapper infoMapper,
        TinyIdAuditLogMapper auditLogMapper, DynamicDataSource routingDataSource,
        TinyIdManagementConverter converter) {
        this.infoMapper = infoMapper;
        this.auditLogMapper = auditLogMapper;
        this.routingDataSource = routingDataSource;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(routingDataSource));
        this.converter = converter;
    }

    /** {@inheritDoc} */
    @Override
    public IPage<TinyIdBusinessAggregateVO> listBusinesses(BusinessPageQuery query) {
        return converter.toBusinessPage(listBusinesses(query.getPage(), query.getPageSize(), query.getKeywords()));
    }

    /** {@inheritDoc} */
    @Override
    public TinyIdBusinessAggregateVO getBusiness(String bizType) {
        TinyIdBusinessAggregateBO business = getBusinessBO(bizType);
        if (business == null) {
            throw new IllegalArgumentException("TinyID business does not exist");
        }
        return converter.toBusinessAggregateVO(business);
    }

    /** {@inheritDoc} */
    @Override
    public TinyIdBusinessAggregateVO createBusiness(CreateBusinessForm form) {
        String bizType = form.getBizType().trim();
        int delta = form.getDelta() == null ? 10 : form.getDelta();
        if (getBusinessBO(bizType) != null) {
            throw new IllegalArgumentException("TinyID business already exists");
        }
        boolean written = false;
        try {
            createBusinessOnAllDataSources(bizType, form.getStep(), delta);
            written = true;
            return getBusiness(bizType);
        } catch (RuntimeException ex) {
            throw compensate(written ? () -> deleteBusinessOnAllDataSources(bizType) : null, ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteBusiness(String bizType) {
        String normalizedBizType = bizType.trim();
        TinyIdBusinessAggregateBO business = getBusinessBO(normalizedBizType);
        if (business == null) {
            throw new IllegalArgumentException("TinyID business does not exist");
        }
        boolean deletable = !business.getDataSources().isEmpty() && business.getDataSources().stream().allMatch(
            instance -> Boolean.TRUE.equals(instance.getHealthy()) && instance.getId() != null && Objects.equals(
                instance.getMaxId(), 0L));
        if (!deletable) {
            throw new IllegalStateException(
                "TinyID business can only be deleted when max_id is zero on every datasource");
        }
        deleteBusinessOnAllDataSources(normalizedBizType);
    }

    /** {@inheritDoc} */
    @Override
    public TinyIdBusinessConsistencyVO getConsistency(String bizType) {
        TinyIdBusinessAggregateBO business = getBusinessBO(bizType);
        if (business == null) {
            return new TinyIdBusinessConsistencyVO(bizType, "PENDING", List.of());
        }
        return new TinyIdBusinessConsistencyVO(bizType, business.getConsistencyStatus(),
            converter.toBusinessVOs(business.getDataSources()));
    }

    /** {@inheritDoc} */
    @Override
    public List<TinyIdDataSourceVO> listDataSources() {
        return converter.toDataSourceVOs(listDataSourceBOs());
    }

    /** {@inheritDoc} */
    @Override
    public IPage<TinyIdAuditLogVO> listAuditLogs(AuditLogPageQuery query) {
        return converter.toAuditLogPage(listAuditLogs(query.getPage(), query.getPageSize(), query.getOperator()));
    }

    /**
     * 执行跨数据源写入补偿。
     *
     * @param compensation 可为空的补偿动作
     * @param cause        原始异常
     * @return 应向上抛出的异常，补偿失败时以补偿异常为主
     */
    private RuntimeException compensate(Runnable compensation, RuntimeException cause) {
        RuntimeException failure = cause;
        if (compensation != null) {
            try {
                compensation.run();
            } catch (RuntimeException compensationFailure) {
                compensationFailure.addSuppressed(cause);
                failure = compensationFailure;
            }
        }
        return failure;
    }

    /** 分页查询聚合后的发号业务领域对象。 */
    IPage<TinyIdBusinessAggregateBO> listBusinesses(int page, int pageSize, String keywords) {
        List<String> businessTypes = findBusinessTypes(keywords);
        int fromIndex = Math.toIntExact(Math.min((long)(page - 1) * pageSize, businessTypes.size()));
        int toIndex = Math.min(fromIndex + pageSize, businessTypes.size());
        Page<TinyIdBusinessAggregateBO> result = new Page<>(page, pageSize, businessTypes.size());
        result.setRecords(businessTypes.subList(fromIndex, toIndex).stream().map(this::aggregateBusiness).toList());
        return result;
    }

    /**
     * 汇总全部物理数据源中的业务类型，并以名称提供稳定的跨库分页顺序。
     *
     * @param keywords 可为空的业务类型关键字
     * @return 去重且排序后的业务类型
     */
    private List<String> findBusinessTypes(String keywords) {
        TreeSet<String> businessTypes = new TreeSet<>();
        for (String key : dataSourceKeys()) {
            List<TinyIdInfo> businesses = on(key, () -> infoMapper.selectList(Wrappers.<TinyIdInfo>lambdaQuery()
                .like(keywords != null && !keywords.isBlank(), TinyIdInfo::getBizType,
                    keywords == null ? null : keywords.trim()).select(TinyIdInfo::getBizType)));
            businesses.stream().map(TinyIdInfo::getBizType).forEach(businessTypes::add);
        }
        return List.copyOf(businessTypes);
    }

    /** 查询聚合后的发号业务领域对象。 */
    TinyIdBusinessAggregateBO getBusinessBO(String bizType) {
        List<TinyIdBusinessBO> entries = findBusinessAcrossDataSources(bizType);
        return entries.stream().noneMatch(entry -> entry.getId() != null) ? null : aggregateBusiness(bizType, entries);
    }

    /** 查询业务在全部数据源中的配置。 */
    List<TinyIdBusinessBO> findBusinessAcrossDataSources(String bizType) {
        List<TinyIdBusinessBO> result = new ArrayList<>();
        for (String key : dataSourceKeys()) {
            try {
                TinyIdBusinessBO business = getBusinessInstance(key, bizType);
                result.add(business == null ? missingBusiness(key, bizType, true) : business);
            } catch (RuntimeException ex) {
                result.add(missingBusiness(key, bizType, false));
            }
        }
        return result;
    }

    /** 在全部数据源创建发号业务。 */
    void createBusinessOnAllDataSources(String bizType, int step, int delta) {
        List<String> keys = dataSourceKeys();
        if (keys.size() > delta) {
            throw new IllegalArgumentException("delta must be greater than every TinyID datasource sequence");
        }
        for (String key : keys) {
            ensureHealthy(key);
            if (businessCount(key, bizType) > 0) {
                throw new IllegalArgumentException("TinyID business already exists");
            }
        }
        List<String> inserted = new ArrayList<>();
        try {
            for (String key : keys) {
                on(key, () -> infoMapper.insert(newBusiness(bizType, step, delta, sequence(key))));
                inserted.add(key);
            }
        } catch (RuntimeException ex) {
            Collections.reverse(inserted);
            for (String key : inserted) {
                try {
                    deleteBusiness(key, bizType);
                } catch (RuntimeException compensationFailure) {
                    ex.addSuppressed(compensationFailure);
                }
            }
            if (ex.getSuppressed().length > 0) {
                throw new IllegalStateException("TinyID cross-datasource business compensation failed", ex);
            }
            throw ex;
        }
    }

    /** 删除指定数据源中的发号业务。 */
    void deleteBusiness(String dataSourceKey, String bizType) {
        on(dataSourceKey,
            () -> infoMapper.delete(Wrappers.<TinyIdInfo>lambdaQuery().eq(TinyIdInfo::getBizType, bizType)));
    }

    /** 删除全部数据源中的未使用业务。 */
    void deleteBusinessOnAllDataSources(String bizType) {
        Map<String, TinyIdInfo> businessSnapshots = new LinkedHashMap<>();
        for (String key : dataSourceKeys()) {
            TinyIdInfo business = findBusinessEntity(key, bizType);
            if (business == null) {
                throw new IllegalStateException("TinyID business is not configured on every datasource");
            }
            if (!Objects.equals(business.getMaxId(), 0L)) {
                throw new IllegalStateException(
                    "TinyID business can only be deleted when max_id is zero on every datasource");
            }
            businessSnapshots.put(key, business);
        }
        List<String> completed = new ArrayList<>();
        try {
            for (String key : dataSourceKeys()) {
                inTransaction(key, () -> {
                    int deleted = infoMapper.delete(
                        Wrappers.<TinyIdInfo>lambdaQuery().eq(TinyIdInfo::getBizType, bizType)
                            .eq(TinyIdInfo::getMaxId, 0L));
                    if (deleted != 1) {
                        throw new IllegalStateException(
                            "TinyID business can only be deleted when max_id is zero on every datasource");
                    }
                });
                completed.add(key);
            }
        } catch (RuntimeException ex) {
            restoreBusinessSnapshots(completed, businessSnapshots, ex);
            throw ex;
        }
    }

    /** 查询物理数据源状态领域对象。 */
    List<TinyIdDataSourceBO> listDataSourceBOs() {
        return dataSourceKeys().stream().map(key -> {
            try {
                ensureHealthy(key);
                return new TinyIdDataSourceBO(key, sequence(key), true);
            } catch (RuntimeException ex) {
                return new TinyIdDataSourceBO(key, sequence(key), false);
            }
        }).collect(Collectors.toList());
    }

    /** 分页查询管理审计日志领域对象。 */
    IPage<TinyIdAuditLogBO> listAuditLogs(int page, int pageSize, String operator) {
        Page<TinyIdAuditLog> result = primary(() -> auditLogMapper.selectPage(new Page<>(page, pageSize),
            Wrappers.<TinyIdAuditLog>lambdaQuery()
                .like(operator != null && !operator.isBlank(), TinyIdAuditLog::getOperator,
                    operator == null ? null : operator.trim()).orderByDesc(TinyIdAuditLog::getId)));
        return result.convert(this::toAuditLogBO);
    }

    /**
     * 创建发号业务实体。
     *
     * @param bizType   业务类型
     * @param step      号段步长
     * @param delta     多数据库预留实例数
     * @param remainder 当前数据源余数
     * @return 新发号业务实体
     */
    private TinyIdInfo newBusiness(String bizType, int step, int delta, int remainder) {
        TinyIdInfo business = new TinyIdInfo();
        Date now = new Date();
        business.setBizType(bizType);
        business.setBeginId(0L);
        business.setMaxId(0L);
        business.setStep(step);
        business.setDelta(delta);
        business.setRemainder(remainder);
        business.setCreateTime(now);
        business.setUpdateTime(now);
        business.setVersion(0L);
        return business;
    }

    /**
     * 查询指定数据源上的业务记录数。
     *
     * @param key     数据源展示标识
     * @param bizType 业务类型
     * @return 业务记录数
     */
    private long businessCount(String key, String bizType) {
        return on(key,
            () -> infoMapper.selectCount(Wrappers.<TinyIdInfo>lambdaQuery().eq(TinyIdInfo::getBizType, bizType)));
    }

    /**
     * 查询一个数据源中的发号业务实体。
     *
     * @param key     数据源展示标识
     * @param bizType 业务类型
     * @return 发号业务实体，不存在时返回 {@code null}
     */
    private TinyIdInfo findBusinessEntity(String key, String bizType) {
        return on(key,
            () -> infoMapper.selectOne(Wrappers.<TinyIdInfo>lambdaQuery().eq(TinyIdInfo::getBizType, bizType)));
    }

    /**
     * 查询一个数据源中的发号业务配置。
     *
     * @param key     数据源展示标识
     * @param bizType 业务类型
     * @return 发号业务对象，不存在时返回 {@code null}
     */
    private TinyIdBusinessBO getBusinessInstance(String key, String bizType) {
        TinyIdInfo entity = findBusinessEntity(key, bizType);
        return entity == null ? null : toBusinessBO(key, entity);
    }

    /**
     * 检查指定物理数据源能否执行查询。
     *
     * @param key 数据源展示标识
     */
    private void ensureHealthy(String key) {
        on(key, () -> infoMapper.selectCount(Wrappers.emptyWrapper()));
    }

    /**
     * 加载并聚合一个业务在全部数据源中的配置。
     *
     * @param bizType 业务类型
     * @return 跨数据源业务聚合对象
     */
    private TinyIdBusinessAggregateBO aggregateBusiness(String bizType) {
        return aggregateBusiness(bizType, findBusinessAcrossDataSources(bizType));
    }

    /**
     * 将已加载的各数据源配置聚合为业务对象。
     *
     * @param bizType 业务类型
     * @param entries 各数据源业务配置
     * @return 跨数据源业务聚合对象
     */
    private TinyIdBusinessAggregateBO aggregateBusiness(String bizType, List<TinyIdBusinessBO> entries) {
        String status = businessStatus(entries);
        TinyIdBusinessBO configured = entries.stream().filter(entry -> entry.getId() != null).findFirst().orElse(null);
        Integer step = configured == null ? null : configured.getStep();
        Integer delta = configured == null ? null : configured.getDelta();
        return new TinyIdBusinessAggregateBO(bizType, step, delta, status, entries);
    }

    /**
     * 计算业务的跨数据源一致性状态。
     *
     * @param entries 各数据源业务配置
     * @return COMPLETE、PENDING 或 CONFLICT
     */
    private String businessStatus(List<TinyIdBusinessBO> entries) {
        List<TinyIdBusinessBO> configured = entries.stream().filter(entry -> entry.getId() != null).toList();
        if (configured.isEmpty()) {
            return "PENDING";
        }
        Set<Integer> steps = configured.stream().map(TinyIdBusinessBO::getStep).collect(Collectors.toSet());
        Set<Integer> deltas = configured.stream().map(TinyIdBusinessBO::getDelta).collect(Collectors.toSet());
        Set<Integer> remainders = configured.stream().map(TinyIdBusinessBO::getRemainder).collect(Collectors.toSet());
        int delta = configured.get(0).getDelta();
        boolean conflict =
            steps.size() != 1 || deltas.size() != 1 || remainders.size() != configured.size() || remainders.stream()
                .anyMatch(value -> value < 0 || value >= delta) || configured.stream()
                .anyMatch(entry -> !Objects.equals(entry.getRemainder(), entry.getSequence()));
        if (conflict) {
            return "CONFLICT";
        }
        boolean allHealthyAndConfigured = entries.size() == dataSourceKeys().size() && entries.stream()
            .allMatch(entry -> Boolean.TRUE.equals(entry.getHealthy()) && entry.getId() != null);
        return allHealthyAndConfigured ? "COMPLETE" : "PENDING";
    }

    /**
     * 构造数据源缺失业务时的占位对象。
     *
     * @param key     数据源展示标识
     * @param bizType 业务类型
     * @param healthy 数据源是否可访问
     * @return 缺失业务占位对象
     */
    private TinyIdBusinessBO missingBusiness(String key, String bizType, boolean healthy) {
        TinyIdBusinessBO business = new TinyIdBusinessBO();
        business.setDataSourceKey(key);
        business.setSequence(sequence(key));
        business.setHealthy(healthy);
        business.setBizType(bizType);
        return business;
    }

    /**
     * 将业务实体转换为带数据源信息的业务对象。
     *
     * @param key    数据源展示标识
     * @param entity 发号业务实体
     * @return 发号业务对象
     */
    private TinyIdBusinessBO toBusinessBO(String key, TinyIdInfo entity) {
        TinyIdBusinessBO business = new TinyIdBusinessBO();
        business.setId(entity.getId());
        business.setDataSourceKey(key);
        business.setSequence(sequence(key));
        business.setHealthy(true);
        business.setBizType(entity.getBizType());
        business.setBeginId(entity.getBeginId());
        business.setMaxId(entity.getMaxId());
        business.setStep(entity.getStep());
        business.setDelta(entity.getDelta());
        business.setRemainder(entity.getRemainder());
        business.setVersion(entity.getVersion());
        business.setCreateTime(toLocalDateTime(entity.getCreateTime()));
        business.setUpdateTime(toLocalDateTime(entity.getUpdateTime()));
        return business;
    }

    /**
     * 将审计实体转换为业务对象。
     *
     * @param entity 审计日志实体
     * @return 审计日志业务对象
     */
    private TinyIdAuditLogBO toAuditLogBO(TinyIdAuditLog entity) {
        TinyIdAuditLogBO auditLog = new TinyIdAuditLogBO();
        auditLog.setId(entity.getId());
        auditLog.setOperator(entity.getOperator());
        auditLog.setAction(entity.getAction());
        auditLog.setRequestMethod(entity.getRequestMethod());
        auditLog.setRequestUri(entity.getRequestUri());
        auditLog.setSuccess(entity.isSuccess());
        auditLog.setErrorSummary(entity.getErrorSummary());
        auditLog.setClientIp(entity.getClientIp());
        auditLog.setCreateTime(entity.getCreateTime());
        return auditLog;
    }

    /**
     * 将旧版日期转换为本地日期时间。
     *
     * @param value 日期值
     * @return 本地日期时间，输入为空时返回 {@code null}
     */
    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 恢复已删除数据源中的业务快照。
     *
     * @param completed      已完成删除的数据源
     * @param businesses     各数据源删除前的业务实体
     * @param cause          原始删除异常
     */
    private void restoreBusinessSnapshots(List<String> completed, Map<String, TinyIdInfo> businesses,
        RuntimeException cause) {
        compensateDeletedData(completed, cause, "TinyID business deletion compensation failed", (key, ignored) -> {
            infoMapper.insert(businesses.get(key));
        });
    }

    /**
     * 逆序恢复已完成删除的数据源，并聚合补偿失败信息。
     *
     * @param completed 已完成删除的数据源
     * @param cause     原始删除异常
     * @param message   补偿失败提示
     * @param restore   单个数据源的恢复动作
     */
    private void compensateDeletedData(List<String> completed, RuntimeException cause, String message,
        BiConsumer<String, Void> restore) {
        List<String> keys = new ArrayList<>(completed);
        Collections.reverse(keys);
        RuntimeException compensationFailure = null;
        for (String key : keys) {
            try {
                inTransaction(key, () -> restore.accept(key, null));
            } catch (RuntimeException ex) {
                if (compensationFailure == null) {
                    compensationFailure = new IllegalStateException(message, ex);
                    compensationFailure.addSuppressed(cause);
                } else {
                    compensationFailure.addSuppressed(ex);
                }
            }
        }
        if (compensationFailure != null) {
            throw compensationFailure;
        }
    }

    /**
     * 在首个物理数据源上执行查询。
     *
     * @param action 数据访问动作
     * @param <T>    返回类型
     * @return 数据访问结果
     */
    private <T> T primary(Supplier<T> action) {
        return routingDataSource.execute(0, action);
    }

    /**
     * 在指定物理数据源上执行查询。
     *
     * @param key    数据源展示标识
     * @param action 数据访问动作
     * @param <T>    返回类型
     * @return 数据访问结果
     */
    private <T> T on(String key, Supplier<T> action) {
        return routingDataSource.execute(sequence(key), action);
    }

    /**
     * 在指定物理数据源上执行命令。
     *
     * @param key    数据源展示标识
     * @param action 数据访问动作
     */
    private void on(String key, Runnable action) {
        routingDataSource.execute(sequence(key), action);
    }

    /**
     * 在指定物理数据源的本地事务中执行动作。
     *
     * @param key    数据源展示标识
     * @param action 事务动作
     */
    private void inTransaction(String key, Runnable action) {
        routingDataSource.execute(sequence(key),
            () -> transactionTemplate.executeWithoutResult(status -> action.run()));
    }

    /**
     * 生成按物理数据源列表下标命名的展示标识。
     *
     * @return 有序数据源展示标识
     */
    private List<String> dataSourceKeys() {
        return IntStream.range(0, routingDataSource.size()).mapToObj(index -> "datasource-" + index).toList();
    }

    /**
     * 获取数据源在物理列表中的下标。
     *
     * @param key 数据源展示标识
     * @return 数据源列表下标，同时也是新业务的 remainder
     */
    private int sequence(String key) {
        int index = dataSourceKeys().indexOf(key);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown TinyID datasource");
        }
        return index;
    }

}

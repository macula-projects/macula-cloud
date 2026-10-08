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
import dev.macula.cloud.tinyid.mapper.TinyIdTokenMapper;
import dev.macula.cloud.tinyid.pojo.bo.*;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdAuditLog;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdInfo;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdToken;
import dev.macula.cloud.tinyid.pojo.form.AddApplicationBusinessesForm;
import dev.macula.cloud.tinyid.pojo.form.CreateApplicationForm;
import dev.macula.cloud.tinyid.pojo.form.CreateBusinessForm;
import dev.macula.cloud.tinyid.pojo.form.UpdateApplicationRemarkForm;
import dev.macula.cloud.tinyid.pojo.query.ApplicationPageQuery;
import dev.macula.cloud.tinyid.pojo.query.AuditLogPageQuery;
import dev.macula.cloud.tinyid.pojo.query.BusinessPageQuery;
import dev.macula.cloud.tinyid.pojo.vo.*;
import dev.macula.cloud.tinyid.service.TinyIdManagementService;
import dev.macula.cloud.tinyid.service.TinyIdTokenService;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
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

    /** 随机 Token 的字节长度。 */
    private static final int TOKEN_BYTES = 32;
    /** 生成唯一 Token 时允许的最大尝试次数。 */
    private static final int TOKEN_ATTEMPTS = 5;
    /** 发号业务 Mapper。 */
    private final TinyIdInfoMapper infoMapper;
    /** 应用授权 Mapper。 */
    private final TinyIdTokenMapper tokenMapper;
    /** 管理审计日志 Mapper。 */
    private final TinyIdAuditLogMapper auditLogMapper;
    /** 发号随机路由与管理显式路由共用的数据源。 */
    private final DynamicDataSource routingDataSource;
    /** 单个物理数据源本地事务模板。 */
    private final TransactionTemplate transactionTemplate;
    /** 当前实例的 Token 授权缓存服务。 */
    private final TinyIdTokenService tokenService;
    /** 管理领域对象到对外视图对象的转换器。 */
    private final TinyIdManagementConverter converter;
    /** 用于生成不可预测接入 Token 的安全随机数生成器。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建 TinyID 管理服务。
     *
     * @param infoMapper        发号业务 Mapper
     * @param tokenMapper       应用授权 Mapper
     * @param auditLogMapper    管理审计日志 Mapper
     * @param routingDataSource 动态路由数据源
     * @param tokenService      当前实例的 Token 授权缓存服务
     * @param converter         管理领域对象转换器
     */
    public TinyIdManagementServiceImpl(TinyIdInfoMapper infoMapper, TinyIdTokenMapper tokenMapper,
        TinyIdAuditLogMapper auditLogMapper, DynamicDataSource routingDataSource, TinyIdTokenService tokenService,
        TinyIdManagementConverter converter) {
        this.infoMapper = infoMapper;
        this.tokenMapper = tokenMapper;
        this.auditLogMapper = auditLogMapper;
        this.routingDataSource = routingDataSource;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(routingDataSource));
        this.tokenService = tokenService;
        this.converter = converter;
    }

    /** {@inheritDoc} */
    @Override
    public IPage<TinyIdApplicationVO> listApplications(ApplicationPageQuery query) {
        return converter.toApplicationPage(listApplications(query.getPage(), query.getPageSize(), query.getKeywords()));
    }

    /** {@inheritDoc} */
    @Override
    public TinyIdApplicationVO getApplication(long appId) {
        return converter.toApplicationVO(getApplicationBO(appId));
    }

    /** {@inheritDoc} */
    @Override
    public TinyIdApplicationVO createApplication(CreateApplicationForm form) {
        List<String> bizTypes = distinct(form.getBizTypes());
        String remark = form.getRemark().trim();
        TinyIdApplicationBO application;
        String token = null;
        boolean written = false;
        try {
            token = generateUniqueToken();
            requireCompleteBusinesses(bizTypes);
            createApplicationOnAllDataSources(token, remark, bizTypes);
            written = true;
            application = getApplicationByToken(token);
        } catch (RuntimeException ex) {
            String allocatedToken = token;
            throw compensate(written ? () -> deleteApplicationOnAllDataSources(allocatedToken) : null, ex);
        }
        tokenService.replaceAuthorizations(application.getToken(), application.getBizTypes());
        return converter.toApplicationVO(application);
    }

    /** {@inheritDoc} */
    @Override
    public TinyIdApplicationVO updateRemark(long appId, UpdateApplicationRemarkForm form) {
        TinyIdApplicationBO application = getApplicationBO(appId);
        updateRemarkOnAllDataSources(application.getToken(), form.getRemark().trim());
        return converter.toApplicationVO(getApplicationBO(appId));
    }

    /** {@inheritDoc} */
    @Override
    public TinyIdApplicationVO addBusinesses(long appId, AddApplicationBusinessesForm form) {
        List<String> requestedBizTypes = distinct(form.getBizTypes());
        TinyIdApplicationBO application = getApplicationBO(appId);
        requireCompleteBusinesses(requestedBizTypes);
        if (requestedBizTypes.stream().anyMatch(application.getBizTypes()::contains)) {
            throw new IllegalArgumentException("TinyID application authorization already exists");
        }
        List<AuthorizationInsert> inserted = List.of();
        try {
            inserted =
                addAuthorizationsOnAllDataSources(application.getToken(), application.getRemark(), requestedBizTypes);
            TinyIdApplicationBO updated = getApplicationBO(appId);
            tokenService.replaceAuthorizations(updated.getToken(), updated.getBizTypes());
            return converter.toApplicationVO(updated);
        } catch (RuntimeException ex) {
            List<AuthorizationInsert> actualInserts = inserted;
            throw compensate(actualInserts.isEmpty() ? null
                : () -> deleteInsertedAuthorizations(application.getToken(), actualInserts), ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteApplication(long appId) {
        TinyIdApplicationBO application = getApplicationBO(appId);
        deleteApplicationOnAllDataSources(application.getToken());
        tokenService.removeToken(application.getToken());
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
        tokenService.removeBusiness(normalizedBizType);
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
     * 生成数据库中尚未使用的随机接入 Token。
     *
     * @return 唯一的 URL 安全 Token
     */
    private String generateUniqueToken() {
        for (int i = 0; i < TOKEN_ATTEMPTS; i++) {
            byte[] bytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!tokenExists(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique TinyID application credential");
    }

    /**
     * 去除字符串首尾空白并按原顺序去重。
     *
     * @param values 原始字符串列表
     * @return 规范化后的不可变列表
     */
    private List<String> distinct(List<String> values) {
        return values.stream().map(String::trim).distinct().toList();
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

    /**
     * 校验待授权业务已完整存在于全部当前数据源。
     *
     * @param bizTypes 待校验的业务类型列表
     */
    private void requireCompleteBusinesses(List<String> bizTypes) {
        for (String bizType : bizTypes) {
            TinyIdBusinessAggregateBO business = getBusinessBO(bizType);
            if (business == null || !"COMPLETE".equals(business.getConsistencyStatus())) {
                throw new IllegalArgumentException("TinyID business is not complete: " + bizType);
            }
        }
    }

    /** 分页查询接入应用领域对象。 */
    IPage<TinyIdApplicationBO> listApplications(int page, int pageSize, String keywords) {
        Page<TinyIdApplicationBO> result =
            primary(() -> tokenMapper.selectApplicationPage(new Page<>(page, pageSize), keywords));
        result.getRecords().forEach(application -> application.setBizTypes(loadBizTypes(application.getToken())));
        return result;
    }

    /** 查询接入应用领域对象。 */
    TinyIdApplicationBO getApplicationBO(long appId) {
        return completeApplication(primary(() -> tokenMapper.selectApplicationById(appId)));
    }

    /** 按 Token 查询接入应用领域对象。 */
    TinyIdApplicationBO getApplicationByToken(String token) {
        return completeApplication(primary(() -> tokenMapper.selectApplicationByToken(token)));
    }

    /** 在全部数据源创建应用授权。 */
    void createApplicationOnAllDataSources(String token, String remark, List<String> bizTypes) {
        for (String key : dataSourceKeys()) {
            for (String bizType : bizTypes) {
                if (businessCount(key, bizType) == 0) {
                    throw new IllegalStateException("Business is not configured on every TinyID datasource");
                }
            }
        }
        List<String> completed = new ArrayList<>();
        try {
            for (String key : dataSourceKeys()) {
                completed.add(key);
                for (String bizType : bizTypes) {
                    on(key, () -> tokenMapper.insert(newToken(token, bizType, remark)));
                }
            }
        } catch (RuntimeException ex) {
            for (String key : completed) {
                try {
                    deleteToken(key, token);
                } catch (RuntimeException compensationFailure) {
                    ex.addSuppressed(compensationFailure);
                }
            }
            if (ex.getSuppressed().length > 0) {
                throw new IllegalStateException("TinyID cross-datasource compensation failed", ex);
            }
            throw ex;
        }
    }

    /** 在全部数据源更新应用备注。 */
    void updateRemarkOnAllDataSources(String token, String remark) {
        Map<String, String> previous = new LinkedHashMap<>();
        try {
            for (String key : dataSourceKeys()) {
                List<TinyIdToken> authorizations = findTokensByToken(key, token);
                if (authorizations.isEmpty()) {
                    throw new IllegalStateException(
                        "Application authorization is inconsistent across TinyID datasources");
                }
                previous.put(key, authorizations.get(0).getRemark());
                updateRemark(key, token, remark);
            }
        } catch (RuntimeException ex) {
            previous.forEach((key, value) -> {
                try {
                    updateRemark(key, token, value);
                } catch (RuntimeException compensationFailure) {
                    ex.addSuppressed(compensationFailure);
                }
            });
            if (ex.getSuppressed().length > 0) {
                throw new IllegalStateException("TinyID cross-datasource compensation failed", ex);
            }
            throw ex;
        }
    }

    /** 在全部数据源追加应用授权。 */
    List<AuthorizationInsert> addAuthorizationsOnAllDataSources(String token, String remark, List<String> bizTypes) {
        for (String key : dataSourceKeys()) {
            if (tokenCount(key, token, null) == 0) {
                throw new IllegalStateException("Application authorization is inconsistent across TinyID datasources");
            }
            for (String bizType : bizTypes) {
                if (businessCount(key, bizType) == 0) {
                    throw new IllegalStateException("Business is not configured on every TinyID datasource");
                }
            }
        }
        List<AuthorizationInsert> inserted = new ArrayList<>();
        try {
            for (String key : dataSourceKeys()) {
                for (String bizType : bizTypes) {
                    if (tokenCount(key, token, bizType) == 0) {
                        on(key, () -> tokenMapper.insert(newToken(token, bizType, remark)));
                        inserted.add(new AuthorizationInsert(key, bizType));
                    }
                }
            }
        } catch (RuntimeException ex) {
            compensateAuthorizationInserts(token, inserted, ex);
            throw ex;
        }
        return List.copyOf(inserted);
    }

    /** 判断 Token 是否存在。 */
    boolean tokenExists(String token) {
        return dataSourceKeys().stream().anyMatch(key -> tokenCount(key, token, null) > 0);
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

    /** 删除全部数据源中的接入应用。 */
    void deleteApplicationOnAllDataSources(String token) {
        Map<String, List<TinyIdToken>> snapshots = new LinkedHashMap<>();
        for (String key : dataSourceKeys()) {
            snapshots.put(key, findTokensByToken(key, token));
        }
        if (snapshots.values().stream().allMatch(List::isEmpty)) {
            throw new IllegalArgumentException("TinyID application does not exist");
        }
        List<String> completed = new ArrayList<>();
        try {
            for (String key : dataSourceKeys()) {
                inTransaction(key,
                    () -> tokenMapper.delete(Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getToken, token)));
                completed.add(key);
            }
        } catch (RuntimeException ex) {
            restoreApplicationSnapshots(completed, snapshots, ex);
            throw ex;
        }
    }

    /** 删除本次请求实际写入的授权。 */
    void deleteInsertedAuthorizations(String token, List<AuthorizationInsert> insertedAuthorizations) {
        compensateAuthorizationInserts(token, insertedAuthorizations, null);
    }

    /** 删除全部数据源中的未使用业务。 */
    void deleteBusinessOnAllDataSources(String bizType) {
        Map<String, TinyIdInfo> businessSnapshots = new LinkedHashMap<>();
        Map<String, List<TinyIdToken>> authorizationSnapshots = new LinkedHashMap<>();
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
            authorizationSnapshots.put(key, findTokensByBusiness(key, bizType));
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
                    tokenMapper.delete(Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getBizType, bizType));
                });
                completed.add(key);
            }
        } catch (RuntimeException ex) {
            restoreBusinessSnapshots(completed, businessSnapshots, authorizationSnapshots, ex);
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
     * 补全接入应用的业务授权列表。
     *
     * @param application 接入应用聚合对象
     * @return 补全后的接入应用
     */
    private TinyIdApplicationBO completeApplication(TinyIdApplicationBO application) {
        if (application == null) {
            throw new IllegalArgumentException("TinyID application does not exist");
        }
        application.setBizTypes(loadBizTypes(application.getToken()));
        return application;
    }

    /**
     * 加载首个物理数据源中的 Token 授权业务列表。
     *
     * @param token 应用接入 Token
     * @return 已排序的业务类型列表
     */
    private List<String> loadBizTypes(String token) {
        return primary(() -> tokenMapper.selectList(
            Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getToken, token).orderByAsc(TinyIdToken::getBizType)
                .select(TinyIdToken::getBizType))).stream().map(TinyIdToken::getBizType).toList();
    }

    /**
     * 创建应用授权实体。
     *
     * @param token   应用接入 Token
     * @param bizType 业务类型
     * @param remark  应用备注
     * @return 新授权实体
     */
    private TinyIdToken newToken(String token, String bizType, String remark) {
        TinyIdToken authorization = new TinyIdToken();
        Date now = new Date();
        authorization.setToken(token);
        authorization.setBizType(bizType);
        authorization.setRemark(remark);
        authorization.setCreateTime(now);
        authorization.setUpdateTime(now);
        return authorization;
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
     * 查询指定数据源上的授权记录数。
     *
     * @param key     数据源展示标识
     * @param token   应用接入 Token
     * @param bizType 可为空的业务类型
     * @return 授权记录数
     */
    private long tokenCount(String key, String token, String bizType) {
        return on(key, () -> tokenMapper.selectCount(
            Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getToken, token)
                .eq(bizType != null, TinyIdToken::getBizType, bizType)));
    }

    /**
     * 更新指定数据源中的应用备注。
     *
     * @param key    数据源展示标识
     * @param token  应用接入 Token
     * @param remark 新应用备注
     */
    private void updateRemark(String key, String token, String remark) {
        on(key, () -> tokenMapper.update(null, Wrappers.<TinyIdToken>lambdaUpdate().set(TinyIdToken::getRemark, remark)
            .set(TinyIdToken::getUpdateTime, new Date()).eq(TinyIdToken::getToken, token)));
    }

    /**
     * 删除指定数据源中的全部应用授权。
     *
     * @param key   数据源展示标识
     * @param token 应用接入 Token
     */
    private void deleteToken(String key, String token) {
        on(key, () -> tokenMapper.delete(Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getToken, token)));
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
     * 查询指定数据源中的应用授权快照。
     *
     * @param key   数据源展示标识
     * @param token 应用接入 Token
     * @return 应用授权实体列表
     */
    private List<TinyIdToken> findTokensByToken(String key, String token) {
        return on(key,
            () -> tokenMapper.selectList(Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getToken, token)));
    }

    /**
     * 查询指定数据源中的业务授权快照。
     *
     * @param key     数据源展示标识
     * @param bizType 业务类型
     * @return 业务授权实体列表
     */
    private List<TinyIdToken> findTokensByBusiness(String key, String bizType) {
        return on(key,
            () -> tokenMapper.selectList(Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getBizType, bizType)));
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
     * 恢复已删除数据源中的应用授权快照。
     *
     * @param completed 已完成删除的数据源
     * @param snapshots 各数据源删除前快照
     * @param cause     原始删除异常
     */
    private void restoreApplicationSnapshots(List<String> completed, Map<String, List<TinyIdToken>> snapshots,
        RuntimeException cause) {
        compensateDeletedData(completed, cause, "TinyID application deletion compensation failed",
            (key, ignored) -> restoreAuthorizations(snapshots.get(key)));
    }

    /**
     * 恢复已删除数据源中的业务及授权快照。
     *
     * @param completed      已完成删除的数据源
     * @param businesses     各数据源删除前的业务实体
     * @param authorizations 各数据源删除前的授权实体
     * @param cause          原始删除异常
     */
    private void restoreBusinessSnapshots(List<String> completed, Map<String, TinyIdInfo> businesses,
        Map<String, List<TinyIdToken>> authorizations, RuntimeException cause) {
        compensateDeletedData(completed, cause, "TinyID business deletion compensation failed", (key, ignored) -> {
            infoMapper.insert(businesses.get(key));
            restoreAuthorizations(authorizations.get(key));
        });
    }

    /**
     * 恢复当前数据源中的授权快照。
     *
     * @param snapshots 待恢复授权实体
     */
    private void restoreAuthorizations(List<TinyIdToken> snapshots) {
        for (TinyIdToken snapshot : snapshots) {
            tokenMapper.insert(snapshot);
        }
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
     * 精确回滚本次请求实际插入的 Token 授权。
     *
     * @param token    应用接入 Token
     * @param inserted 本次实际插入的授权记录
     * @param original 可为空的原始异常
     */
    private void compensateAuthorizationInserts(String token, List<AuthorizationInsert> inserted,
        RuntimeException original) {
        RuntimeException failure = null;
        List<AuthorizationInsert> changes = new ArrayList<>(inserted);
        Collections.reverse(changes);
        for (AuthorizationInsert change : changes) {
            try {
                on(change.dataSourceKey(), () -> tokenMapper.delete(
                    Wrappers.<TinyIdToken>lambdaQuery().eq(TinyIdToken::getToken, token)
                        .eq(TinyIdToken::getBizType, change.bizType())));
            } catch (RuntimeException ex) {
                if (failure == null) {
                    failure = new IllegalStateException("TinyID authorization compensation failed", ex);
                    if (original != null) {
                        failure.addSuppressed(original);
                    }
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
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

    /**
     * 标识本次管理操作实际新增的一条授权记录。
     *
     * @param dataSourceKey 物理数据源标识
     * @param bizType       业务类型
     */
    record AuthorizationInsert(String dataSourceKey, String bizType) {
    }
}

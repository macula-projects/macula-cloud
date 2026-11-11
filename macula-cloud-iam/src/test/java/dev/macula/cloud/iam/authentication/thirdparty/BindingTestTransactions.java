/*
 * Copyright (c) 2026 Macula
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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.authentication.thirdparty.directory.ProviderDirectoryPolicyService;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单测执行真实事务同步生命周期；数据库回滚与锁行为仍由隔离MySQL集成测试证明。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class BindingTestTransactions extends AbstractPlatformTransactionManager {
    /**
     * 显式提供目录替身，保留生产目录调用并返回同一已核验身份。
     */
    public static ProviderDirectoryPolicyService directory() {
        var directory = mock(ProviderDirectoryPolicyService.class);
        when(directory.enrich(any(), any())).thenAnswer(call -> call.getArgument(0));
        return directory;
    }

    /**
     * 每次事务使用独立测试资源。
     */
    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    /**
     * 无数据库资源，事务同步仍由Spring执行。
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    /**
     * 提交后Spring触发afterCommit回调。
     */
    @Override
    protected void doCommit(DefaultTransactionStatus status) {
    }

    /**
     * 回滚后Spring触发afterCompletion回调。
     */
    @Override
    protected void doRollback(DefaultTransactionStatus status) {
    }
}

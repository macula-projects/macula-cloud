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

create table sys_application_tenant
(
    id               int auto_increment
        primary key,
    tenant_id        bigint null comment '租户id',
    application_name varchar(55)                                              not null,
    code             varchar(55)                                              not null comment '应用编码',
    ak               varchar(55) null,
    sk               varchar(55) null,
    homepage         varchar(55) null,
    manager          varchar(55)                                              not null comment '负责人',
    maintainer       varchar(255) null comment '维护人',
    mobile           varchar(20)                                              not null comment '联系方式',
    access_path      varchar(255) null comment '可访问路径',
    create_by        varchar(50) collate utf8mb4_general_ci default '*SYSADM' not null comment '创建人',
    create_time      datetime                                                 not null comment '创建时间',
    last_update_by   varchar(50) collate utf8mb4_general_ci default '*SYSADM' not null comment '更新人',
    last_update_time datetime                                                 not null comment '更新时间'
) collate = utf8mb4_bin;

create table sys_dept
(
    id               bigint auto_increment comment '主键'
        primary key,
    name             varchar(64)  default '' null comment '部门名称',
    parent_id        bigint       default 0 null comment '父节点id',
    tree_path        varchar(255) default '' null comment '父节点id路径',
    sort             int          default 0 null comment '显示顺序',
    status           tinyint      default 1 null comment '状态(1:正常;0:禁用)',
    deleted          tinyint      default 0 null comment '逻辑删除标识(1:已删除;0:未删除)',
    create_by        varchar(50)  default '*SYSADM' not null comment '创建人',
    create_time      datetime                       not null comment '创建时间',
    last_update_by   varchar(50)  default '*SYSADM' not null comment '更新人',
    last_update_time datetime                       not null comment '更新时间'
) comment '部门表' collate = utf8mb4_general_ci;

create table sys_dict_item_tenant
(
    id               bigint auto_increment comment '主键'
        primary key,
    tenant_id        bigint null comment '租户id',
    type_code        varchar(64) null comment '字典类型编码',
    name             varchar(50)  default '' null comment '字典项名称',
    value            varchar(50)  default '' null comment '字典项值',
    sort             int          default 0 null comment '排序',
    status           tinyint      default 0 null comment '状态(1:正常;0:禁用)',
    defaulted        tinyint      default 0 null comment '是否默认(1:是;0:否)',
    remark           varchar(255) default '' null comment '备注',
    create_by        varchar(50)  default '*SYSADM' not null comment '创建人',
    create_time      datetime                       not null comment '创建时间',
    last_update_by   varchar(50)  default '*SYSADM' not null comment '更新人',
    last_update_time datetime                       not null comment '更新时间'
) comment '字典数据表' collate = utf8mb4_general_ci;

create table sys_dict_type_tenant
(
    id               bigint auto_increment comment '主键 '
        primary key,
    tenant_id        bigint null comment '租户id',
    name             varchar(50) default '' null comment '类型名称',
    code             varchar(50) default '' null comment '类型编码',
    status           tinyint(1) default 0 null comment '状态(0:正常;1:禁用)',
    remark           varchar(255) null comment '备注',
    create_by        varchar(50) default '*SYSADM' not null comment '创建人',
    create_time      datetime                      not null comment '创建时间',
    last_update_by   varchar(50) default '*SYSADM' not null comment '更新人',
    last_update_time datetime                      not null comment '更新时间',
    constraint type_code
        unique (code)
) comment '字典类型表' collate = utf8mb4_general_ci;

create table sys_log
(
    id                bigint auto_increment comment '主键'
        primary key,
    op_ip             varchar(55) null comment '操作IP',
    op_url            varchar(255) null comment '操作Url',
    op_name           varchar(55) null comment '操作人',
    op_title          varchar(255) null comment '操作标题',
    op_method         varchar(255) null comment '请求方法',
    op_request_method varchar(55) null comment '请求方式',
    op_param          varchar(255) null comment '请求参数',
    op_status         int null comment '操作状态',
    error_msg         varchar(255) null comment '错误信息',
    json_result       longtext null comment '响应结果信息',
    create_by         varchar(50) default '*SYSADM' not null comment '创建人',
    create_time       datetime                      not null comment '创建时间',
    last_update_by    varchar(50) default '*SYSADM' not null comment '更新人',
    last_update_time  datetime                      not null comment '更新时间'
) collate = utf8mb4_general_ci;

create table sys_menu_tenant
(
    id               bigint auto_increment
        primary key,
    tenant_id        bigint null comment '租户id',
    parent_id        bigint                         not null comment '父菜单ID',
    type             tinyint                        not null comment '菜单类型(1：菜单；2：目录；3：外链；4：iFrame；5：按钮)',
    name             varchar(64)  default '' null comment '菜单名称',
    path             varchar(128) default '' null comment '路由路径(浏览器地址栏路径)',
    component        varchar(128) null comment '组件路径(vue页面完整路径，省略.vue后缀)',
    perm             varchar(50) null comment '按钮权限标识',
    icon             varchar(64)  default '' null comment '菜单图标',
    sort             int          default 0 null comment '排序',
    visible          tinyint(1) default 1 null comment '状态(0:禁用;1:开启)',
    redirect         varchar(128) default '' null comment '跳转路径',
    create_by        varchar(50)  default '*SYSADM' not null comment '创建人',
    create_time      datetime                       not null comment '创建时间',
    last_update_by   varchar(50)  default '*SYSADM' not null comment '更新人',
    last_update_time datetime                       not null comment '更新时间',
    full_page        tinyint(1) null comment '整页路径'
) comment '菜单管理' collate = utf8mb4_general_ci;

create table sys_oauth2_client
(
    id                              bigint auto_increment comment 'ID'
        primary key,
    client_id                       varchar(100)                          not null comment 'oauth2客户端id',
    client_id_issued_at             timestamp   default CURRENT_TIMESTAMP not null comment '客户端创建时间',
    client_secret                   varchar(200)                          not null comment '客户端密码',
    client_secret_expires_at        timestamp null comment '客户端密码过期时间',
    client_name                     varchar(200)                          not null comment '客户端名称',
    client_authentication_methods   varchar(255)                          not null comment '客户端认证方式',
    authorization_grant_types       varchar(255)                          not null comment '客户端授权方式',
    redirect_uris                   varchar(255)                          not null comment '客户端允许重定向的uri',
    scopes                          varchar(255)                          not null comment '客户端允许的scope 来自role表',
    require_proof_key               tinyint(1) default 0 null comment '客户端是否需要证明密钥',
    require_authorization_consent   tinyint(1) default 0 null comment '客户端是否需要授权确认页面',
    jwk_set_url                     varchar(255) null comment 'jwkSet url',
    signing_algorithm               varchar(255) null comment '支持的签名算法',
    authorization_code_time_to_live bigint null comment 'authorization_code 有效时间(秒)',
    access_token_time_to_live       bigint null comment 'access_token 有效时间(秒)',
    token_format                    varchar(255) null comment 'token 格式  jwt、opaque',
    reuse_refresh_tokens            tinyint(1) default 1 null comment '是否重用 refresh_token',
    refresh_token_time_to_live      bigint null comment 'refresh_token 有效时间(秒)',
    id_token_signature_algorithm    varchar(255) null comment 'oidc id_token 签名算法',
    user_type                       varchar(25)                           not null comment '用户类型(member,employee)',
    create_by                       varchar(50) default '*SYSADM'         not null comment '创建人',
    create_time                     datetime                              not null comment '创建时间',
    last_update_by                  varchar(50) default '*SYSADM'         not null comment '最后更新人',
    last_update_time                datetime                              not null comment '最后更新时间',
    constraint UK_drwlno5wbex09l0acnnwecp7r
        unique (client_id)
);

create table sys_permission
(
    id               bigint auto_increment
        primary key,
    name             varchar(64) null,
    menu_id          int null,
    url_perm         varchar(128) null,
    create_by        varchar(50) null,
    create_time      datetime null,
    last_update_by   varchar(50) null,
    last_update_time datetime null,
    constraint urlPerm_unique
        unique (url_perm)
) collate = utf8mb4_general_ci;

create table sys_role
(
    id               bigint auto_increment
        primary key,
    name             varchar(64) default ''        not null comment '角色名称',
    code             varchar(32) null comment '角色编码',
    sort             int null comment '显示顺序',
    status           tinyint     default 1 null comment '角色状态(1-正常；0-停用)',
    deleted          tinyint     default 0         not null comment '逻辑删除标识(0-未删除；1-已删除)',
    data_scope       tinyint     default 0 null comment '数据权限(1-全部可见；2-本人可见；3-所在部门可见；4-所在部门及子级可见；5-选择部门可见；6-自定义)',
    create_by        varchar(50) default '*SYSADM' not null comment '创建人',
    create_time      datetime                      not null comment '创建时间',
    last_update_by   varchar(50) default '*SYSADM' not null comment '更新人',
    last_update_time datetime                      not null comment '更新时间',
    constraint name
        unique (name)
) comment '角色表' collate = utf8mb4_general_ci;

create table sys_role_menu_tenant
(
    role_id   bigint not null comment '角色ID',
    menu_id   bigint not null comment '菜单ID',
    tenant_id bigint null comment '租户id',
    primary key (role_id, menu_id)
) comment '角色和菜单关联表' collate = utf8mb4_general_ci;

create table sys_role_permission_tenant
(
    role_id       int not null,
    permission_id int not null,
    tenant_id     bigint null comment '租户id',
    primary key (permission_id, role_id)
) collate = utf8mb4_general_ci;

create table sys_tenant_info
(
    id               int auto_increment
        primary key,
    name             varchar(50) null,
    create_by        varchar(50) collate utf8mb4_general_ci default '*SYSADM' not null comment '创建人',
    create_time      datetime                                                 not null comment '创建时间',
    last_update_by   varchar(50) collate utf8mb4_general_ci default '*SYSADM' not null comment '更新人',
    last_update_time datetime                                                 not null comment '更新时间',
    code             varchar(50) null,
    description      varchar(100) null,
    supervisor       varchar(50) null comment '绑定了多个负责人，暂时不用，后续删除'
) collate = utf8mb4_bin;

create table sys_tenant_user
(
    user_id   bigint not null comment '用户id',
    tenant_id bigint not null comment '租户id'
) collate = utf8mb4_bin;

create table sys_user
(
    id               int auto_increment
        primary key,
    username         varchar(64) null comment '用户名',
    nickname         varchar(64) null comment '昵称',
    gender           tinyint(1) default 1 null comment '性别((1:男;2:女))',
    password         varchar(100) null comment '密码',
    dept_id          int null comment '部门ID',
    avatar           varchar(255) default '' null comment '用户头像',
    mobile           varchar(20) null comment '联系方式',
    status           tinyint(1) default 1 null comment '用户状态((1:正常;0:禁用))',
    email            varchar(128) null comment '用户邮箱',
    deleted          tinyint(1) default 0 null comment '逻辑删除标识(0:未删除;1:已删除)',
    create_by        varchar(50)  default '*SYSADM' not null comment '创建人',
    create_time      datetime                       not null comment '创建时间',
    last_update_by   varchar(50)  default '*SYSADM' not null comment '更新人',
    last_update_time datetime                       not null comment '更新时间'
) comment '用户信息表' collate = utf8mb4_general_ci;

create index login_name
    on sys_user (username);

create table sys_user_role
(
    user_id int not null comment '用户ID',
    role_id int not null comment '角色ID',
    primary key (user_id, role_id)
) comment '用户和角色关联表' collate = utf8mb4_general_ci;


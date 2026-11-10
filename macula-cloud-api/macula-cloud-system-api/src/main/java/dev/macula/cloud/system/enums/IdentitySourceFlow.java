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
package dev.macula.cloud.system.enums;

import java.util.Arrays;
import java.util.Set;

/**
 * 已核实的固定兑换合同；来源与flow必须匹配，不能在失败后试另一版本。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentitySourceFlow {
    WECHAT_WEBSITE_V1("WECHAT_WEBSITE_V1", Set.of("WECHAT_WEBSITE"), true, false),
    WECHAT_OFFICIAL_V1("WECHAT_OFFICIAL_V1", Set.of("WECHAT_OFFICIAL_ACCOUNT"), true, false),
    WECHAT_OPEN_APP_V1("WECHAT_OPEN_APP_V1", Set.of("WECHAT_MOBILE_APP"), true, false),
    WECHAT_MINIAPP_V1("WECHAT_MINIAPP_V1", Set.of("WECHAT_MINIAPP"), true, false),
    WECOM_WEB_V1("WECOM_WEB_V1", Set.of("WECOM_WEB"), true, false),
    WECOM_SDK_V1("WECOM_SDK_V1", Set.of("WECOM_SDK"), true, false),
    WECOM_MINIAPP_V1("WECOM_MINIAPP_V1", Set.of("WECOM_MINIAPP"), true, false),
    FEISHU_OAUTH_V3("FEISHU_OAUTH_V3", Set.of("FEISHU_WEB"), true, false),
    FEISHU_APPLINK_V3("FEISHU_APPLINK_V3", Set.of("FEISHU_APPLINK"), true, true),
    FEISHU_WEBVIEW_V1("FEISHU_WEBVIEW_V1", Set.of("FEISHU_WEBVIEW"), false, false),
    FEISHU_WEBVIEW_V3("FEISHU_WEBVIEW_V3", Set.of("FEISHU_WEBVIEW"), true, false),
    DINGTALK_OAUTH_V1("DINGTALK_OAUTH_V1", Set.of("DINGTALK_WEB", "DINGTALK_SDK"), true, false),
    DINGTALK_CORP_H5_V1("DINGTALK_CORP_H5_V1", Set.of("DINGTALK_WEBVIEW"), true, false),
    GITHUB_OAUTH_V1("GITHUB_OAUTH_V1", Set.of("GITHUB_OAUTH"), true, false),
    ALIYUN_MOBILE_V1("ALIYUN_MOBILE_V1", Set.of("ALIYUN_MOBILE"), true, false);

    private final String value;
    private final Set<String> sourceTypes;
    private final boolean implemented;
    private final boolean providerPkce;

    /** 能力目录和运行时使用同一白名单。 */
    IdentitySourceFlow(String value, Set<String> sourceTypes, boolean implemented, boolean providerPkce) {
        this.value = value;
        this.sourceTypes = sourceTypes;
        this.implemented = implemented;
        this.providerPkce = providerPkce;
    }

    /** 返回稳定配置值；此枚举的值为显式版本化合同标识。 */
    public String value() { return value; }

    /** 来源类型已经限定应用/企业凭据，不能只按厂商匹配。 */
    public boolean supports(IdentitySourceType source) { return sourceTypes.contains(source.value()); }

    /** 未核实客户端合同的flow不能因有界面选项就宣称可用。 */
    public boolean implemented() { return implemented; }

    /** 此标记不是IAM PKCE，只描述对应平台所需的证明。 */
    public boolean providerPkce() { return providerPkce; }

    /** 仅无歧义且非飞书版本化来源可省略显式选择。 */
    public static IdentitySourceFlow resolve(IdentitySourceType source, String value) {
        IdentitySourceFlow flow = declared(source, value);
        if (!flow.implemented()) {
            throw new IllegalArgumentException("身份源flow尚未核实");
        }
        return flow;
    }

    /** 新建仅能默认选择唯一已核实的适用合同，不在失败后换接口。 */
    public static IdentitySourceFlow defaultFor(IdentitySourceType source) {
        var candidates = Arrays.stream(values()).filter(item -> item.supports(source) && item.implemented()).toList();
        if (candidates.size() != 1) { throw new IllegalArgumentException("该接入方式尚无唯一已核实的接口合同"); }
        return candidates.get(0);
    }

    /** 未核实记录仅可禁用保存，仍须匹配明确的来源类型。 */
    public static IdentitySourceFlow declared(IdentitySourceType source, String value) {
        IdentitySourceFlow flow = value == null || value.isBlank()
            ? Arrays.stream(values()).filter(item -> item.supports(source))
                .filter(item -> !source.providerType().equals("FEISHU")).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("请选择并核实身份源flow"))
            : Arrays.stream(values()).filter(item -> item.value().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("身份源flow无效"));
        if (!flow.supports(source)) {
            throw new IllegalArgumentException("身份源flow尚未核实或不适用");
        }
        return flow;
    }
}

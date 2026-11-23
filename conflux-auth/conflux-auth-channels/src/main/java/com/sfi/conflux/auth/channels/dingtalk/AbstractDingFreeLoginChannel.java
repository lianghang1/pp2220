package com.sfi.conflux.auth.channels.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉「客户端内免登」系公共基类（企业内部 H5 / 第三方企业 / 小程序 / 管理后台）。
 *
 * <p>免登本质是容器内 JSAPI 拿临时 code，与标准 OAuth 跳转型不同：
 * ① 授权入口不产生 URL，返回前端需调用的 JSAPI 参数（{@link #jsApiAction()}）；
 * ② 平台不回传 state，故 {@link #requireStateCheck()} 关闭；
 * ③ 后端统一归到 code → userinfo 交换层，由各子类按官方端点实现
 * （企业内 H5 走 gettoken+getuserinfo；小程序走 sns/getuserinfo_bycode…）。</p>
 *
 * @author lianghang
 */
abstract class AbstractDingFreeLoginChannel extends AbstractAuthChannel {

    protected final DingHttpClient http;

    AbstractDingFreeLoginChannel(DingHttpClient http) {
        this.http = http;
    }

    /** 前端需要调用的 JSAPI 方法名，如 dd.getAuthCode / dd.requestAuthCode */
    protected abstract String jsApiAction();

    @Override
    public boolean requireStateCheck() {
        return false;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        Map<String, Object> jsApi = new LinkedHashMap<String, Object>();
        jsApi.put("action", jsApiAction());
        jsApi.put("clientId", config.getClientId());
        return new AuthorizeResult().setJsApiParams(jsApi);
    }

    /** 企业内部/第三方企业免登通用：用 clientId+clientSecret 换应用 access_token（oapi gettoken） */
    protected String appAccessToken(AuthConfig config) {
        String url = "https://oapi.dingtalk.com/gettoken"
                + "?corpid=" + DingSupport.enc(config.getClientId())
                + "&corpsecret=" + DingSupport.enc(config.getClientSecret());
        JsonNode node = JsonUtils.readTree(http.get(url, null));
        if (node == null) {
            return null;
        }
        if (node.has("errcode") && !"0".equals(AbstractDingOAuth2Channel.text(node, "errcode"))) {
            return null;
        }
        return AbstractDingOAuth2Channel.text(node, "access_token");
    }
}

package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpOAuth2Service;
import me.chanjar.weixin.cp.bean.WxCpOauth2UserInfo;

/**
 * 企业微信第三方二维码扫码登录（服务商代开发）：channelCode = wechat_wecom_third。
 *
 * <p>授权入口同 qrConnect 端点，但 appid 是服务商 CorpID、agentid 是授权方应用，
 * 服务商套件等特有参数通过 config.attr / AuthorizeOption.extraParams 传入（不写死参数名）。
 * 身份交换用 getAuthUserInfo，返回 externalUserId 等第三方身份字段。</p>
 *
 * @author lianghang
 */
public class WecomThirdAuthChannel extends AbstractWecomChannel {

    public static final String CHANNEL_CODE = "wechat_wecom_third";
    private static final String AUTHORIZE_URL = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    public WecomThirdAuthChannel() {
        this(new WxClientHolder());
    }

    WecomThirdAuthChannel(WxClientHolder clients) {
        super(clients);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        StringBuilder url = new StringBuilder(AUTHORIZE_URL)
                .append("?appid=").append(WxSupport.enc(config.getClientId()));
        Integer agentId = WxSupport.agentId(config);
        if (agentId != null) {
            url.append("&agentid=").append(agentId);
        }
        url.append("&redirect_uri=").append(WxSupport.enc(config.getRedirectUri()))
                .append("&state=").append(WxSupport.enc(state));
        // 服务商场景的 suite_id / 第三方特有参数由调用方通过 extraParams 补充
        String result = WxSupport.appendExtra(url.toString(), option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(result);
    }

    @Override
    protected WxCpOauth2UserInfo fetchUserInfo(WxCpOAuth2Service oauth2, AuthConfig config, String code)
            throws WxErrorException {
        return oauth2.getAuthUserInfo(code);
    }
}

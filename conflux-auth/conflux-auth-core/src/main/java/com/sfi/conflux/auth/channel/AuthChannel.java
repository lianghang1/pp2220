package com.sfi.conflux.auth.channel;

import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.config.AuthConfig;

/**
 * 渠道 SPI：新增平台的唯一扩展点。
 *
 * <p>框架只认三个核心行为方法（channelCode / authorize / exchange）与两个可选 default 钩子，
 * provider 内部用什么协议、什么链路、什么参数名完全自由。内建渠道一律继承
 * {@link AbstractAuthChannel}。</p>
 *
 * @author lianghang
 */
public interface AuthChannel {

    /** 该 provider 负责的形态，如 wechat_open / alipay_mini */
    String channelCode();

    /** 生成授权入口。state 由基类生成并透传进来，实现只管按本平台参数名组装 */
    AuthorizeResult authorize(AuthConfig config, AuthorizeOption option);

    /** 用票据换统一身份（脏活在这层，验签也在这层委托 SDK 完成） */
    AuthResult<AuthUser> exchange(AuthConfig config, AuthCallbackRequest request);

    /**
     * 【可选覆盖】从回调里解析出 state 值。默认读名为 "state" 的参数；
     * 非主流平台若 state 藏在别的参数名 / header / body 里，覆盖此方法即可，框架与其他 provider 零改动。
     */
    default String resolveState(AuthCallbackRequest request) {
        return request.param("state");
    }

    /**
     * 【可选覆盖】是否需要 state 校验。默认 true；
     * 某些免登形态平台根本不回传 state，覆盖返回 false 即可。
     */
    default boolean requireStateCheck() {
        return true;
    }
}

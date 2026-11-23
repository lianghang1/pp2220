package com.sfi.conflux.auth.channel;

import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.support.StateCache;
import com.sfi.conflux.auth.support.UuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 渠道模板基类（借鉴 JustAuth AuthDefaultRequest）：框架把通用流程写死，provider 只填两个钩子。
 *
 * <p>职责边界：门面只路由；本基类只管 state 链路（生成 / 写缓存 / 取出即删）+ 异常包装，
 * 不碰验签；验签与身份交换由 provider 的 {@link #doExchange} 委托底层 SDK 完成。</p>
 *
 * <p>{@link #authorize} 与 {@link #exchange} 为 final，provider 无法覆盖框架流程，
 * 只能实现 {@link #doBuildAuthorize} 与 {@link #doExchange}，避免 24 个渠道各自重复 state 链路。</p>
 *
 * @author lianghang
 */
public abstract class AbstractAuthChannel implements AuthChannel {

    private static final Logger log = LoggerFactory.getLogger(AbstractAuthChannel.class);

    /** 由框架在注册渠道时注入（即 builder 的 StateCache，未配置则为默认内存实现） */
    protected StateCache stateCache;

    /** 框架注入 state 缓存 */
    public final void setStateCache(StateCache stateCache) {
        this.stateCache = stateCache;
    }

    /** 基类固化：生成 state + 写缓存，再调组装钩子。门面不参与。 */
    @Override
    public final AuthorizeResult authorize(AuthConfig config, AuthorizeOption option) {
        String state = UuidUtils.random();
        int expire = (option == null || option.getExpireSeconds() == null) ? 300 : option.getExpireSeconds();
        Map<String, String> extra = option == null ? null : option.getStateExtra();
        stateCache.save(state, extra, expire);
        return doBuildAuthorize(config, state, option);
    }

    /** 基类固化：state 校验（参数名由 provider 声明、取出即删）+ 统一异常包装。不做验签。 */
    @Override
    public final AuthResult<AuthUser> exchange(AuthConfig config, AuthCallbackRequest request) {
        if (requireStateCheck()) {
            String state = resolveState(request);
            if (state == null || stateCache.loadAndRemove(state) == null) {
                return AuthResult.fail("ILLEGAL_STATE", "state 缺失或已使用");
            }
        }
        try {
            return doExchange(config, request);
        } catch (Exception e) {
            log.warn("登录身份交换失败, channelCode={}", channelCode(), e);
            return AuthResult.fail("EXCHANGE_ERROR", e.getMessage());
        }
    }

    /** provider 钩子：只管按本平台参数名组装授权入口（把 state 塞进平台需要的参数位） */
    protected abstract AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option);

    /** provider 钩子：按本平台参数名取票据、调官方 SDK 验签并换身份、归一为 AuthUser */
    protected abstract AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request);
}

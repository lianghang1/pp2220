package com.sfi.conflux.auth.api;

import java.util.Map;

/**
 * 统一身份：所有平台 userinfo 归一到本模型，上层调用方只认这一种类型。
 *
 * <p>小程序形态（jscode2session / my.getAuthCode）可能只拿到 openId、没有 unionId /
 * 昵称 / 头像，这些字段允许为空；拿不到的原始字段一律保留在 {@link #rawUserInfo}。</p>
 *
 * @author lianghang
 */
public class AuthUser {

    private String channelCode;
    private String openId;
    private String unionId;
    private String username;
    private String avatar;
    private String email;
    private String gender;
    private Map<String, Object> rawUserInfo;

    public String getChannelCode() {
        return channelCode;
    }

    public AuthUser setChannelCode(String channelCode) {
        this.channelCode = channelCode;
        return this;
    }

    public String getOpenId() {
        return openId;
    }

    public AuthUser setOpenId(String openId) {
        this.openId = openId;
        return this;
    }

    public String getUnionId() {
        return unionId;
    }

    public AuthUser setUnionId(String unionId) {
        this.unionId = unionId;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public AuthUser setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getAvatar() {
        return avatar;
    }

    public AuthUser setAvatar(String avatar) {
        this.avatar = avatar;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public AuthUser setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getGender() {
        return gender;
    }

    public AuthUser setGender(String gender) {
        this.gender = gender;
        return this;
    }

    public Map<String, Object> getRawUserInfo() {
        return rawUserInfo;
    }

    public AuthUser setRawUserInfo(Map<String, Object> rawUserInfo) {
        this.rawUserInfo = rawUserInfo;
        return this;
    }
}

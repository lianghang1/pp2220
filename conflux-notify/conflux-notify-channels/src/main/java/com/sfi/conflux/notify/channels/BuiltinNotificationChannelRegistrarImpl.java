package com.sfi.conflux.notify.channels;

import com.sfi.conflux.notify.channel.NotificationChannel;
import com.sfi.conflux.notify.channels.mini.WechatMiniChannel;
import com.sfi.conflux.notify.channels.mp.WechatMpChannel;
import com.sfi.conflux.notify.channels.wecom.WeComChannel;
import com.sfi.conflux.notify.spi.BuiltinNotificationChannelRegistrar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 内置 IM 通知渠道注册实现：微信三大平台（公众号/小程序/企业微信）。
 *
 * @author lianghang
 */
public class BuiltinNotificationChannelRegistrarImpl implements BuiltinNotificationChannelRegistrar {

    @Override
    public List<NotificationChannel> builtinChannels() {
        return new ArrayList<NotificationChannel>(Arrays.asList(
                new WechatMpChannel(),
                new WechatMiniChannel(),
                new WeComChannel()));
    }
}

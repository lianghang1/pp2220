package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.api.BillSheet;

import java.time.LocalDate;

/**
 * 渠道账单能力：下载并解析指定日期的交易账单，供对账引擎比对。
 *
 * <p>实现方负责渠道账单的获取（文件下载/接口拉取）、解析与条目归一化；
 * 当日账单未生成等业务性缺失返回空条目集而非抛错。</p>
 *
 * @author lianghang
 */
public interface BillHandler {

    /**
     * 拉取指定应用、指定日期的渠道账单。
     */
    BillSheet fetchBill(String appId, LocalDate billDate, ChannelConfig config);
}

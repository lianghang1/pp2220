package io.github.lianghang1.conflux.pay.example.store;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 网关记录表：演示用方的通用记录存储——框架模型对象以 JSON 落库，
 * 六类 Store SPI 共用一张表。生产项目通常按模型拆表建索引，此处求简。
 *
 * @author lianghang
 */
@Getter
@Setter
@Entity
@Table(name = "gateway_record")
public class GatewayRecordEntity {

    /** 记录主键：网关单号 / 通知记录号 / 差错记录号 */
    @Id
    @Column(name = "record_id", length = 64)
    private String recordId;

    /** 记录类型：PAYMENT / REFUND / TRANSFER / PROFIT_SHARE / NOTIFY / RECON */
    @Column(name = "record_type", length = 32, nullable = false)
    private String recordType;

    /** 幂等键（appId + 业务单号），唯一场景按类型保证 */
    @Column(name = "idempotent_key", length = 128)
    private String idempotentKey;

    /** 渠道编码（对账/渠道单号查询用） */
    @Column(name = "channel_code", length = 32)
    private String channelCode;

    /** 渠道侧单号 */
    @Column(name = "channel_order_no", length = 128)
    private String channelOrderNo;

    /** 时间锚点（订单=创建时间，通知=下次投递时间，差错=创建时间） */
    @Column(name = "anchor_time")
    private LocalDateTime anchorTime;

    /** 模型对象 JSON */
    @Column(name = "content_json", length = 4096, nullable = false)
    private String contentJson;
}

package io.github.lianghang1.conflux.pay.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 分账接收方：一条分账单内的单个接收方明细。
 *
 * <p>{@code state} 由渠道回填单接收方的执行结果
 * （成功/失败语义与各渠道一致，渠道包内负责翻译）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShareReceiver implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收方类型：MERCHANT（商户号）/ PERSONAL（个人账户），渠道语义内自行校验 */
    private String receiverType;
    /** 接收方账户（商户号或个人账号） */
    private String receiverAccount;
    /** 分账金额（分） */
    private Long amount;
    /** 与发起方的关系类型（渠道语义，可空） */
    private String relationType;
    /** 分账描述 */
    private String description;
    /** 单接收方执行结果状态（渠道回填，可空） */
    private String state;
    /** 单接收方失败原因（渠道回填，可空） */
    private String errorMsg;
}

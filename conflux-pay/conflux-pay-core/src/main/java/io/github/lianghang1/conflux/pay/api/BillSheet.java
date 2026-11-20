package io.github.lianghang1.conflux.pay.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 渠道账单：指定应用、渠道、日期的流水集合。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class BillSheet implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 渠道编码 */
    private String channelCode;
    /** 账单日期 */
    private LocalDate billDate;
    /** 账单条目 */
    private List<BillEntry> entries;
}

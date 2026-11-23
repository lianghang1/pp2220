package com.sfi.conflux.pay.channels.wechat;

import com.github.binarywang.wxpay.bean.request.WxPayDownloadBillRequest;
import com.github.binarywang.wxpay.service.WxPayService;
import com.sfi.conflux.pay.api.BillEntry;
import com.sfi.conflux.pay.api.BillSheet;
import com.sfi.conflux.pay.channel.BillHandler;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.BillEntryType;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 微信交易账单处理器：V2 对账单接口（downloadRawBill，GZIP + CSV，
 * UTF-8 编码）按表头列名归一化为账单条目。
 *
 * <p>金额列以「元（两位小数）」表达，折算为分；流水类型按「交易状态」列判定
 * （含"退款"记为退款流水）；时间取「交易时间」，解析失败回退账单日期零点。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatBillHandler implements BillHandler {

    private static final DateTimeFormatter BILL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WechatClientHolder holder;

    public WechatBillHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public BillSheet fetchBill(String configId, LocalDate billDate, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        WxPayDownloadBillRequest request = new WxPayDownloadBillRequest();
        request.setBillDate(billDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        request.setBillType("ALL");
        request.setTarType("GZIP");
        String csv = wrap("账单下载", () -> service.downloadRawBill(request));
        List<BillEntry> entries = parseCsv(csv, billDate);
        return new BillSheet()
                .setConfigId(configId)
                .setChannelCode("wechat")
                .setBillDate(billDate)
                .setEntries(entries);
    }

    private List<BillEntry> parseCsv(String csv, LocalDate billDate) {
        List<BillEntry> entries = new ArrayList<BillEntry>();
        Map<String, Integer> columnIndex = new HashMap<String, Integer>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new ByteArrayInputStream(
                        csv.getBytes(Charset.forName("UTF-8"))), Charset.forName("UTF-8")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] cells = line.split(",", -1);
                if (columnIndex.isEmpty()) {
                    for (int i = 0; i < cells.length; i++) {
                        columnIndex.put(cells[i].trim().replace("`", ""), i);
                    }
                    continue;
                }
                if (cells.length < columnIndex.size() || line.contains("总交易单数")) {
                    continue;
                }
                entries.add(toEntry(cells, columnIndex, billDate));
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信账单解析失败: {}", e.getMessage(), e);
        }
        return entries;
    }

    private BillEntry toEntry(String[] cells, Map<String, Integer> columnIndex, LocalDate billDate) {
        BillEntry entry = new BillEntry()
                .setChannelOrderNo(cell(cells, columnIndex, "微信支付单号"))
                .setBizOrderNo(cell(cells, columnIndex, "商户订单号"))
                .setRawState(cell(cells, columnIndex, "交易状态"));
        String state = entry.getRawState();
        entry.setSuccess(state != null && (state.contains("支付成功") || state.contains("充值成功")));
        entry.setEntryType(state != null && state.contains("退款")
                ? BillEntryType.REFUND : BillEntryType.PAYMENT);
        String yuan = cell(cells, columnIndex, "付款金额");
        if (StringUtils.isBlank(yuan)) {
            yuan = cell(cells, columnIndex, "金额");
        }
        if (StringUtils.isNotBlank(yuan)) {
            entry.setAmount(com.sfi.conflux.common.util.AmountUtil.toFen(
                    new java.math.BigDecimal(yuan.replace("`", ""))));
        }
        try {
            entry.setOccurTime(LocalDateTime.parse(cell(cells, columnIndex, "交易时间"), BILL_TIME));
        } catch (Exception e) {
            entry.setOccurTime(billDate.atStartOfDay());
        }
        return entry;
    }

    private String cell(String[] cells, Map<String, Integer> columnIndex, String column) {
        Integer position = columnIndex.get(column);
        return position == null || position >= cells.length ? null : cells[position].trim().replace("`", "");
    }

    private <T> T wrap(String action, WechatCall<T> call) {
        try {
            return call.call();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}

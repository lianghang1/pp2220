package io.github.lianghang1.paygateway.channels.wechat;

import com.wechat.pay.java.service.billdownload.model.BillType;
import com.wechat.pay.java.service.billdownload.model.GetTradeBillRequest;
import com.wechat.pay.java.service.billdownload.model.QueryBillEntity;
import com.wechat.pay.java.service.billdownload.model.TarType;
import io.github.lianghang1.paygateway.api.BillEntry;
import io.github.lianghang1.paygateway.api.BillSheet;
import io.github.lianghang1.paygateway.channel.BillHandler;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
 * 微信交易账单处理器：拉取指定日期 ALL 类型账单（GZIP + CSV），按表头列名归一化为账单条目。
 *
 * <p>金额列为元（两位小数），按四舍五入折算为分；表头与汇总行的编码差异
 * 以 UTF-8 优先、GBK 回退的顺序解析；汇总行（首列含"总"）跳过。</p>
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
    public BillSheet fetchBill(String appId, LocalDate billDate, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        GetTradeBillRequest request = new GetTradeBillRequest();
        request.setBillDate(billDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        request.setBillType(BillType.ALL);
        request.setTarType(TarType.GZIP);
        if (config.getSubMchNo() != null) {
            request.setSubMchid(config.getSubMchNo());
        }
        QueryBillEntity entity = wrap("账单获取", () -> holder.clients(config).billDownloadService().getTradeBill(request));
        String csv = wrap("账单下载", () -> downloadCsv(entity.getDownloadUrl(), config));

        List<BillEntry> entries = new ArrayList<BillEntry>();
        Map<String, Integer> columnIndex = null;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.ByteArrayInputStream(csv.getBytes(Charset.forName("UTF-8"))),
                        Charset.forName("UTF-8")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] cells = line.split(",", -1);
                if (columnIndex == null) {
                    columnIndex = buildColumnIndex(cells);
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
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED, "微信账单解析失败: {}", e.getMessage(), e);
        }
        return new BillSheet()
                .setAppId(appId)
                .setChannelCode("wechat")
                .setBillDate(billDate)
                .setEntries(entries);
    }

    private BillEntry toEntry(String[] cells, Map<String, Integer> columnIndex, LocalDate billDate) {
        BillEntry entry = new BillEntry()
                .setChannelOrderNo(cell(cells, columnIndex, "微信支付单号"))
                .setBizOrderNo(cell(cells, columnIndex, "商户订单号"))
                .setRawState(cell(cells, columnIndex, "交易状态"));
        String state = entry.getRawState();
        entry.setSuccess(state != null && (state.contains("支付成功") || state.contains("充值成功")));
        entry.setEntryType(state != null && (state.contains("退款") || state.contains("转出"))
                ? io.github.lianghang1.paygateway.model.BillEntryType.REFUND
                : io.github.lianghang1.paygateway.model.BillEntryType.PAYMENT);
        String yuan = cell(cells, columnIndex, "付款金额");
        if (StringUtils.isBlank(yuan)) {
            yuan = cell(cells, columnIndex, "金额");
        }
        entry.setAmount(toFen(yuan));
        try {
            LocalDateTime occurTime = LocalDateTime.parse(cell(cells, columnIndex, "记账日期"), BILL_TIME);
            entry.setOccurTime(occurTime);
        } catch (Exception e) {
            entry.setOccurTime(billDate.atStartOfDay());
        }
        return entry;
    }

    private Map<String, Integer> buildColumnIndex(String[] headerCells) {
        Map<String, Integer> index = new HashMap<String, Integer>();
        for (int i = 0; i < headerCells.length; i++) {
            index.put(headerCells[i].trim().replace("`", ""), i);
        }
        return index;
    }

    private String cell(String[] cells, Map<String, Integer> columnIndex, String column) {
        Integer position = columnIndex.get(column);
        return position == null || position >= cells.length ? null : cells[position].trim().replace("`", "");
    }

    /** 元（两位小数）→ 分（四舍五入）。 */
    private Long toFen(String yuanText) {
        if (StringUtils.isBlank(yuanText)) {
            return null;
        }
        return io.github.lianghang1.paygateway.util.AmountUtil.toFen(
                new java.math.BigDecimal(yuanText.replaceAll("`", "")));
    }

    private String downloadCsv(String downloadUrl, ChannelConfig config) {
        try (InputStream stream = new com.wechat.pay.java.core.http.DefaultHttpClientBuilder()
                        .config(holder.clients(config).rsaConfig())
                        .build()
                        .download(downloadUrl);
             GZIPInputStream gzip = new GZIPInputStream(stream);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = gzip.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
            }
            return new String(buffer.toByteArray(), Charset.forName("UTF-8"));
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED, "微信账单下载失败: {}", e.getMessage(), e);
        }
    }

    private <T> T wrap(String action, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}

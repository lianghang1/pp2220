package com.sfi.conflux.pay.channels.alipay;

import com.alipay.api.domain.AlipayDataDataserviceBillDownloadurlQueryModel;
import com.alipay.api.request.AlipayDataDataserviceBillDownloadurlQueryRequest;
import com.alipay.api.response.AlipayDataDataserviceBillDownloadurlQueryResponse;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 支付宝交易账单处理器：获取账单下载地址（zip 内含 GB18030 编码 CSV），
 * 解析后按「#表头」列名归一化为账单条目。
 *
 * <p>渠道单号取「业务流水号」、业务单号取「商户订单号」、金额取「订单金额」（元）、
 * 流水类型按「业务类型」列判定（含"退款"记为退款流水）；时间取「完成时间」，
 * 解析失败回退账单日期零点。</p>
 *
 * @author lianghang
 */
@Slf4j
public class AlipayBillHandler implements BillHandler {

    private static final DateTimeFormatter BILL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Charset BILL_CHARSET = Charset.forName("GB18030");

    private final AlipayClientHolder holder;

    public AlipayBillHandler(AlipayClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public BillSheet fetchBill(String configId, LocalDate billDate, ChannelConfig config) {
        AlipayDataDataserviceBillDownloadurlQueryRequest request =
                new AlipayDataDataserviceBillDownloadurlQueryRequest();
        AlipayDataDataserviceBillDownloadurlQueryModel model =
                new AlipayDataDataserviceBillDownloadurlQueryModel();
        model.setBillDate(billDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.setBillType("trade");
        request.setBizModel(model);
        AlipayDataDataserviceBillDownloadurlQueryResponse response =
                holder.execute("账单地址获取", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
        String csv = downloadCsv(response.getBillDownloadUrl());
        List<BillEntry> entries = parseCsv(csv, billDate);
        return new BillSheet()
                .setConfigId(configId)
                .setChannelCode("alipay")
                .setBillDate(billDate)
                .setEntries(entries);
    }

    /** 下载并解压 zip，取首个 CSV 条目文件内容。 */
    private String downloadCsv(String downloadUrl) {
        try (InputStream stream = new java.net.URL(downloadUrl).openStream();
             ZipInputStream zip = new ZipInputStream(stream, BILL_CHARSET)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().contains("汇总")) {
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = zip.read(chunk)) > 0) {
                    buffer.write(chunk, 0, n);
                }
                return new String(buffer.toByteArray(), BILL_CHARSET);
            }
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED, "支付宝账单压缩包内无明细文件");
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "支付宝账单下载失败: {}", e.getMessage(), e);
        }
    }

    private List<BillEntry> parseCsv(String csv, LocalDate billDate) {
        List<BillEntry> entries = new ArrayList<BillEntry>();
        Map<String, Integer> columnIndex = new HashMap<String, Integer>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new ByteArrayInputStream(csv.getBytes(BILL_CHARSET)), BILL_CHARSET))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] cells = line.split(",", -1);
                if (lineNo == 1) {
                    // 首行为账户与账期摘要，跳过
                    continue;
                }
                if (columnIndex.isEmpty() && line.contains("#")) {
                    for (int i = 0; i < cells.length; i++) {
                        columnIndex.put(cells[i].trim().replace("#", ""), i);
                    }
                    continue;
                }
                if (columnIndex.isEmpty() || cells.length < columnIndex.size()) {
                    continue;
                }
                entries.add(toEntry(cells, columnIndex, billDate));
            }
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "支付宝账单解析失败: {}", e.getMessage(), e);
        }
        return entries;
    }

    private BillEntry toEntry(String[] cells, Map<String, Integer> columnIndex, LocalDate billDate) {
        String bizType = cell(cells, columnIndex, "业务类型");
        BillEntry entry = new BillEntry()
                .setEntryType(bizType != null && bizType.contains("退款")
                        ? BillEntryType.REFUND : BillEntryType.PAYMENT)
                .setChannelOrderNo(cell(cells, columnIndex, "业务流水号"))
                .setBizOrderNo(cell(cells, columnIndex, "商户订单号"))
                .setRawState(bizType)
                .setAmount(AlipayStateTranslator.toFen(cell(cells, columnIndex, "订单金额")))
                .setSuccess(true);
        try {
            entry.setOccurTime(LocalDateTime.parse(cell(cells, columnIndex, "完成时间"), BILL_TIME));
        } catch (Exception e) {
            entry.setOccurTime(billDate.atStartOfDay());
        }
        return entry;
    }

    private String cell(String[] cells, Map<String, Integer> columnIndex, String column) {
        Integer position = columnIndex.get(column);
        return position == null || position >= cells.length ? null : cells[position].trim();
    }
}

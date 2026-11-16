package io.github.lianghang1.conflux.invoice.engine;

import com.sun.net.httpserver.HttpServer;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.support.PdfImageConverter;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceFileFetcher;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.support.InMemoryAuthSessionStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceFileStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryRedApplyStore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 发票文件门面能力回归：fileBytes 统一出口（content/url 双形态 + 幂等回写 +
 * url 失效渠道重拉）、按需转图 pageImages、打印适配 printAdaptedPdf
 * （PDF → 逐页栅格化 → 重组 A4 PDF，防丢字场景）。
 */
class InvoiceFileGatewayCapabilityTest {

    private static final String CODE = "stub";
    private static final String TAX = "91310000TEST000001";
    private static final String ACCOUNT_KEY = InvoiceAccountKey.of("acc", TAX);

    private HttpServer server;
    private String base;
    private final AtomicInteger hits = new AtomicInteger();

    private InvoiceGateway gateway;
    private InMemoryInvoiceFileStore fileStore;
    private RefetchStubProvider provider;
    /** 渠道重拉返回的字节；null 表示渠道也取不到。静态供内部桩类共享。 */
    private static volatile byte[] refetchBytes;

    @BeforeEach
    void start() throws IOException {
        refetchBytes = null;
        byte[] pdf = samplePdf();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fresh.pdf", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.getResponseBody().close();
        });
        server.createContext("/expired.pdf", exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        provider = new RefetchStubProvider();
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(provider);

        ChannelConfig config = new ChannelConfig()
                .setAppId("demo").setChannelCode(CODE)
                .setChannelInstanceId("demo::stub::" + TAX)
                .setAccountKey(ACCOUNT_KEY).setSellerTaxNo(TAX);
        ChannelInstance instance = new ChannelInstance()
                .setInstanceId("i1").setAppId("demo").setChannelCode(CODE)
                .setAccountKey(ACCOUNT_KEY).setSellerTaxNo(TAX)
                .setTier(ChannelTier.SERVICE_API).setPriority(10).setEnabled(true)
                .setConfig(config);
        InMemoryInvoiceConfigProvider configProvider = new InMemoryInvoiceConfigProvider()
                .register(config, instance);

        InMemoryInvoiceStore invoiceStore = new InMemoryInvoiceStore();
        invoiceStore.save(invoice("I-1"));
        invoiceStore.save(invoice("I-2"));
        fileStore = new InMemoryInvoiceFileStore();
        gateway = new InvoiceGateway(new InvoiceRuntime(registry, configProvider,
                invoiceStore, new InMemoryRedApplyStore(),
                new InMemoryAuthSessionStore(), fileStore));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** 测试内生成的真实单页 PDF（经 PDFBox），保证转图/重组用例走真实渲染。 */
    private static byte[] samplePdf() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle_A4()));
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static org.apache.pdfbox.pdmodel.common.PDRectangle PDRectangle_A4() {
        return org.apache.pdfbox.pdmodel.common.PDRectangle.A4;
    }

    private Invoice invoice(String id) {
        return new Invoice()
                .setInvoiceId(id).setAppId("demo").setBizOrderNo("BIZ-" + id)
                .setChannelCode(CODE).setChannelInstanceId("i1").setAccountKey(ACCOUNT_KEY)
                .setSellerTaxNo(TAX).setSellerName("测试销方")
                .setBuyerTitle("测试购方").setBuyerTaxNo("91310000BUYER00001")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setAmount(new BigDecimal("100.00"))
                .setState(InvoiceState.SUCCEEDED)
                .setCreatedTime(LocalDateTime.now()).setUpdatedTime(LocalDateTime.now())
                .setVersion(0L);
    }

    private void archive(String invoiceId, InvoiceFile file) {
        List<InvoiceFile> one = new ArrayList<>();
        file.setInvoiceId(invoiceId);
        one.add(file);
        fileStore.saveAll(one);
    }

    // ==================== fileBytes ====================

    @Test
    void fileBytesReturnsDecodedContentDirectly() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><invoice/>";
        archive("I-1", new InvoiceFile().setFileId("F-XML")
                .setType(InvoiceFileType.XML).setContent(xml));
        byte[] bytes = gateway.fileBytes("I-1", InvoiceFileType.XML);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(xml);
    }

    @Test
    void fileBytesDownloadsUrlThenWritesBackIdempotently() {
        archive("I-1", new InvoiceFile().setFileId("F-PDF")
                .setType(InvoiceFileType.PDF).setUrl(base + "/fresh.pdf"));
        byte[] first = gateway.fileBytes("I-1", InvoiceFileType.PDF);
        assertThat(first).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(hits.get()).isEqualTo(1);

        // 回写幂等：FileStore 中已有内联 content，第二次不再下载
        byte[] second = gateway.fileBytes("I-1", InvoiceFileType.PDF);
        assertThat(second).isEqualTo(first);
        assertThat(hits.get()).isEqualTo(1);
        List<InvoiceFile> stored = fileStore.findByInvoiceIdAndType("I-1", InvoiceFileType.PDF);
        assertThat(stored.get(0).isMaterialized()).isTrue();
        assertThat(stored.get(0).getDigest()).isNotBlank();
    }

    @Test
    void fileBytesFallsBackToChannelRefetchWhenUrlExpired() throws IOException {
        refetchBytes = samplePdf();
        archive("I-1", new InvoiceFile().setFileId("F-PDF")
                .setType(InvoiceFileType.PDF).setUrl(base + "/expired.pdf"));
        // url 404 → 自动降级调渠道 InvoiceFileFetcher 重拉 → 字节可用且已固化归档
        byte[] bytes = gateway.fileBytes("I-1", InvoiceFileType.PDF);
        assertThat(bytes).isEqualTo(refetchBytes);
        assertThat(fileStore.findByInvoiceIdAndType("I-1", InvoiceFileType.PDF)
                .get(0).isMaterialized()).isTrue();
        assertThat(provider.refetchCount.get()).isEqualTo(1);
    }

    @Test
    void fileBytesFailsWithUrlExpiredWhenChannelAlsoEmpty() {
        refetchBytes = null;
        archive("I-2", new InvoiceFile().setFileId("F-PDF-2")
                .setType(InvoiceFileType.PDF).setUrl(base + "/expired.pdf"));
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.fileBytes("I-2", InvoiceFileType.PDF));
        assertThat(ex.getErrorCode()).isEqualTo(InvoiceErrorCode.FILE_URL_EXPIRED);
        assertThat(provider.refetchCount.get()).isEqualTo(1);
    }

    @Test
    void fileBytesRejectsMissingFiles() {
        assertThatThrownBy(() -> gateway.fileBytes("I-404", InvoiceFileType.PDF))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("单据不存在");
        assertThatThrownBy(() -> gateway.fileBytes("I-1", InvoiceFileType.OFD))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("未归档");
    }

    // ==================== 按需转图 / 打印适配 ====================

    @Test
    void pageImagesConvertsOnDemand() throws IOException {
        archive("I-1", new InvoiceFile().setFileId("F-PDF").setType(InvoiceFileType.PDF)
                .setContent(Base64.getMimeEncoder().encodeToString(samplePdf())));
        PdfImageConverter.Config config = new PdfImageConverter.Config();
        config.dpi = 96;
        List<PdfImageConverter.PageImage> pages = gateway.pageImages("I-1", config);
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getImageData()[0]).isEqualTo((byte) 0x89); // PNG 魔数
        assertThat(pages.get(0).getDataURL()).startsWith("data:image/png;base64,");
    }

    @Test
    void printAdaptedPdfRecomposesIntoTargetPaper() throws IOException {
        archive("I-1", new InvoiceFile().setFileId("F-PDF").setType(InvoiceFileType.PDF)
                .setContent(Base64.getMimeEncoder().encodeToString(samplePdf())));
        // 真实场景链路：PDF → 逐页转图 → 重组 A4 PDF（文字固化成像素，打印防丢字）
        byte[] adapted = gateway.printAdaptedPdf("I-1", PdfImageConverter.PaperSize.A4, 96);
        assertThat(adapted).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        try (PDDocument doc = PDDocument.load(adapted)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            // A4 竖版：210mm 宽 ≈ 595pt
            assertThat(doc.getPage(0).getMediaBox().getWidth())
                    .isCloseTo(595.0f, org.assertj.core.data.Offset.offset(2.0f));
        }
        // paper=null 保持原始尺寸
        byte[] original = gateway.printAdaptedPdf("I-1", null, 96);
        try (PDDocument doc = PDDocument.load(original)) {
            assertThat(doc.getPage(0).getMediaBox().getWidth())
                    .isCloseTo(595.0f, org.assertj.core.data.Offset.offset(2.0f));
        }
    }

    // ==================== 桩 ====================

    /** 带 fileFetcher 的桩渠道：重拉返回 {@link #refetchBytes}（null = 渠道也取不到）。 */
    private static final class RefetchStubProvider implements InvoiceChannelProvider {
        final AtomicInteger refetchCount = new AtomicInteger();

        @Override
        public String channelCode() {
            return CODE;
        }

        @Override
        public ChannelTier channelTier() {
            return ChannelTier.SERVICE_API;
        }

        @Override
        public Set<InvoiceType> supportedTypes() {
            return EnumSet.allOf(InvoiceType.class);
        }

        @Override
        public InvoiceFileFetcher fileFetcher() {
            return new InvoiceFileFetcher() {
                @Override
                public List<InvoiceFile> fetch(InvoiceContext ctx, Set<InvoiceFileType> types) {
                    refetchCount.incrementAndGet();
                    List<InvoiceFile> result = new ArrayList<>();
                    if (refetchBytes != null) {
                        result.add(new InvoiceFile()
                                .setType(InvoiceFileType.PDF)
                                .setContent(Base64.getMimeEncoder().encodeToString(refetchBytes)));
                    }
                    return result;
                }
            };
        }
    }
}

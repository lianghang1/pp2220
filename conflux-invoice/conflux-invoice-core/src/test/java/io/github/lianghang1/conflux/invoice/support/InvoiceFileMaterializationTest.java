package io.github.lianghang1.conflux.invoice.support;

import com.sun.net.httpserver.HttpServer;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 版式文件固化回归：Base64/url 两种渠道形态统一拉平为字节，
 * digest/size 核算、可选落盘、XML 法定原件 strict 阻断、就地回填。
 */
class InvoiceFileMaterializationTest {

    private static final String PDF_BYTES_SOURCE = "%PDF-1.4 fake invoice pdf body";
    private static final String XML_TEXT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><invoice>数电票</invoice>";

    @TempDir
    java.nio.file.Path tempDir;

    private HttpServer server;
    private String base;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final byte[] pdf = PDF_BYTES_SOURCE.getBytes(StandardCharsets.US_ASCII);
        server.createContext("/ok.pdf", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, pdf.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdf);
            }
        });
        server.createContext("/expired.pdf", exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private InvoiceFile pdfWithContent() {
        return new InvoiceFile().setFileId("F-1").setInvoiceId("I-1")
                .setType(InvoiceFileType.PDF)
                .setContent(Base64.getMimeEncoder().encodeToString(
                        PDF_BYTES_SOURCE.getBytes(StandardCharsets.US_ASCII)));
    }

    private InvoiceFile pdfWithUrl(String path) {
        return new InvoiceFile().setFileId("F-2").setInvoiceId("I-1")
                .setType(InvoiceFileType.PDF).setUrl(base + path);
    }

    @Test
    void contentFileGetsDigestAndSize() {
        InvoiceFileMaterializer m = InvoiceFileMaterializer.detached(new InvoiceFileOptions());
        InvoiceFile file = pdfWithContent();
        m.materialize(single(file), true);
        assertThat(file.isMaterialized()).isTrue();
        assertThat(file.getDigest()).isNotBlank();
        assertThat(file.getSize()).isEqualTo(PDF_BYTES_SOURCE.length());
        // 解码往返一致
        assertThat(new String(m.materializeBytes(file), StandardCharsets.US_ASCII))
                .isEqualTo(PDF_BYTES_SOURCE);
    }

    @Test
    void urlFileIsDownloadedAndBackfilled() {
        InvoiceFileMaterializer m = InvoiceFileMaterializer.detached(new InvoiceFileOptions());
        InvoiceFile file = pdfWithUrl("/ok.pdf");
        m.materialize(single(file), true);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(file.isMaterialized()).isTrue();
        // 固化结果就地回填到载体（无台账，由使用方随载体带回）
        assertThat(new String(InvoiceFileMaterializer.decode(file), StandardCharsets.US_ASCII))
                .isEqualTo(PDF_BYTES_SOURCE);
    }

    @Test
    void expiredUrlBlocksSuccessForLegalXml() {
        InvoiceFileMaterializer m = InvoiceFileMaterializer.detached(new InvoiceFileOptions());
        InvoiceFile xml = new InvoiceFile().setFileId("F-XML").setInvoiceId("I-1")
                .setType(InvoiceFileType.XML).setUrl(base + "/expired.pdf");
        GatewayException ex = assertThrows(GatewayException.class,
                () -> m.materialize(single(xml), true));
        assertThat(ex).hasMessageContaining("XML");
        assertThat(ex.getErrorCode()).isEqualTo(InvoiceErrorCode.XML_ORIGINAL_REQUIRED);
    }

    @Test
    void expiredUrlIsNonBlockingForDisplayFiles() {
        InvoiceFileMaterializer m = InvoiceFileMaterializer.detached(new InvoiceFileOptions());
        InvoiceFile pdf = pdfWithUrl("/expired.pdf");
        // 非严格（回调补充场景）：失败仅告警，url 保留待补拉
        m.materialize(single(pdf), false);
        assertThat(pdf.isMaterialized()).isFalse();
        assertThat(pdf.getUrl()).endsWith("/expired.pdf");

        // 两级策略：strict 仅对 XML 阻断，PDF 展示文件即使 strict 也只告警不阻断
        InvoiceFile pdfStrict = pdfWithUrl("/expired.pdf");
        m.materialize(single(pdfStrict), true);
        assertThat(pdfStrict.isMaterialized()).isFalse();

        // 单文件取字节（fileBytes 出口）仍抛 FILE_URL_EXPIRED，引导渠道重拉/修复
        GatewayException ex = assertThrows(GatewayException.class,
                () -> m.materializeBytes(pdfWithUrl("/expired.pdf")));
        assertThat(ex.getErrorCode()).isEqualTo(InvoiceErrorCode.FILE_URL_EXPIRED);
    }

    @Test
    void immediateDownloadDisabledKeepsUrlButStillForcesXml() {
        InvoiceFileOptions options = new InvoiceFileOptions().setImmediateDownload(false);
        InvoiceFileMaterializer m = InvoiceFileMaterializer.detached(options);
        InvoiceFile pdf = pdfWithUrl("/ok.pdf");
        m.materialize(single(pdf), true);
        // 展示文件：关闭即时固化后保留 url，不下载
        assertThat(hits.get()).isEqualTo(0);
        assertThat(pdf.isMaterialized()).isFalse();

        // XML 不受开关影响：法定底线始终强制下载
        InvoiceFile xml = new InvoiceFile().setFileId("F-XML").setInvoiceId("I-1")
                .setType(InvoiceFileType.XML).setUrl(base + "/ok.pdf");
        m.materialize(single(xml), true);
        assertThat(xml.isMaterialized()).isTrue();
        assertThat(new String(InvoiceFileMaterializer.decode(xml), StandardCharsets.UTF_8))
                .startsWith("%PDF");
    }

    @Test
    void diskCopyIsWrittenAndRecorded() throws IOException {
        InvoiceFileOptions options = new InvoiceFileOptions()
                .setOutputDir(tempDir.resolve("files").toString());
        InvoiceFileMaterializer m = InvoiceFileMaterializer.detached(options);
        InvoiceFile file = pdfWithContent();
        m.materialize(single(file), true);
        assertThat(file.getFilePath()).isNotBlank();
        File target = new File(file.getFilePath());
        assertThat(target).exists();
        assertThat(Files.readAllBytes(target.toPath()))
                .isEqualTo(PDF_BYTES_SOURCE.getBytes(StandardCharsets.US_ASCII));
        // 二次固化不重复落盘（filePath 已存在则跳过）
        m.materialize(single(file), true);
    }

    @Test
    void xmlContentIsTextNotBase64() {
        InvoiceFile xml = new InvoiceFile().setFileId("F-XML").setInvoiceId("I-1")
                .setType(InvoiceFileType.XML).setContent(XML_TEXT);
        assertThat(new String(InvoiceFileMaterializer.decode(xml), StandardCharsets.UTF_8))
                .isEqualTo(XML_TEXT);
        // 二进制类型的 content 才是 Base64
        InvoiceFile pdf = pdfWithContent();
        assertThat(InvoiceFileMaterializer.decode(pdf))
                .isEqualTo(PDF_BYTES_SOURCE.getBytes(StandardCharsets.US_ASCII));
        assertThat(InvoiceFileMaterializer.extensionOf(InvoiceFileType.OFD)).isEqualTo("ofd");
    }

    @Test
    void materializeBytesIsIdempotentAcrossCalls() {
        InvoiceFileMaterializer m = InvoiceFileMaterializer.detached(new InvoiceFileOptions());
        InvoiceFile file = pdfWithUrl("/ok.pdf");
        byte[] first = m.materializeBytes(file);
        assertThat(first).isEqualTo(PDF_BYTES_SOURCE.getBytes(StandardCharsets.US_ASCII));
        byte[] second = m.materializeBytes(file);
        assertThat(second).isEqualTo(first);
        // 内容已内联，第二次不再下载
        assertThat(hits.get()).isEqualTo(1);
    }

    private List<InvoiceFile> single(InvoiceFile file) {
        List<InvoiceFile> list = new ArrayList<>();
        list.add(file);
        return list;
    }
}

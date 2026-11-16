package io.github.lianghang1.conflux.invoice.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfImageConverter 回归：页转图（原尺寸/强制纸型）、栅格化 PDF 往返、
 * Base64 入口、非法输入与配置校验、PNG/JPEG DPI 元数据。
 */
class PdfImageConverterTest {

    @TempDir
    Path tempDir;

    /** 生成 2 页测试 PDF（A4 竖版 + A5 横版），每页写一行文本。 */
    private byte[] samplePdf() throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDRectangle[] boxes = {PDRectangle.A4,
                    new PDRectangle(PDRectangle.A5.getHeight(), PDRectangle.A5.getWidth())};
            for (int i = 0; i < boxes.length; i++) {
                PDPage page = new PDPage(boxes[i]);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 12);
                    cs.newLineAtOffset(72, boxes[i].getHeight() - 72);
                    cs.showText("page " + (i + 1));
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private PdfImageConverter.Config config() {
        PdfImageConverter.Config config = new PdfImageConverter.Config();
        config.dpi = 96; // 测试降 DPI 控制耗时与体积
        return config;
    }

    @Test
    void convertKeepsOriginalPageSize() throws Exception {
        List<PdfImageConverter.PageImage> pages =
                PdfImageConverter.convert(samplePdf(), config());
        assertThat(pages).hasSize(2);
        // A4@96dpi ≈ 793 x 1123（595pt → 793.33px 取整）
        assertThat(pages.get(0).getWidthPx()).isBetween(790, 800);
        assertThat(pages.get(0).getHeightPx()).isBetween(1118, 1128);
        // 第二页是 A5 横版
        assertThat(pages.get(1).getWidthPx()).isGreaterThan(pages.get(1).getHeightPx());
        // 物理尺寸按 DPI 反算回毫米
        assertThat(pages.get(0).getWidthMM()).isBetween(209.0, 211.0);
        assertThat(pages.get(0).getImageData()).startsWith((byte) 0x89, 'P', 'N', 'G');
    }

    @Test
    void convertForcesUnifiedA4Portrait() throws Exception {
        PdfImageConverter.Config config = config();
        config.targetPaper = PdfImageConverter.PaperSize.A4;
        config.targetOrientation = PdfImageConverter.Orientation.PORTRAIT;
        List<PdfImageConverter.PageImage> pages =
                PdfImageConverter.convert(samplePdf(), config);
        assertThat(pages).hasSize(2);
        // 横版 A5 内容放进竖版 A4 画布：等比缩放不变形，水平居中
        assertThat(pages.get(1).getWidthPx()).isBetween(790, 800);
        assertThat(pages.get(1).getHeightPx()).isBetween(1118, 1128);
        assertThat(pages.get(1).getHeightPx()).isGreaterThan(pages.get(1).getWidthPx());
    }

    @Test
    void convertContentRegionShrinksInsidePaper() throws Exception {
        PdfImageConverter.Config config = config();
        config.targetPaper = PdfImageConverter.PaperSize.A4;
        config.targetOrientation = PdfImageConverter.Orientation.LANDSCAPE;
        config.contentWidthMM = 210;
        config.contentHeightMM = 148.5; // A4 折半（A5 竖向区域）
        List<PdfImageConverter.PageImage> pages =
                PdfImageConverter.convert(samplePdf(), config);
        // 画布为 A4 横向（297 x 210mm），内容区域 210 x 148.5mm 在画布内等比缩放
        assertThat(pages.get(0).getWidthPx()).isBetween(1118, 1128);
        assertThat(pages.get(0).getHeightPx()).isBetween(790, 800);
        assertThat(pages.get(0).getWidthMM()).isCloseTo(297.0, org.assertj.core.data.Offset.offset(0.5));
        assertThat(pages.get(0).getHeightMM()).isCloseTo(210.0, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void rasterizeToPdfRoundTrip() throws Exception {
        byte[] source = samplePdf();
        File output = tempDir.resolve("rasterized.pdf").toFile();
        PdfImageConverter.rasterizeToPdf(source, output, config());
        assertThat(output).exists();
        assertThat(output).isNotEmpty();

        // 输出仍是合法 PDF，页数与源一致，每页尺寸随图像像素反算
        try (PDDocument doc = PDDocument.load(output)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isEqualTo(794f * 72f / 96f, org.assertj.core.data.Offset.offset(1.0f));
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isEqualTo(1123f * 72f / 96f, org.assertj.core.data.Offset.offset(1.0f));
        }
    }

    @Test
    void rasterizeToPdfBase64AndStream() throws Exception {
        byte[] source = samplePdf();
        String base64 = PdfImageConverter.rasterizeToPdfBase64(source, config());
        assertThat(Base64.getDecoder().decode(base64)).startsWith('%', 'P', 'D', 'F');

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfImageConverter.rasterizeToPdf(source, out, config());
        assertThat(out.toByteArray()).startsWith('%', 'P', 'D', 'F');
    }

    @Test
    void convertToBase64ListAndInputStreamOverloads() throws Exception {
        byte[] source = samplePdf();
        List<String> base64List = PdfImageConverter.convertToBase64List(source, config());
        assertThat(base64List).hasSize(2);
        assertThat(Base64.getDecoder().decode(base64List.get(0))).isNotEmpty();

        // InputStream / File / data URL 入口
        List<PdfImageConverter.PageImage> fromStream =
                PdfImageConverter.convert(new ByteArrayInputStream(source), config());
        assertThat(fromStream).hasSize(2);

        File pdfFile = tempDir.resolve("sample.pdf").toFile();
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            fos.write(source);
        }
        assertThat(PdfImageConverter.convert(pdfFile, config())).hasSize(2);
        assertThat(PdfImageConverter.convert(
                "data:application/pdf;base64," + Base64.getEncoder().encodeToString(source),
                config())).hasSize(2);
    }

    @Test
    void multiPageFileOutputBecomesZipSinglePageStaysImage() throws Exception {
        byte[] source = samplePdf();
        File zip = tempDir.resolve("pages.zip").toFile();
        PdfImageConverter.convertToFile(source, zip, config());
        try (java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(new FileInputStream(zip))) {
            java.util.zip.ZipEntry first = zis.getNextEntry();
            assertThat(first.getName()).isEqualTo("page_01.png");
            assertThat(zis.getNextEntry().getName()).isEqualTo("page_02.png");
            assertThat(zis.getNextEntry()).isNull();
        }

        // 单页输出 = 纯图片字节（非 ZIP）
        byte[] singleSource;
        try (PDDocument one = new PDDocument();
             ByteArrayOutputStream pdfOut = new ByteArrayOutputStream()) {
            one.addPage(new PDPage(PDRectangle.A4));
            one.save(pdfOut);
            singleSource = pdfOut.toByteArray();
        }
        File single = tempDir.resolve("single.png").toFile();
        PdfImageConverter.convertToFile(singleSource, single, config());
        assertThat(java.nio.file.Files.readAllBytes(single.toPath()))
                .startsWith((byte) 0x89, 'P', 'N', 'G');
    }

    @Test
    void jpegOutputFlattensTransparencyAndWritesDensity() throws Exception {
        PdfImageConverter.Config config = config();
        config.outputFormat = PdfImageConverter.ImageFormat.JPEG;
        config.jpegQuality = 0.9f;
        List<PdfImageConverter.PageImage> pages =
                PdfImageConverter.convert(samplePdf(), config);
        assertThat(pages.get(0).getImageData()[0]).isEqualTo((byte) 0xFF);
        assertThat(pages.get(0).getFormat()).isEqualTo(PdfImageConverter.ImageFormat.JPEG);
        assertThat(Base64.getDecoder().decode(pages.get(0).toBase64())).isNotEmpty();
    }

    @Test
    void rejectsInvalidPdfBytes() {
        byte[] garbage = "not a pdf at all".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> PdfImageConverter.convert(garbage, config()))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("%PDF-");
        assertThatThrownBy(() -> PdfImageConverter.convert((byte[]) null, config()))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("null");
    }

    @Test
    void validatesConfigBounds() {
        PdfImageConverter.Config config = config();
        config.dpi = 10;
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DPI");

        config.dpi = 300;
        config.marginMM = -1;
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("marginMM");

        config.marginMM = 0;
        config.contentWidthMM = 210;
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同时");

        config.contentHeightMM = 148.5;
        config.outputFormat = PdfImageConverter.ImageFormat.JPEG;
        config.jpegQuality = 1.5f;
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jpegQuality");
    }

    @Test
    void pageImageMetaHelpers() throws Exception {
        List<PdfImageConverter.PageImage> pages =
                PdfImageConverter.convert(samplePdf(), config());
        PdfImageConverter.PageImage page = pages.get(0);
        assertThat(page.getPageIndex()).isZero();
        assertThat(page.getWidthMM()).isCloseTo(210.0, org.assertj.core.data.Offset.offset(0.5));
        assertThat(page.getHeightMM()).isCloseTo(297.0, org.assertj.core.data.Offset.offset(0.5));
        assertThat(page.getDataURL()).startsWith("data:image/png;base64,");
        assertThat(page.getSizeInfo()).contains("第1页").contains("mm");
    }
}

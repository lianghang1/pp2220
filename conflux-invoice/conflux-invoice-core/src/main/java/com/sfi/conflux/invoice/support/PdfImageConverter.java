package com.sfi.conflux.invoice.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * PDF 转图片工具类（打印/展示防丢字场景专用）
 *
 * <p>背景：部分 PDF 依赖特殊/非标准字体，直接展示或打印时容易丢字、乱码。
 * 将每页栅格化为图片后，文字固化成像素，打印不再依赖目标机器的字体环境。</p>
 *
 * <p>三种工作模式：
 * <ul>
 *   <li><b>保持原始尺寸</b>（默认）：每页按页面原始物理尺寸 × DPI 直接渲染，
 *       A4/A5/横版/竖版/超大图纸/混合页面尺寸都按原样保留</li>
 *   <li><b>强制统一纸型</b>：设置 {@code targetPaper} 后，所有页面等比缩放放进指定纸型
 *       （如 A4 竖向），内容按 {@code contentAlignment} 对齐、空白处补白底，
 *       <b>绝不裁切、绝不变形</b>；方向可选 PORTRAIT / LANDSCAPE / AUTO（按每页比例自动横竖）。
 *       进一步设置 {@code contentWidthMM}/{@code contentHeightMM} 可把内容压缩到指定尺寸的区域
 *       （如 A4 页面上内容压缩到 A5 大小），页面纸型保持 {@code targetPaper} 不变</li>
 *   <li><b>栅格化 PDF</b>：{@code rasterizeToPdf} 输出仍是单一 PDF 文件（每页内容已变成
 *       整页图片），"一个文件进出"的接口无需任何改动，打印同样不会丢字</li>
 * </ul>
 *
 * <p>其他约定：
 * <ul>
 *   <li>单页与多页统一返回 {@code List<PageImage>}</li>
 *   <li>输出到单个文件/流时：单页 = 纯图片字节；多页 = ZIP 包（page_01.png ...）</li>
 *   <li>基于 PDFBox 2.x（PDDocument.load）；升级 PDFBox 3.x 需改用 Loader.loadPDF</li>
 * </ul>
 *
 * <p>打印建议：按"实际大小/100% 缩放"打印，图片物理尺寸即与配置的纸型一致；
 * 文字类文档 DPI 建议 300，页面很大时可降到 200 控制文件体积。</p>
 *
 * <p>用法示例：</p>
 * <pre>
 *   PdfImageConverter.Config config = new PdfImageConverter.Config();
 *   config.dpi = 300;
 *   config.outputFormat = PdfImageConverter.ImageFormat.PNG;
 *
 *   // 模式一：保持原始页面尺寸（默认，推荐）
 *   List&lt;PageImage&gt; pages = PdfImageConverter.convert(pdfFile, config);
 *
 *   // 模式二：强制统一为 A4 竖向（内容等比缩放居中，白边补齐）
 *   config.targetPaper = PdfImageConverter.PaperSize.A4;
 *   config.targetOrientation = PdfImageConverter.Orientation.PORTRAIT;
 *   config.contentAlignment = PdfImageConverter.Alignment.CENTER; // 默认即居中，可省略
 *
 *   List&lt;PageImage&gt; a4Pages = PdfImageConverter.convert(pdfFile, config);
 *   byte[] firstPagePng = a4Pages.get(0).getImageData();
 *
 *   // 模式二变体：页面仍为 A4，内容压缩到 A4 折半大小（210x148.5，即 A5 横向；
 *   //             任意宽高均可，内容等比缩放，位置按 contentAlignment 对齐）
 *   config.contentWidthMM = 210;
 *   config.contentHeightMM = 148.5;
 *
 *   // 模式三：栅格化 PDF —— 输出仍是单个 .pdf 文件，接口无需改动
 *   PdfImageConverter.rasterizeToPdf(pdfFile, new File("output.pdf"), config);
 * </pre>
 *
 * <p>依赖说明：基于 PDFBox 2.x（PDDocument.load）。本依赖为 {@code optional}，
 * 使用方需自行引入 {@code org.apache.pdfbox:pdfbox}；升级 PDFBox 3.x 需改用 Loader.loadPDF。</p>
 *
 * @Author lianghang
 */
public class PdfImageConverter {

    /** 输出图片格式 */
    public enum ImageFormat { PNG, JPEG }

    /** 纸型（按竖放记录短边/长边，单位毫米） */
    public enum PaperSize {
        A4(210, 297),
        A5(148, 210),
        A3(297, 420),
        B5(176, 250),
        LETTER(215.9, 279.4);

        /** 短边（毫米） */
        public final double shortMM;
        /** 长边（毫米） */
        public final double longMM;

        PaperSize(double shortMM, double longMM) {
            this.shortMM = shortMM;
            this.longMM = longMM;
        }
    }

    /** 强制纸型时的页面方向（仅 targetPaper 非空时生效） */
    public enum Orientation { PORTRAIT, LANDSCAPE, AUTO }

    /** 强制纸型时内容在画布中的垂直对齐（水平始终居中） */
    public enum Alignment { TOP, CENTER, BOTTOM }

    /** 转换配置 */
    public static class Config {
        /** 渲染 DPI。打印建议 300；最低 72，最高 1200 */
        public int dpi = 300;
        /** 输出格式。图片输出：打印推荐 PNG（无损、文字边缘锐利），体积敏感用 JPEG；
         *  栅格化 PDF：PNG=无损嵌入图片，JPEG=有损嵌入图片（体积小） */
        public ImageFormat outputFormat = ImageFormat.PNG;
        /** JPEG 压缩质量 (0, 1]，打印建议 ≥ 0.9 */
        public float jpegQuality = 0.9f;

        /** 目标纸型；null（默认）= 保持每页原始尺寸，不缩放 */
        public PaperSize targetPaper = null;
        /** 目标方向，仅在 targetPaper 非空时生效；AUTO = 按每页宽高比自动选横/竖 */
        public Orientation targetOrientation = Orientation.PORTRAIT;
        /** 内容的垂直对齐，水平始终居中；默认居中（打印观感最好）。
         *  设置 contentWidthMM/contentHeightMM 时，该对齐同样决定内容区域在页面上的垂直位置 */
        public Alignment contentAlignment = Alignment.CENTER;
        /** 强制纸型时四周留白（毫米）：内容在画布内缩进 marginMM 后再等比缩放，默认 0 = 尽量铺满 */
        public double marginMM = 0;
        /** 内容目标宽度（毫米，可选）：与 contentHeightMM 同时设置后，内容缩放进该尺寸的区域，
         *  区域在页面上水平居中、垂直按 {@link #contentAlignment} 对齐（再受 marginMM 内缩）。
         *  典型用法：targetPaper=A4 + contentWidthMM=210 + contentHeightMM=148.5 = 页面仍为 A4，
         *  内容压缩到 A4 折半（A5 横向）大小。默认 0 = 不指定，内容尽量铺满页面（原有逻辑不变） */
        public double contentWidthMM = 0;
        /** 内容目标高度（毫米，可选），必须与 {@link #contentWidthMM} 同时大于 0；默认 0 = 不指定 */
        public double contentHeightMM = 0;
        /** 内容是否拉伸填满内容尺寸区域（宽高独立缩放，允许变形）。
         *  false（默认）= 等比缩放：内容比例与区域不一致时短边方向会留白（如竖版 A4 内容放进
         *  210x148.5 区域，实际只有 105x148.5，两边留白）；
         *  true = 内容正好铺满 contentWidthMM x contentHeightMM 区域，无两边留白但内容有拉伸变形 */
        public boolean contentStretch = false;

        public void validate() {
            if (dpi < 72 || dpi > 1200) {
                throw new IllegalArgumentException("DPI 必须在 72-1200 之间: " + dpi);
            }
            if (marginMM < 0) {
                throw new IllegalArgumentException("marginMM 不能为负数: " + marginMM);
            }
            if ((contentWidthMM > 0) != (contentHeightMM > 0)) {
                throw new IllegalArgumentException("contentWidthMM 与 contentHeightMM 必须同时设置或同时为 0: "
                        + contentWidthMM + " x " + contentHeightMM);
            }
            if (contentWidthMM < 0 || contentHeightMM < 0) {
                throw new IllegalArgumentException("内容尺寸不能为负数: " + contentWidthMM + " x " + contentHeightMM);
            }
            if (outputFormat == ImageFormat.JPEG && (jpegQuality <= 0 || jpegQuality > 1)) {
                throw new IllegalArgumentException("jpegQuality 必须在 (0, 1] 之间: " + jpegQuality);
            }
        }
    }

    /** 单页转换结果 */
    public static class PageImage {
        private final int pageIndex;          // 从 0 开始
        private final byte[] imageData;
        private final int widthPx;
        private final int heightPx;
        private final int dpi;
        private final ImageFormat format;

        PageImage(int pageIndex, byte[] imageData, int widthPx, int heightPx, int dpi, ImageFormat format) {
            this.pageIndex = pageIndex;
            this.imageData = imageData;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.dpi = dpi;
            this.format = format;
        }

        public int getPageIndex() { return pageIndex; }
        public byte[] getImageData() { return imageData; }
        public int getWidthPx() { return widthPx; }
        public int getHeightPx() { return heightPx; }
        public ImageFormat getFormat() { return format; }

        /**
         * 打印物理宽度（毫米）= 像素 / DPI × 25.4。
         * 保持原始尺寸模式下 = 原 PDF 页面宽度；强制纸型模式下 = 目标纸型宽度。
         */
        public double getWidthMM() { return widthPx * 25.4 / dpi; }

        /** 打印物理高度（毫米），含义同 {@link #getWidthMM()} */
        public double getHeightMM() { return heightPx * 25.4 / dpi; }

        public String toBase64() { return Base64.getEncoder().encodeToString(imageData); }

        public String getDataURL() {
            String mime = format == ImageFormat.JPEG ? "jpeg" : "png";
            return "data:image/" + mime + ";base64," + toBase64();
        }

        public String getSizeInfo() {
            return String.format("第%d页 %dx%dpx (%.1f x %.1f mm)",
                    pageIndex + 1, widthPx, heightPx, getWidthMM(), getHeightMM());
        }
    }

    /**
     * ========================
     * 核心 API：每页转一张图
     * ========================
     */

    public static List<PageImage> convert(File pdfFile, Config config) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            return renderAllPages(document, config);
        }
    }

    public static List<PageImage> convert(byte[] pdfData, Config config) throws IOException {
        try (PDDocument document = loadPdf(pdfData)) {
            return renderAllPages(document, config);
        }
    }

    /** 注意：PDDocument 会读取并关闭传入的流 */
    public static List<PageImage> convert(InputStream pdfStream, Config config) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            return renderAllPages(document, config);
        }
    }

    /** 支持纯 Base64 或 data URL 前缀形式 */
    public static List<PageImage> convert(String pdfBase64, Config config) throws IOException {
        return convert(decodeBase64(pdfBase64), config);
    }

    /**
     * ========================
     * 输出到目录：每页一个图片文件
     * ========================
     */

    public static List<File> convertToDirectory(File pdfFile, File outputDir, Config config) throws IOException {
        config.validate();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outputDir);
        }
        String baseName = stripExtension(pdfFile.getName());
        List<PageImage> pages = convert(pdfFile, config);
        List<File> files = new ArrayList<>(pages.size());
        for (PageImage page : pages) {
            File out = new File(outputDir, baseName + "_page_"
                    + String.format("%02d", page.getPageIndex() + 1) + "." + extension(page.getFormat()));
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(page.getImageData());
            }
            files.add(out);
        }
        return files;
    }

    /**
     * ========================
     * 输出到单个文件：单页为图片；多页为 ZIP（请传入 .zip 后缀的文件名）
     * ========================
     */

    public static void convertToFile(File pdfFile, File outputFile, Config config) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            convertToStream(pdfFile, fos, config);
        }
    }

    public static void convertToFile(byte[] pdfData, File outputFile, Config config) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            convertToStream(pdfData, fos, config);
        }
    }

    public static void convertToFile(InputStream pdfStream, File outputFile, Config config) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            convertToStream(pdfStream, fos, config);
        }
    }

    public static void convertToFile(String pdfBase64, File outputFile, Config config) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            convertToStream(pdfBase64, fos, config);
        }
    }

    /**
     * ========================
     * 输出到流：单页为图片字节；多页为 ZIP
     * ========================
     */

    public static void convertToStream(File pdfFile, OutputStream outputStream, Config config) throws IOException {
        writeToStream(convert(pdfFile, config), outputStream);
    }

    public static void convertToStream(byte[] pdfData, OutputStream outputStream, Config config) throws IOException {
        writeToStream(convert(pdfData, config), outputStream);
    }

    public static void convertToStream(InputStream pdfStream, OutputStream outputStream, Config config) throws IOException {
        writeToStream(convert(pdfStream, config), outputStream);
    }

    public static void convertToStream(String pdfBase64, OutputStream outputStream, Config config) throws IOException {
        writeToStream(convert(pdfBase64, config), outputStream);
    }

    /**
     * ========================
     * 栅格化 PDF：无论几页，输出都是单一 PDF 文件（推荐给"一个文件进出"的接口）
     * ========================
     */

    public static void rasterizeToPdf(File pdfFile, File outputPdf, Config config) throws IOException {
        try (PDDocument source = PDDocument.load(pdfFile)) {
            rasterize(source, config, outputPdf, null);
        }
    }

    public static void rasterizeToPdf(byte[] pdfData, File outputPdf, Config config) throws IOException {
        try (PDDocument source = loadPdf(pdfData)) {
            rasterize(source, config, outputPdf, null);
        }
    }

    /** 注意：PDDocument 会读取并关闭传入的流 */
    public static void rasterizeToPdf(InputStream pdfStream, File outputPdf, Config config) throws IOException {
        try (PDDocument source = PDDocument.load(pdfStream)) {
            rasterize(source, config, outputPdf, null);
        }
    }

    public static void rasterizeToPdf(String pdfBase64, File outputPdf, Config config) throws IOException {
        rasterizeToPdf(decodeBase64(pdfBase64), outputPdf, config);
    }

    /** 输出到流（如 HTTP 下载响应），单文件，无论页数 */
    public static void rasterizeToPdf(File pdfFile, OutputStream outputStream, Config config) throws IOException {
        try (PDDocument source = PDDocument.load(pdfFile)) {
            rasterize(source, config, null, outputStream);
        }
    }

    public static void rasterizeToPdf(byte[] pdfData, OutputStream outputStream, Config config) throws IOException {
        try (PDDocument source = loadPdf(pdfData)) {
            rasterize(source, config, null, outputStream);
        }
    }

    /**
     * ========================
     * 栅格化 PDF → Base64：输出是 Base64 编码的 PDF 字符串，无论页数
     * ========================
     */

    public static String rasterizeToPdfBase64(File pdfFile, Config config) throws IOException {
        try (PDDocument source = PDDocument.load(pdfFile)) {
            return rasterizeToBase64(source, config);
        }
    }

    public static String rasterizeToPdfBase64(byte[] pdfData, Config config) throws IOException {
        try (PDDocument source = loadPdf(pdfData)) {
            return rasterizeToBase64(source, config);
        }
    }

    /** 注意：PDDocument 会读取并关闭传入的流 */
    public static String rasterizeToPdfBase64(InputStream pdfStream, Config config) throws IOException {
        try (PDDocument source = PDDocument.load(pdfStream)) {
            return rasterizeToBase64(source, config);
        }
    }

    /** 输入支持纯 Base64 或 data URL 前缀形式 */
    public static String rasterizeToPdfBase64(String pdfBase64, Config config) throws IOException {
        return rasterizeToPdfBase64(decodeBase64(pdfBase64), config);
    }

    /**
     * ========================
     * Base64 输出：每页一个元素
     * ========================
     */

    public static List<String> convertToBase64List(File pdfFile, Config config) throws IOException {
        return collectBase64(convert(pdfFile, config));
    }

    public static List<String> convertToBase64List(byte[] pdfData, Config config) throws IOException {
        return collectBase64(convert(pdfData, config));
    }

    public static List<String> convertToBase64List(InputStream pdfStream, Config config) throws IOException {
        return collectBase64(convert(pdfStream, config));
    }

    public static List<String> convertToBase64List(String pdfBase64, Config config) throws IOException {
        return collectBase64(convert(pdfBase64, config));
    }

    /**
     * ========================
     * 内部实现
     * ========================
     */

    /**
     * 把源 PDF 每页渲染成图片后写入新 PDF：页数与页面顺序与源文件一致。
     * 未设置 targetPaper 时每页尺寸与源页一致；设置了 targetPaper 则按目标纸型统一。
     * 页面尺寸按渲染图像素反算（pt = px × 72 / DPI），自动兼容源页面的旋转标记。
     * 注意：所有页面的嵌入图片会驻留内存直到保存，页数很多时建议降低 DPI 或改用 JPEG 输出格式。
     */
    private static void rasterize(PDDocument source, Config config, File outputPdf, OutputStream outputStream)
            throws IOException {
        config.validate();
        PDFRenderer renderer = new PDFRenderer(source);
        try (PDDocument target = new PDDocument()) {
            int pageCount = source.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                checkRenderSize(source, i, config);
                BufferedImage rendered = renderer.renderImageWithDPI(i, config.dpi, ImageType.RGB);
                BufferedImage image = config.targetPaper != null ? fitToPaper(rendered, config) : rendered;
                try {
                    float pageW = image.getWidth() * 72f / config.dpi;
                    float pageH = image.getHeight() * 72f / config.dpi;
                    PDPage page = new PDPage(new PDRectangle(pageW, pageH));
                    target.addPage(page);
                    PDImageXObject pdImage = config.outputFormat == ImageFormat.JPEG
                            ? JPEGFactory.createFromImage(target, image, config.jpegQuality)
                            : LosslessFactory.createFromImage(target, image);
                    try (PDPageContentStream contentStream = new PDPageContentStream(target, page)) {
                        contentStream.drawImage(pdImage, 0, 0, pageW, pageH);
                    }
                } finally {
                    rendered.flush(); // 及时释放当前页位图，控制内存峰值
                    if (image != rendered) {
                        image.flush();
                    }
                }
            }
            if (outputPdf != null) {
                target.save(outputPdf);
            } else {
                target.save(outputStream);
            }
        }
    }

    /** 栅格化后编码为 Base64 字符串 */
    private static String rasterizeToBase64(PDDocument source, Config config) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            rasterize(source, config, null, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    /**
     * 逐页渲染。
     * 未强制纸型：像素尺寸 = 页面物理尺寸(英寸) × DPI，不做任何缩放；
     * 强制纸型：渲染后等比缩放放进目标画布。
     */
    private static List<PageImage> renderAllPages(PDDocument document, Config config) throws IOException {
        config.validate();
        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = document.getNumberOfPages();
        boolean forcePaper = config.targetPaper != null;
        List<PageImage> pages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            checkRenderSize(document, i, config);
            // ImageType.RGB：不透明渲染，兼顾 JPEG 兼容性与体积
            BufferedImage rendered = renderer.renderImageWithDPI(i, config.dpi, ImageType.RGB);
            BufferedImage output = forcePaper ? fitToPaper(rendered, config) : rendered;
            byte[] data = encodeImage(output, config);
            pages.add(new PageImage(i, data, output.getWidth(), output.getHeight(), config.dpi, config.outputFormat));
            rendered.flush(); // 及时释放当前页位图，控制多页大文档的内存峰值
            if (output != rendered) {
                output.flush();
            }
        }
        return pages;
    }

    /** 计算目标画布尺寸并把页面等比放入 */
    private static BufferedImage fitToPaper(BufferedImage page, Config config) {
        double shortSide = config.targetPaper.shortMM;
        double longSide = config.targetPaper.longMM;
        boolean landscape;
        switch (config.targetOrientation) {
            case LANDSCAPE:
                landscape = true;
                break;
            case AUTO:
                // 按页面实际比例自动选方向：横版页用横向画布，竖版页用竖向画布
                landscape = page.getWidth() >= page.getHeight();
                break;
            case PORTRAIT:
            default:
                landscape = false;
                break;
        }
        double canvasWMM = landscape ? longSide : shortSide;
        double canvasHMM = landscape ? shortSide : longSide;
        int canvasW = (int) Math.round(canvasWMM / 25.4 * config.dpi);
        int canvasH = (int) Math.round(canvasHMM / 25.4 * config.dpi);
        int marginPx = (int) Math.round(config.marginMM / 25.4 * config.dpi);

        // 计算内容可用区域（region），内容在该区域内等比缩放、水平居中、垂直按 contentAlignment 对齐
        int regionX, regionY, regionW, regionH;
        if (config.contentWidthMM > 0 && config.contentHeightMM > 0) {
            // 指定内容尺寸：区域 = contentWidthMM x contentHeightMM（再内缩 marginMM）
            regionW = (int) Math.round(config.contentWidthMM / 25.4 * config.dpi) - 2 * marginPx;
            regionH = (int) Math.round(config.contentHeightMM / 25.4 * config.dpi) - 2 * marginPx;
            if (regionW <= 0 || regionH <= 0) {
                throw new IllegalArgumentException("marginMM 过大：内容尺寸 " + config.contentWidthMM
                        + " x " + config.contentHeightMM + "mm 内放不下 " + config.marginMM + "mm 的边距");
            }
            if (regionW > canvasW || regionH > canvasH) {
                throw new IllegalArgumentException("内容尺寸 " + config.contentWidthMM + " x "
                        + config.contentHeightMM + "mm 超过目标纸型 " + config.targetPaper
                        + "（" + canvasWMM + " x " + canvasHMM + "mm）画布，无法放入");
            }
            // 区域水平居中，垂直按 contentAlignment 对齐
            regionX = (canvasW - regionW) / 2;
            switch (config.contentAlignment) {
                case TOP:
                    regionY = marginPx;
                    break;
                case BOTTOM:
                    regionY = canvasH - marginPx - regionH;
                    break;
                case CENTER:
                default:
                    regionY = (canvasH - regionH) / 2;
                    break;
            }
        } else {
            // 未指定内容尺寸：内容区域 = 画布内缩 marginMM（原有逻辑）
            regionW = canvasW - 2 * marginPx;
            regionH = canvasH - 2 * marginPx;
            if (regionW <= 0 || regionH <= 0) {
                throw new IllegalArgumentException("marginMM 过大：" + config.targetPaper
                        + " 画布内放不下 " + config.marginMM + "mm 的边距");
            }
            regionX = marginPx;
            regionY = marginPx;
        }
        return fitContain(page, canvasW, canvasH, regionX, regionY, regionW, regionH,
                config.contentAlignment, config.contentStretch);
    }

    /**
     * 把页面放进画布上的指定区域，空白处铺白底。
     * stretch=false：等比缩放（不变形），内容在区域内水平居中、垂直按 alignment 对齐；
     * stretch=true：宽高独立缩放，内容拉伸正好铺满整个区域（允许变形，alignment 不再起作用）。
     */
    private static BufferedImage fitContain(BufferedImage page, int canvasW, int canvasH,
                                            int regionX, int regionY, int regionW, int regionH,
                                            Alignment alignment, boolean stretch) {
        // 区域即整个画布且页面尺寸与画布完全一致时无需重绘
        if (regionX == 0 && regionY == 0 && regionW == canvasW && regionH == canvasH
                && page.getWidth() == canvasW && page.getHeight() == canvasH) {
            return page;
        }
        final int drawW, drawH, x, y;
        if (stretch) {
            // 拉伸填满区域：宽高各自缩放到区域尺寸
            drawW = regionW;
            drawH = regionH;
            x = regionX;
            y = regionY;
        } else {
            double scale = Math.min((double) regionW / page.getWidth(), (double) regionH / page.getHeight());
            drawW = Math.max(1, (int) Math.round(page.getWidth() * scale));
            drawH = Math.max(1, (int) Math.round(page.getHeight() * scale));
            x = regionX + (regionW - drawW) / 2;
            switch (alignment) {
                case TOP:
                    y = regionY;
                    break;
                case BOTTOM:
                    y = regionY + regionH - drawH;
                    break;
                case CENTER:
                default:
                    y = regionY + (regionH - drawH) / 2;
                    break;
            }
        }

        BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, canvasW, canvasH);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(page, x, y, drawW, drawH, null);
        } finally {
            g2d.dispose();
        }
        return canvas;
    }

    private static void writeToStream(List<PageImage> pages, OutputStream outputStream) throws IOException {
        if (pages.isEmpty()) {
            throw new IOException("源 PDF 没有任何页面，无法输出");
        }
        if (pages.size() == 1) {
            outputStream.write(pages.get(0).getImageData());
        } else {
            writeZip(pages, outputStream);
        }
        outputStream.flush();
    }

    /** 只 finish 不 close：不接管调用方流的关闭时机 */
    private static void writeZip(List<PageImage> pages, OutputStream outputStream) throws IOException {
        String ext = extension(pages.get(0).getFormat());
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        for (PageImage page : pages) {
            zip.putNextEntry(new ZipEntry(String.format("page_%02d.%s", page.getPageIndex() + 1, ext)));
            zip.write(page.getImageData());
            zip.closeEntry();
        }
        zip.finish();
    }

    private static List<String> collectBase64(List<PageImage> pages) {
        List<String> list = new ArrayList<>(pages.size());
        for (PageImage page : pages) {
            list.add(page.toBase64());
        }
        return list;
    }

    private static byte[] encodeImage(BufferedImage image, Config config) throws IOException {
        return config.outputFormat == ImageFormat.JPEG
                ? encodeJpeg(image, config.jpegQuality, config.dpi)
                : encodePng(image, config.dpi);
    }

    /**
     * PNG 编码并写入 pHYs 物理分辨率（像素/米）。
     * 必须带 DPI 元数据：输出图片若被外部工具（如 WPS/Acrobat）再转 PDF，
     * 工具按该 DPI 还原物理尺寸；缺失时工具按 72/96 DPI 估算，A4@300dpi 会被放大成 ~875mm 宽的巨大页面。
     */
    private static byte[] encodePng(BufferedImage image, int dpi) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("没有可用的 PNG 编码器");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
                if (metadata != null) {
                    metadata.setFromTree(metadata.getNativeMetadataFormatName(),
                            pngMetadataTree(dpi).getDocumentElement());
                }
                try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(image, null, metadata), param);
                }
                return baos.toByteArray();
            } finally {
                writer.dispose();
            }
        }
    }

    /** 构造只含 pHYs 节点的 PNG 元数据树（未指定的 chunk 由编码器按默认值生成） */
    private static Document pngMetadataTree(int dpi) throws IOException {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElement("javax_imageio_png_1.0");
            Element phys = doc.createElement("pHYs");
            int perMeter = (int) Math.round(dpi / 0.0254);
            phys.setAttribute("pixelsPerUnitXAxis", String.valueOf(perMeter));
            phys.setAttribute("pixelsPerUnitYAxis", String.valueOf(perMeter));
            phys.setAttribute("unitSpecifier", "meter"); // 枚举值：unknown / meter（meter = 像素/米）
            root.appendChild(phys);
            doc.appendChild(root);
            return doc;
        } catch (Exception e) {
            throw new IOException("构造 PNG pHYs 元数据失败", e);
        }
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality, int dpi) throws IOException {
        BufferedImage rgbImage = image;
        if (image.getTransparency() != BufferedImage.OPAQUE) {
            // JPEG 不支持透明通道，透明区域铺白底
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = rgbImage.createGraphics();
            try {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
                g2d.drawImage(image, 0, 0, null);
            } finally {
                g2d.dispose();
            }
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("没有可用的 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(rgbImage), param);
                if (metadata != null) {
                    applyJpegDensity(metadata, dpi);
                }
                try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(rgbImage, null, metadata), param);
                }
                return baos.toByteArray();
            } finally {
                writer.dispose();
            }
        }
    }

    /** 在 JPEG JFIF（app0JFIF）元数据中写入密度（resUnits=1 即 DPI），语义同 PNG pHYs */
    private static void applyJpegDensity(IIOMetadata metadata, int dpi) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(format);
        Node jfif = findFirstNode(root, "app0JFIF");
        if (jfif == null) {
            return; // 没有 JFIF 段时由编码器自行决定，不强行注入
        }
        jfif.getAttributes().getNamedItem("resUnits").setNodeValue("1"); // 1 = 点/英寸
        jfif.getAttributes().getNamedItem("Xdensity").setNodeValue(String.valueOf(dpi));
        jfif.getAttributes().getNamedItem("Ydensity").setNodeValue(String.valueOf(dpi));
        metadata.setFromTree(format, root);
    }

    private static Node findFirstNode(Node node, String name) {
        if (name.equals(node.getNodeName())) {
            return node;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node found = findFirstNode(children.item(i), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 兼容带换行/空白的 Base64（MIME 解码器会忽略字母表之外的字符） */
    private static byte[] decodeBase64(String base64) {
        String clean = base64.contains(",")
                ? base64.substring(base64.indexOf(',') + 1)
                : base64;
        return Base64.getMimeDecoder().decode(clean);
    }

    /** 单页渲染位图内存上限（宽×高×4字节 ARGB，1GB）：超过时抛明确错误而非 OutOfMemoryError */
    private static final long MAX_SINGLE_PAGE_RENDER_BYTES = 1L << 30;

    /**
     * 渲染前估算单页位图内存（页面尺寸 × DPI 的 ARGB 位图字节数），超限直接抛可排查的 IOException。
     * 防御场景：超大图纸页（如几千 pt 的 CAD 导出）配合高 DPI 时，单页位图可达数 GB，
     * 若不拦截会在 renderImageWithDPI 内抛出难以定位的 OutOfMemoryError。
     */
    private static void checkRenderSize(PDDocument document, int pageIndex, Config config) throws IOException {
        PDRectangle box = document.getPage(pageIndex).getCropBox();
        long pxW = (long) Math.ceil(box.getWidth() / 72.0 * config.dpi);
        long pxH = (long) Math.ceil(box.getHeight() / 72.0 * config.dpi);
        long bytes = pxW * pxH * 4L;
        if (bytes > MAX_SINGLE_PAGE_RENDER_BYTES) {
            throw new IOException(String.format(
                    "第 %d 页渲染内存超限：页面 %.0f x %.0f pt @ %d DPI 约需 %.0f MB 位图（单页上限 1024 MB）。"
                            + "请降低 DPI、拆分源文件，或联系管理员调整上限",
                    pageIndex + 1, box.getWidth(), box.getHeight(), config.dpi, bytes / 1048576.0));
        }
    }

    /**
     * 加载前先校验 PDF 魔数（%PDF-），把"Error: End-of-File, expected line"这类底层解析错误
     * 转成可排查的明确提示：输入通常是错误页/空文件/损坏的 Base64，需检查上游数据源。
     */
    private static PDDocument loadPdf(byte[] pdfData) throws IOException {
        boolean valid = pdfData != null && pdfData.length >= 5
                && pdfData[0] == '%' && pdfData[1] == 'P' && pdfData[2] == 'D' && pdfData[3] == 'F';
        if (!valid) {
            StringBuilder head = new StringBuilder();
            if (pdfData != null) {
                for (int i = 0; i < Math.min(16, pdfData.length); i++) {
                    char c = (char) (pdfData[i] & 0xFF);
                    head.append(c >= 32 && c < 127 ? c : '.');
                }
            }
            throw new IOException("输入不是有效的 PDF：未找到 %PDF- 文件头（"
                    + (pdfData == null ? "输入为 null" : "大小 " + pdfData.length + " 字节，开头: " + head)
                    + "）。请检查上游数据源：HTTP 下载是否返回错误页、Base64 是否正确、文件是否完整");
        }
        return PDDocument.load(new ByteArrayInputStream(pdfData));
    }

    private static String extension(ImageFormat format) {
        return format == ImageFormat.JPEG ? "jpg" : "png";
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

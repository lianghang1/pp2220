package io.github.lianghang1.conflux.common.util;

import com.sun.net.httpserver.HttpServer;
import io.github.lianghang1.conflux.common.http.HttpResponse;
import io.github.lianghang1.conflux.common.http.HttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 二进制下载回归：getBinary/getRawBytes 保留原始字节（文本与二进制视图互不损坏），
 * 供版式文件固化（url → 字节）使用。
 */
class HttpBinaryDownloadTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] binary = new byte[512];
        // 造一组覆盖全字节值域的数据，验证 String 转码不会出现在字节链路里
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) (i % 256);
        }
        final byte[] payload = binary;
        server.createContext("/bin", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.createContext("/text", exchange -> {
            byte[] data = "中文文本响应".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        });
        server.createContext("/gone", exchange -> {
            exchange.sendResponseHeaders(404, -1);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void binaryBytesSurviveRoundTrip() {
        byte[] expected = new byte[512];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i % 256);
        }
        HttpResponse response = HttpTransport.getBinary(base + "/bin", null,
                5000L, 5000L, 5000L);
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.getRawBytes()).isEqualTo(expected);
        // 同一份字节，文本视图只是 UTF-8 解码，不会破坏二进制
        assertThat(response.getRawBytes()).hasSize(512);
    }

    @Test
    void textResponseSharesSameBytes() {
        HttpResponse response = HttpTransport.get(base + "/text", null);
        assertThat(response.getBody()).isEqualTo("中文文本响应");
        assertThat(response.getRawBytes())
                .isEqualTo("中文文本响应".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void non2xxStillReturnsRawResponse() {
        HttpResponse response = HttpTransport.getBinary(base + "/gone", null,
                5000L, 5000L, 5000L);
        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.getRawBytes()).isEmpty();
        assertThat(Arrays.toString(response.getRawBytes())).isNotNull();
    }
}

package com.sfi.conflux.file.engine;

import com.sfi.conflux.file.config.FileChannelConfig;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 channelCode 的 kebab-case（官方 yml 写法）与 camelCase（Java 字段名写法）
 * 均能映射到官方 Config，且 platform 实例名保持传入原值（与门面 setPlatform 路由一致）。
 *
 * @author lianghang
 */
class FileChannelCodeMappingTest {

    private Map<String, String> ossAttr() {
        Map<String, String> attr = new LinkedHashMap<String, String>();
        attr.put("access-key", "AK");
        attr.put("secret-key", "SK");
        attr.put("end-point", "oss-cn-hangzhou.aliyuncs.com");
        attr.put("bucket-name", "mybucket");
        return attr;
    }

    private FileStorageProperties apply(String channelCode) throws Exception {
        Method m = FileStorageRegistry.class.getDeclaredMethod(
                "applyConfig", FileStorageProperties.class, FileChannelConfig.class);
        m.setAccessible(true);
        FileStorageProperties props = new FileStorageProperties();
        m.invoke(null, props, new FileChannelConfig("demo", channelCode, ossAttr()));
        return props;
    }

    @Test
    void kebabChannelCodeShouldMapToOfficialConfig() throws Exception {
        // 官方 yml 写法 aliyun-oss → 反射字段 aliyunOss，platform 仍为原值 aliyun-oss
        FileStorageProperties props = apply("aliyun-oss");
        assertThat(props.getAliyunOss()).hasSize(1);
        FileStorageProperties.AliyunOssConfig c = props.getAliyunOss().get(0);
        assertThat(c.getPlatform()).isEqualTo("aliyun-oss");
        assertThat(c.getAccessKey()).isEqualTo("AK");
        assertThat(c.getSecretKey()).isEqualTo("SK");
        assertThat(c.getEndPoint()).isEqualTo("oss-cn-hangzhou.aliyuncs.com");
        assertThat(c.getBucketName()).isEqualTo("mybucket");
    }

    @Test
    void camelChannelCodeShouldStillWork() throws Exception {
        // 旧写法 aliyunOss 兼容：无 '-' 原样反射，platform 为 aliyunOss
        FileStorageProperties props = apply("aliyunOss");
        assertThat(props.getAliyunOss()).hasSize(1);
        assertThat(props.getAliyunOss().get(0).getPlatform()).isEqualTo("aliyunOss");
    }

    @Test
    void variousKebabSegmentsShouldNormalize() throws Exception {
        // 覆盖多段、含数字的官方段名：amazon-s3 / amazon-s3-v2 / go-fastdfs / local-plus
        assertThat(apply("amazon-s3").getAmazonS3()).hasSize(1);
        assertThat(apply("amazon-s3-v2").getAmazonS3V2()).hasSize(1);
        assertThat(apply("go-fastdfs").getGoFastdfs()).hasSize(1);
        assertThat(apply("local-plus").getLocalPlus()).hasSize(1);
    }
}

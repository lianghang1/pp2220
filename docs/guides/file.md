# 文件域使用指南

文件域内部**全量委托 [x-file-storage](https://x-file-storage.xuyanwu.cn/) 原始包**（`x-file-storage-core`，不用它的 starter），我们只保留两层：配置装载与出口门面。**不含任何存储平台实现代码**。通用契约见 [getting-started.md](../getting-started.md)。

## 1. 引入

### Spring Boot starter（推荐）

```xml
<dependency>
    <groupId>com.sfi.conflux</groupId>
    <artifactId>conflux-file-spring-boot-starter</artifactId>
    <version>请替换为实际版本</version>
</dependency>
```

starter 自动装配 `FileGateway`。

### 纯 jar / Spring MVC

引入 `conflux-file-core`（经 core 传递引入 x-file-storage 原始包），core 零 Spring 依赖：

```java
Map<String, String> attr = new LinkedHashMap<>();   // JDK8 不用 Map.of
attr.put("basePath", "/data/files");
attr.put("domain", "http://localhost/file/");
FileConfigProvider cp = new InMemoryFileConfigProvider()
        .register("demo", "local", attr);           // configId / channelCode(平台类型) / 扁平凭证
FileGateway gateway = FileGateway.builder().configProvider(cp).build();
```

## 2. 配置

配置路径 `conflux.file.configs.<configId>.<channelCode>`，与支付/发票/通知/登录域完全同构：

```yaml
conflux:
  file:
    configs:
      demo:
        local:                     # channelCode = x-file-storage 平台类型段名
          attr:                    # 扁平凭证，键名对齐官方 Config 字段(支持 kebab/camel)
            base-path: /data/files
            domain: http://localhost/file/
        aliyunOss:                 # 另一平台类型，同一结构
          attr:
            access-key: xxx
            secret-key: xxx
            bucket-name: my-bucket
            end-point: https://oss-cn-hangzhou.aliyuncs.com
```

- **`channelCode`** = x-file-storage 的**平台类型段名**（`local` / `aliyunOss` / `minio` …），与支付域的 `wechat` / `alipay` 语义一致（都是类型编码，不是实例名）。装载层反射 `FileStorageProperties` 的同名 List 段、取官方 Config 类，把 `attr` 转成该 Config 并注入实例名——**不硬编码平台清单**，官方新增平台段自动适配。
- **value 是自有扁平配置**（`attr` 键值包），**不含任何第三方官方类型**，与其余四域的 `ChannelProperties` 同构。平台 SDK（阿里云 OSS、MinIO 等）由使用方按需引入。
- **多实例**：同一平台类型下多个账号/实例用不同 `configId` 区分（与支付域服务商模式一致），如 `configs.prod-oss.aliyunOss` 与 `configs.bak-oss.aliyunOss`。
- 配置源每次读取，配置变更可重建 service；自定义 `FileConfigProvider` 可接 DB / 配置中心。

## 3. 文件操作

门面只暴露最小可用方法 + 已放开的两组，返回官方原始类型：

```java
FileInfo info = gateway.upload("demo", "local", bytes, "a.jpg");   // 上传
byte[] data = gateway.download("demo", "local", info).bytes();     // 下载
boolean ok   = gateway.exists("demo", "local", info);              // 存在性
gateway.delete("demo", "local", info);                             // 删除
String url   = gateway.generatePresignedUrl("demo", "local", info, new Date()); // 预签名(放开B)
gateway.copy("demo", "local", info);                               // 复制(放开C)
gateway.move("demo", "local", info);                               // 移动(放开C)
```

- `download` 返回官方 `Downloader`（`bytes()` / `file()` / `outputStream()`）；`copy` / `move` 返回官方预处理器供链式配置。
- **进阶能力走原生出口**：分片 / ACL / 元数据 / 列举 / 图片处理直接用官方 API——

```java
FileStorageService nativeSvc = gateway.nativeService("demo", "local");
```

## 4. FileRecorder 边界（重要）

我们**不实现、不托管** `FileRecorder`、不落库；门面一律以 `FileInfo` 为入参/返回，**不暴露 `getFileInfoByUrl` 及一切 `url → 元信息` 反查方法**（这些方法依赖 DB 记录，官方默认 `DefaultFileRecorder` 下会抛明确异常）。

需要按 url 查询的使用方：自行实现 `FileRecorder`，再走 `nativeService()` 原生能力。

## 5. 自定义平台

接入清单外的自定义平台走 [x-file-storage 官方扩展](https://x-file-storage.xuyanwu.cn/)：按其文档实现 `FileStorage`（可选 `FileStorageClientFactory`），把实例交给 `FileChannelProvider.of(fileStorage)` 声明即可。内置与自定义经同一个 `addFileStorage(...)` 注入，无需改框架代码。运行期也可用 `gateway.registerChannel(provider)` 增补、`gateway.registeredChannels()` 查看。

## 6. 排错

- `CONFIG_MISSING`：确认 `configId` + `channelCode` 与配置一致（内存实现按 `configId#channelCode` 索引）。
- 平台初始化失败：检查 channelCode 是否为合法平台类型段名、`attr` 键名是否对齐官方 Config 字段、平台 SDK 依赖是否引入。
- 调用 `url → 元信息` 抛异常：这是预期的（不托管 FileRecorder），改为保存 `FileInfo` 或自行实现 `FileRecorder`。
- 进阶能力缺失：用 `nativeService()` 走官方 API，不在门面层找。

> x-file-storage 版本在 BOM（`conflux-dependencies`）单源管理。

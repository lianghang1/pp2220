package com.sfi.conflux.pay.channels.wechat;

import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信 V2 XML 应答解析（无外部依赖的最小 DOM 解析，防 XXE 关闭外部实体）。
 *
 * @author lianghang
 */
final class WechatXmlParser {

    private WechatXmlParser() {
    }

    /** XML 文本 → 键值 Map（仅取一级子节点文本）。 */
    static Map<String, String> parse(String xml) {
        Map<String, String> result = new HashMap<String, String>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();
            org.w3c.dom.NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node node = children.item(i);
                if (node instanceof Element) {
                    result.put(node.getNodeName(), node.getTextContent());
                }
            }
            return result;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED,
                    "微信 XML 报文解析失败: {}", e.getMessage(), e);
        }
    }
}

package com.jiseong.homesense.batch.collector;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 페이지네이션·에러코드 판정에 필요한 header/resultCode(또는 게이트웨이 오류의 returnReasonCode),
 * totalCount만 가볍게 읽는다. item 파싱은 BAT-PRS-01(TradeXmlParser) 책임이라 여기서는 하지 않는다.
 */
final class OpenApiXmlReader {

    private OpenApiXmlReader() {
    }

    static PageMeta readMeta(String xml, String datasetId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE 방지 — 외부 Open API 응답이라도 DTD·외부 엔티티는 신뢰하지 않는다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            // 서비스키 미등록·만료 같은 게이트웨이 레벨 오류는 정상 응답(response/header/resultCode)과
            // 전혀 다른 봉투(OpenAPI_ServiceResponse/cmmMsgHeader/returnReasonCode)로 내려온다.
            // resultCode가 없으면 이 봉투로 폴백해야 30/31 등이 ABORT_BATCH로 정상 판정된다.
            String resultCode = textOf(document, "resultCode");
            if (resultCode == null) {
                resultCode = textOf(document, "returnReasonCode");
            }
            if (resultCode == null) {
                throw new OpenApiResponseException(
                        "dataset=" + datasetId + " 응답에 header/resultCode·returnReasonCode가 없다");
            }
            int totalCount = parseIntOrZero(textOf(document, "totalCount"));
            return new PageMeta(resultCode, totalCount);
        } catch (OpenApiResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenApiResponseException("dataset=" + datasetId + " 응답 XML을 읽을 수 없다", e);
        }
    }

    private static String textOf(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private static int parseIntOrZero(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

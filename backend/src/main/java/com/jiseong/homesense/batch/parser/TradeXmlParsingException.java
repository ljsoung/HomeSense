package com.jiseong.homesense.batch.parser;

/**
 * 응답 구조 자체(header/resultCode, XML 형식)가 잘못돼 item 단위로 넘어갈 수 없는 경우에 던진다.
 * 개별 item 하나만의 문제인 {@link MalformedTradeItemException}과 달리 응답 전체를 무효로 본다.
 */
public class TradeXmlParsingException extends RuntimeException {

    public TradeXmlParsingException(String message) {
        super(message);
    }

    public TradeXmlParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}

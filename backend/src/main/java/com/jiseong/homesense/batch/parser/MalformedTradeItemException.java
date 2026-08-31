package com.jiseong.homesense.batch.parser;

/**
 * item 하나가 필수 필드 누락 등으로 통합 스키마로 변환할 수 없을 때 던진다.
 * 호출부(BAT-SCH-01)는 이 예외를 건별로 잡아 해당 item만 skip·로깅하고 배치는 계속 진행한다.
 */
public class MalformedTradeItemException extends RuntimeException {

    public MalformedTradeItemException(String message) {
        super(message);
    }
}

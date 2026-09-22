package com.jiseong.homesense.common.mail;

/**
 * COM-CFG-01/AUTH-03. 이메일 발송의 최소 계약 — 수신자·제목·HTML 본문만 받는다. 현재 유일한 구현체는
 * {@link SesMailSender}이지만, 인터페이스로 분리해 두는 이유는 향후 BAT-MAIL-01(알림 이메일 발송,
 * 3차 확장 로드맵)이 이 계약을 그대로 재사용할 수 있게 하기 위함이다 — SES 클라이언트 조립·자격
 * 증명·리전 설정은 {@link com.jiseong.homesense.common.config.SesClientConfig}에 이미 있으므로
 * BAT-MAIL-01은 이 인터페이스만 주입받아 쓰면 된다.
 */
public interface MailSender {

    void send(String to, String subject, String htmlBody);
}

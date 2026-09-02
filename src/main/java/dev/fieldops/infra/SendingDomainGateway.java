package dev.fieldops.infra;

public interface SendingDomainGateway {
    String verifyDomain(String domain, String idempotencyKey);
    String sendFollowUp(String to, String subject, String html, String idempotencyKey);
}

package dev.fieldops.service;

import dev.fieldops.domain.WorkOrderFollowUp;
import dev.fieldops.infra.SendingDomainGateway;

import java.util.List;

public final class DomainOnboardingServiceTest {
    public static void main(String[] args) {
        pendingDnsDoesNotSendCustomerMail();
        verifiedDomainSendsCompletedOrderFollowUp();
        System.out.println("DomainOnboardingServiceTest: PASS");
    }

    private static void pendingDnsDoesNotSendCustomerMail() {
        FakeGateway gateway = new FakeGateway("pending");
        var result = new DomainOnboardingService(gateway).onboardAndNotify("dispatch.example", order());
        check(result.outcome() == DomainOnboardingService.Outcome.DNS_ACTION_REQUIRED,
                "pending DNS must request domain action");
        check(gateway.sendCount == 0, "customer mail must wait for verified DNS");
    }

    private static void verifiedDomainSendsCompletedOrderFollowUp() {
        FakeGateway gateway = new FakeGateway("verified");
        var result = new DomainOnboardingService(gateway).onboardAndNotify("dispatch.example", order());
        check(result.outcome() == DomainOnboardingService.Outcome.FOLLOW_UP_SENT,
                "verified domain must send follow-up");
        check("msg_2048".equals(result.messageId()), "message id must remain observable");
        check(gateway.lastHtml.contains("Photos recorded: 2"), "photo count must reach the notice");
    }

    private static WorkOrderFollowUp order() {
        return new WorkOrderFollowUp("WO-2048", "customer@example.com",
                WorkOrderFollowUp.DispatchStatus.COMPLETED, "Mina Patel",
                List.of("arrival-photo", "completed-panel-photo"), "Inspection completed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeGateway implements SendingDomainGateway {
        private final String status;
        private int sendCount;
        private String lastHtml;

        private FakeGateway(String status) { this.status = status; }
        @Override public String verifyDomain(String domain, String key) { return status; }
        @Override public String sendFollowUp(String to, String subject, String html, String key) {
            sendCount++;
            lastHtml = html;
            return "msg_2048";
        }
    }
}

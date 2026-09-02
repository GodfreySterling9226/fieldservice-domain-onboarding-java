package dev.fieldops.service;

import dev.fieldops.domain.WorkOrderFollowUp;
import dev.fieldops.infra.SendingDomainGateway;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class DomainOnboardingService {
    private final SendingDomainGateway email;

    public DomainOnboardingService(SendingDomainGateway email) {
        this.email = email;
    }

    public OnboardingResult onboardAndNotify(String domain, WorkOrderFollowUp workOrder) {
        String verificationKey = stableKey("domain:" + domain);
        String verificationStatus = email.verifyDomain(domain, verificationKey);
        if (!"verified".equalsIgnoreCase(verificationStatus)) {
            return new OnboardingResult(Outcome.DNS_ACTION_REQUIRED, verificationStatus, null);
        }

        String messageId = email.sendFollowUp(
                workOrder.customerEmail(),
                "Work order " + workOrder.workOrderId() + ": "
                        + workOrder.dispatchStatus().name().toLowerCase(Locale.ROOT),
                followUpHtml(workOrder),
                stableKey("follow-up:" + workOrder.workOrderId() + ":" + workOrder.dispatchStatus()));
        return new OnboardingResult(Outcome.FOLLOW_UP_SENT, verificationStatus, messageId);
    }

    private static String followUpHtml(WorkOrderFollowUp order) {
        return "<h1>Service update</h1><p>Technician: " + escape(order.technicianName())
                + "</p><p>Status: " + order.dispatchStatus() + "</p><p>Photos recorded: "
                + order.photoReferences().size() + "</p><p>" + escape(order.followUpNote()) + "</p>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String stableKey(String input) {
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public enum Outcome { DNS_ACTION_REQUIRED, FOLLOW_UP_SENT }
    public record OnboardingResult(Outcome outcome, String verificationStatus, String messageId) {}
}

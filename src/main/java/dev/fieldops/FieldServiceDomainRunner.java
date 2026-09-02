package dev.fieldops;

import dev.fieldops.config.EmailPlatformConfig;
import dev.fieldops.domain.WorkOrderFollowUp;
import dev.fieldops.infra.InfraiEmailClient;
import dev.fieldops.service.DomainOnboardingService;

import java.util.List;

public final class FieldServiceDomainRunner {
    private FieldServiceDomainRunner() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected: <sending-domain> <customer-email>");
        }
        var order = new WorkOrderFollowUp(
                "WO-2048", args[1], WorkOrderFollowUp.DispatchStatus.COMPLETED,
                "Mina Patel", List.of("arrival-photo", "completed-panel-photo"),
                "Electrical panel inspection completed. No further visit is scheduled.");
        var service = new DomainOnboardingService(
                new InfraiEmailClient(EmailPlatformConfig.fromEnvironment()));
        var result = service.onboardAndNotify(args[0], order);
        System.out.printf("outcome=%s verification.status=%s message_id=%s%n",
                result.outcome(), result.verificationStatus(), result.messageId());
    }
}

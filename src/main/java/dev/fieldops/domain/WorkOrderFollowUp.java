package dev.fieldops.domain;

import java.util.List;

public record WorkOrderFollowUp(
        String workOrderId,
        String customerEmail,
        DispatchStatus dispatchStatus,
        String technicianName,
        List<String> photoReferences,
        String followUpNote) {

    public enum DispatchStatus { DISPATCHED, ON_SITE, COMPLETED }

    public WorkOrderFollowUp {
        photoReferences = List.copyOf(photoReferences);
    }
}

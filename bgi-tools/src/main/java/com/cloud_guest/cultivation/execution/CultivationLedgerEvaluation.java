package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;

public record CultivationLedgerEvaluation(
        CultivationPlanRevisionResponse ledger,
        CultivationMaterialCraftingPlan craftingPlan
) {
}

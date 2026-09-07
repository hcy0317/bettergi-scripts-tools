package com.cloud_guest.cultivation.execution;

import com.cloud_guest.cultivation.plan.CultivationPlanRevisionResponse;
import java.util.Set;

public record CultivationLedgerEvaluation(
        CultivationPlanRevisionResponse ledger,
        CultivationMaterialCraftingPlan craftingPlan,
        Set<String> reconciliationMaterials
) {
    public CultivationLedgerEvaluation {
        reconciliationMaterials = Set.copyOf(reconciliationMaterials);
    }

    public CultivationLedgerEvaluation(CultivationPlanRevisionResponse ledger,
                                      CultivationMaterialCraftingPlan craftingPlan) {
        this(ledger, craftingPlan, Set.of());
    }
}

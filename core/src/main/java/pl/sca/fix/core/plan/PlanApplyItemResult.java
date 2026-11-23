package pl.sca.fix.core.plan;

/** Result of one PlanItem execution. */
public record PlanApplyItemResult(String itemId, String status, boolean changed,
                                  String diff, String errorMessage) { }

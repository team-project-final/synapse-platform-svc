package com.synapse.platform.billing.domain;

public enum PlanCode {
    FREE, PRO, TEAM, ENTERPRISE;

    public String value() {
        return name().toLowerCase();
    }
}

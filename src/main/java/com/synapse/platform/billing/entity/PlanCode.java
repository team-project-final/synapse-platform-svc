package com.synapse.platform.billing.entity;

public enum PlanCode {
    FREE, PRO, TEAM, ENTERPRISE;

    public String value() {
        return name().toLowerCase();
    }
}

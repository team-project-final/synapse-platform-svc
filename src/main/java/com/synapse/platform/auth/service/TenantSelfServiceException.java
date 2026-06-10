package com.synapse.platform.auth.service;

import com.synapse.platform.global.exception.BusinessException;

public class TenantSelfServiceException extends BusinessException {

    private TenantSelfServiceException(String errorCode, int status, String message) {
        super(errorCode, status, message);
    }

    public static TenantSelfServiceException userNotFound() {
        return new TenantSelfServiceException("PLAT-TENANT-001", 404, "User not found");
    }

    public static TenantSelfServiceException tenantNotFound() {
        return new TenantSelfServiceException("PLAT-TENANT-002", 404, "Tenant not found");
    }

    public static TenantSelfServiceException membershipRequired() {
        return new TenantSelfServiceException("PLAT-TENANT-003", 403, "Tenant membership is required");
    }

    public static TenantSelfServiceException adminRequired() {
        return new TenantSelfServiceException("PLAT-TENANT-004", 403, "Tenant admin role is required");
    }

    public static TenantSelfServiceException invalidRole() {
        return new TenantSelfServiceException("PLAT-TENANT-005", 400, "Tenant role is invalid");
    }

    public static TenantSelfServiceException selfMutationNotAllowed() {
        return new TenantSelfServiceException("PLAT-TENANT-006", 400, "Cannot modify your own tenant membership");
    }

    public static TenantSelfServiceException lastOwnerRemovalNotAllowed() {
        return new TenantSelfServiceException("PLAT-TENANT-007", 409, "Cannot remove the last tenant owner");
    }

    public static TenantSelfServiceException memberNotFound() {
        return new TenantSelfServiceException("PLAT-TENANT-008", 404, "Tenant member not found");
    }

    public static TenantSelfServiceException invalidTenantName() {
        return new TenantSelfServiceException("PLAT-TENANT-009", 400, "Tenant name is required");
    }

    public static TenantSelfServiceException invalidSettings() {
        return new TenantSelfServiceException("PLAT-TENANT-010", 400, "Tenant settings are invalid");
    }

    public static TenantSelfServiceException ownerRoleChangeNotAllowed() {
        return new TenantSelfServiceException("PLAT-TENANT-011", 409, "Tenant owner role cannot be changed here");
    }

    public static TenantSelfServiceException ownerMutationNotAllowed() {
        return new TenantSelfServiceException(
                "PLAT-TENANT-012",
                403,
                "Tenant owner can only be changed by another owner");
    }
}

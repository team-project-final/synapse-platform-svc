package io.synapse.platform.billing.exception;

import io.synapse.platform.shared.exception.BusinessException;

public class BillingException extends BusinessException {

    public BillingException(String errorCode, int status, String message) {
        super(errorCode, status, message);
    }
}

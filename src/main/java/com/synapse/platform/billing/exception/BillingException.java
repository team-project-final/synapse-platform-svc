package com.synapse.platform.billing.exception;

import com.synapse.platform.shared.exception.BusinessException;

public class BillingException extends BusinessException {

    public BillingException(String errorCode, int status, String message) {
        super(errorCode, status, message);
    }
}

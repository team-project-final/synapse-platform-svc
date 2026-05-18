package io.synapse.platform.billing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingPlaceholder {

    @GetMapping("/billing/health")
    public String health() {
        return "billing-service placeholder";
    }
}

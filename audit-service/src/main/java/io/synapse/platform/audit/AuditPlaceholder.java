package io.synapse.platform.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditPlaceholder {

    @GetMapping("/audit/health")
    public String health() {
        return "audit-service placeholder";
    }
}

package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.PlanQuota;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanQuotaRepository extends JpaRepository<PlanQuota, String> {
}

package com.synapse.platform.admin.repository;

import com.synapse.platform.admin.entity.GdprDataRequest;
import com.synapse.platform.admin.entity.GdprDataRequestStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GdprDataRequestRepository
        extends JpaRepository<GdprDataRequest, UUID>, JpaSpecificationExecutor<GdprDataRequest> {

    long countByStatusIn(Collection<GdprDataRequestStatus> statuses);
}

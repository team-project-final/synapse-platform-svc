package com.synapse.platform.admin.service;

import com.synapse.platform.admin.dto.AdminDataRequestActionRequest;
import com.synapse.platform.admin.dto.AdminDataRequestCreateRequest;
import com.synapse.platform.admin.dto.AdminDataRequestResponse;
import com.synapse.platform.admin.dto.AdminDataRequestSearchRequest;
import com.synapse.platform.admin.entity.GdprDataRequest;
import com.synapse.platform.admin.entity.GdprDataRequestStatus;
import com.synapse.platform.admin.repository.GdprDataRequestRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDataRequestService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<GdprDataRequestStatus> OPEN_STATUSES = List.of(
            GdprDataRequestStatus.PENDING,
            GdprDataRequestStatus.PROCESSING);

    private final GdprDataRequestRepository gdprDataRequestRepository;
    private final UserApi userApi;

    public AdminDataRequestService(
            GdprDataRequestRepository gdprDataRequestRepository,
            UserApi userApi) {
        this.gdprDataRequestRepository = gdprDataRequestRepository;
        this.userApi = userApi;
    }

    @Transactional(readOnly = true)
    public Page<AdminDataRequestResponse> listRequests(AdminDataRequestSearchRequest request) {
        Pageable pageable = PageRequest.of(
                Math.max(0, request.page()),
                Math.max(1, Math.min(request.size(), MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "receivedAt"));
        return gdprDataRequestRepository.findAll(specification(request), pageable)
                .map(AdminDataRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminDataRequestResponse getRequest(UUID id) {
        return AdminDataRequestResponse.from(findRequest(id));
    }

    @Transactional
    public AdminDataRequestResponse createRequest(AdminDataRequestCreateRequest request) {
        UserInfo user = userApi.findById(request.userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        GdprDataRequest created = GdprDataRequest.create(
                user.id(),
                user.email(),
                user.displayName(),
                request.type(),
                request.reason(),
                now());
        return AdminDataRequestResponse.from(gdprDataRequestRepository.save(created));
    }

    @Transactional
    public AdminDataRequestResponse applyAction(UUID id, AdminDataRequestActionRequest request) {
        GdprDataRequest dataRequest = findRequest(id);
        try {
            switch (request.action()) {
                case APPROVE -> dataRequest.approve(request.reason(), now());
                case EXECUTE -> dataRequest.execute(request.reason(), now());
                case REJECT -> dataRequest.reject(request.reason(), now());
            }
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return AdminDataRequestResponse.from(dataRequest);
    }

    @Transactional(readOnly = true)
    public long countOpenRequests() {
        return gdprDataRequestRepository.countByStatusIn(OPEN_STATUSES);
    }

    private Specification<GdprDataRequest> specification(AdminDataRequestSearchRequest request) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (request.status() != null && !request.status().isBlank()) {
                predicates.add(builder.equal(root.get("status"), status(request.status())));
            }
            if (request.q() != null && !request.q().isBlank()) {
                String keyword = "%" + request.q().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("userEmail")), keyword),
                        builder.like(builder.lower(root.get("userDisplayName")), keyword)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private GdprDataRequestStatus status(String value) {
        try {
            return GdprDataRequestStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid data request status: " + value);
        }
    }

    private GdprDataRequest findRequest(UUID id) {
        return gdprDataRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Data request not found"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}

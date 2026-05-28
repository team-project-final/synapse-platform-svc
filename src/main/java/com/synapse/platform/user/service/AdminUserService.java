package com.synapse.platform.user.service;

import com.synapse.platform.user.api.UserSessionsRevocationRequested;
import com.synapse.platform.user.dto.request.AdminUserSearchRequest;
import com.synapse.platform.user.dto.response.AdminUserResponse;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserStatus;
import com.synapse.platform.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminUserService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(AdminUserSearchRequest request) {
        validateStatus(request.status());
        Pageable pageable = PageRequest.of(
                Math.max(0, request.page()),
                Math.max(1, Math.min(request.size(), MAX_PAGE_SIZE)));
        return userRepository.findAll(specification(request), pageable)
                .map(AdminUserResponse::from);
    }

    @Transactional
    public void suspendUser(UUID userId, UUID adminId) {
        assertNotSelf(userId, adminId);
        User user = findUser(userId);
        user.suspend();
        eventPublisher.publishEvent(new UserSessionsRevocationRequested(userId));
    }

    @Transactional
    public void activateUser(UUID userId) {
        User user = findUser(userId);
        user.activate();
    }

    @Transactional
    public void deleteUser(UUID userId, UUID adminId) {
        assertNotSelf(userId, adminId);
        User user = findUser(userId);
        user.softDelete();
        eventPublisher.publishEvent(new UserSessionsRevocationRequested(userId));
    }

    private Specification<User> specification(AdminUserSearchRequest request) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (request.q() != null && !request.q().isBlank()) {
                String keyword = "%" + request.q().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("email")), keyword),
                        builder.like(builder.lower(root.get("displayName")), keyword)));
            }
            if (request.status() != null && !request.status().isBlank()) {
                predicates.add(builder.equal(root.get("status"), userStatus(request.status())));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validateStatus(String status) {
        if (status != null && !status.isBlank()) {
            userStatus(status);
        }
    }

    private UserStatus userStatus(String status) {
        try {
            return UserStatus.fromDbValue(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidUserStatusFilterException(status);
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private void assertNotSelf(UUID userId, UUID adminId) {
        if (userId.equals(adminId)) {
            throw new AdminSelfActionException();
        }
    }
}

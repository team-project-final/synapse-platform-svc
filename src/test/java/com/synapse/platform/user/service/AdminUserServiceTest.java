package com.synapse.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.user.api.UserSessionsRevocationRequested;
import com.synapse.platform.user.dto.request.AdminUserSearchRequest;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserStatus;
import com.synapse.platform.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void listUsers_shouldMapUserResponses() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(java.util.List.of(user(userId))));

        Page<?> result = service().listUsers(new AdminUserSearchRequest("user", "active", 0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listUsers_invalidStatus_shouldThrowBadRequest() {
        assertThatThrownBy(() -> service().listUsers(new AdminUserSearchRequest(null, "invalid", 0, 20)))
                .isInstanceOf(InvalidUserStatusFilterException.class);
    }

    @Test
    void suspendUser_otherUser_shouldSuspendAndDeleteSessions() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = user(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        service().suspendUser(userId, adminId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(eventPublisher).publishEvent(new UserSessionsRevocationRequested(userId));
    }

    @Test
    void suspendUser_self_shouldThrowBadRequestAndNotDeleteSessions() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service().suspendUser(userId, userId))
                .isInstanceOf(AdminSelfActionException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteUser_otherUser_shouldSoftDeleteAndDeleteSessions() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = user(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        service().deleteUser(userId, adminId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).isEqualTo("deleted_" + userId + "@deleted.invalid");
        verify(eventPublisher).publishEvent(new UserSessionsRevocationRequested(userId));
    }

    @Test
    void activateUser_shouldActivateWithoutDeletingSessions() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        user.suspend();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        service().activateUser(userId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private AdminUserService service() {
        return new AdminUserService(userRepository, eventPublisher);
    }

    private static User user(UUID userId) {
        User user = User.ofEmailPassword("user@example.com", "user", "$2a$hash", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}

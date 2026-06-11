package com.synapse.platform.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.admin.dto.AdminDataRequestActionRequest;
import com.synapse.platform.admin.dto.AdminDataRequestActionRequest.Action;
import com.synapse.platform.admin.dto.AdminDataRequestCreateRequest;
import com.synapse.platform.admin.dto.AdminDataRequestResponse;
import com.synapse.platform.admin.dto.AdminDataRequestSearchRequest;
import com.synapse.platform.admin.entity.GdprDataRequest;
import com.synapse.platform.admin.entity.GdprDataRequestStatus;
import com.synapse.platform.admin.entity.GdprDataRequestType;
import com.synapse.platform.admin.repository.GdprDataRequestRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminDataRequestServiceTest {

    @Mock
    private GdprDataRequestRepository gdprDataRequestRepository;

    @Mock
    private UserApi userApi;

    @Test
    void createRequest_shouldSnapshotUserAndPersistPendingRequest() {
        UUID userId = UUID.randomUUID();
        UserInfo user = new UserInfo(userId, "user@example.com", "User", UUID.randomUUID());
        given(userApi.findById(userId)).willReturn(Optional.of(user));
        given(gdprDataRequestRepository.save(any(GdprDataRequest.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AdminDataRequestResponse result = service().createRequest(new AdminDataRequestCreateRequest(
                userId,
                GdprDataRequestType.DATA_EXPORT,
                "export request"));

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.userEmail()).isEqualTo("user@example.com");
        assertThat(result.status()).isEqualTo(GdprDataRequestStatus.PENDING);
        assertThat(result.type()).isEqualTo(GdprDataRequestType.DATA_EXPORT);
        assertThat(result.daysRemaining()).isEqualTo(30);

        ArgumentCaptor<GdprDataRequest> captor = ArgumentCaptor.forClass(GdprDataRequest.class);
        verify(gdprDataRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("export request");
    }

    @Test
    void applyAction_approveExecuteReject_shouldChangeStatus() {
        UUID id = UUID.randomUUID();
        GdprDataRequest request = request(id);
        given(gdprDataRequestRepository.findById(id)).willReturn(Optional.of(request));

        AdminDataRequestResponse approved = service().applyAction(id, new AdminDataRequestActionRequest(
                Action.APPROVE,
                "approved"));
        AdminDataRequestResponse completed = service().applyAction(id, new AdminDataRequestActionRequest(
                Action.EXECUTE,
                "done"));

        assertThat(approved.status()).isEqualTo(GdprDataRequestStatus.PROCESSING);
        assertThat(completed.status()).isEqualTo(GdprDataRequestStatus.COMPLETED);
        assertThat(request.getExecutionLog()).contains("Request approved", "Request executed");
    }

    @Test
    void applyAction_executePendingRequest_shouldReturnConflict() {
        UUID id = UUID.randomUUID();
        given(gdprDataRequestRepository.findById(id)).willReturn(Optional.of(request(id)));

        assertThatThrownBy(() -> service().applyAction(id, new AdminDataRequestActionRequest(
                Action.EXECUTE,
                "bad order")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void applyAction_executeDataErasureRequest_shouldReturnConflict() {
        UUID id = UUID.randomUUID();
        GdprDataRequest request = request(id, GdprDataRequestType.DATA_ERASURE);
        request.approve("approved", OffsetDateTime.parse("2026-06-11T10:01:00Z"));
        given(gdprDataRequestRepository.findById(id)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service().applyAction(id, new AdminDataRequestActionRequest(
                Action.EXECUTE,
                "delete user data")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("dedicated deletion workflow");
        assertThat(request.getStatus()).isEqualTo(GdprDataRequestStatus.PROCESSING);
        assertThat(request.getExecutionLog()).doesNotContain("Request executed");
    }

    @Test
    void listRequests_shouldQueryRepositoryWithPageable() {
        given(gdprDataRequestRepository.findAll(
                ArgumentMatchers.<Specification<GdprDataRequest>>any(),
                any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(request(UUID.randomUUID()))));

        var result = service().listRequests(new AdminDataRequestSearchRequest("pending", "user", 0, 20));

        assertThat(result.getContent()).hasSize(1);
        verify(gdprDataRequestRepository)
                .findAll(ArgumentMatchers.<Specification<GdprDataRequest>>any(), any(Pageable.class));
    }

    @Test
    void countOpenRequests_shouldCountPendingAndProcessing() {
        given(gdprDataRequestRepository.countByStatusIn(any())).willReturn(3L);

        assertThat(service().countOpenRequests()).isEqualTo(3);
    }

    private AdminDataRequestService service() {
        return new AdminDataRequestService(gdprDataRequestRepository, userApi);
    }

    private static GdprDataRequest request(UUID id) {
        return request(id, GdprDataRequestType.DATA_EXPORT);
    }

    private static GdprDataRequest request(UUID id, GdprDataRequestType type) {
        GdprDataRequest request = GdprDataRequest.create(
                UUID.randomUUID(),
                "user@example.com",
                "User",
                type,
                "reason",
                OffsetDateTime.parse("2026-06-11T10:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", id);
        return request;
    }
}

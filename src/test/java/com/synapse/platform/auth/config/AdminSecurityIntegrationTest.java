package com.synapse.platform.auth.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.billing.service.AdminTenantService;
import com.synapse.platform.user.dto.request.AdminUserSearchRequest;
import com.synapse.platform.user.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AdminSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private AdminTenantService adminTenantService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void adminUsers_withoutAuthentication_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUsers_nonAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUsers_admin_shouldReturnOk() throws Exception {
        given(adminUserService.listUsers(any(AdminUserSearchRequest.class)))
                .willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(adminUserService).listUsers(new AdminUserSearchRequest(null, null, 0, 20));
    }

    @Test
    void adminTenants_nonAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTenants_admin_shouldReturnOk() throws Exception {
        given(adminTenantService.listTenants(anyInt(), anyInt()))
                .willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/api/v1/admin/tenants")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(adminTenantService).listTenants(0, 20);
    }
}

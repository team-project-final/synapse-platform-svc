package com.synapse.platform.auth.service;

import com.synapse.platform.auth.dto.OAuthConnectionResponse;
import com.synapse.platform.auth.entity.OAuthIdentity;
import com.synapse.platform.auth.exception.OAuthConnectionException;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.user.api.UserApi;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthConnectionService {

    private final OAuthIdentityRepository oauthIdentityRepository;
    private final UserApi userApi;

    public OAuthConnectionService(OAuthIdentityRepository oauthIdentityRepository, UserApi userApi) {
        this.oauthIdentityRepository = oauthIdentityRepository;
        this.userApi = userApi;
    }

    @Transactional(readOnly = true)
    public List<OAuthConnectionResponse> listConnections(UUID userId) {
        return oauthIdentityRepository.findAllByUserId(userId).stream()
                .map(identity -> new OAuthConnectionResponse(identity.getProvider(), identity.getEmail()))
                .toList();
    }

    @Transactional
    public void unlink(UUID userId, String provider) {
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        List<OAuthIdentity> identities = oauthIdentityRepository.findAllByUserIdForUpdate(userId);
        OAuthIdentity identity = identities.stream()
                .filter(candidate -> normalizedProvider.equals(candidate.getProvider().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> OAuthConnectionException.notFound(provider));
        if (!userApi.hasPasswordLogin(userId) && identities.size() <= 1) {
            throw OAuthConnectionException.lastLoginMethod();
        }
        oauthIdentityRepository.delete(identity);
    }
}

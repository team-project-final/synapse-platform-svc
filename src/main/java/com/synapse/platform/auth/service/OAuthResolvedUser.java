package com.synapse.platform.auth.service;

import com.synapse.platform.user.api.UserInfo;

public record OAuthResolvedUser(UserInfo user, boolean newUser) {
}

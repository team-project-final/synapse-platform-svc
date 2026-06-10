package com.synapse.platform.user.api;

import java.time.OffsetDateTime;

public interface UserAnalyticsApi {

    UserAnalyticsSnapshot getUserAnalytics(OffsetDateTime now);
}

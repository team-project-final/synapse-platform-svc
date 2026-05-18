@org.springframework.modulith.ApplicationModule(
    displayName = "Auth",
    allowedDependencies = {"shared", "shared::exception", "shared::crypto", "user", "user::domain", "user::repository"}
)
package com.synapse.platform.auth;

@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "user::api",
                "auth::tenant-api",
                "billing::api",
                "notification::api",
                "audit::api"
        })
package com.synapse.platform.admin;

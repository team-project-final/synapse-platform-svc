package com.synapse.platform.admin.repository;

import com.synapse.platform.admin.entity.AdminSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSettingRepository extends JpaRepository<AdminSetting, String> {
}

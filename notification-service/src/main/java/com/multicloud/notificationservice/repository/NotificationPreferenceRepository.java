package com.multicloud.notificationservice.repository;

import com.multicloud.notificationservice.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {
}

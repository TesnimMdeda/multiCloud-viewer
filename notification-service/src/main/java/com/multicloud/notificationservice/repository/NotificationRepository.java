package com.multicloud.notificationservice.repository;

import com.multicloud.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findAllByUserEmailOrderByCreatedAtDesc(String userEmail);

    Page<Notification> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    long countByUserEmailAndReadFalse(String userEmail);

    /**
     * Single-row mark-as-read: one UPDATE instead of SELECT + UPDATE.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.read = false")
    int markAsReadById(@Param("id") String id);

    /**
     * Bulk mark-all-as-read: one UPDATE instead of SELECT-all + loop + saveAll.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userEmail = :userEmail AND n.read = false")
    int markAllAsReadByUserEmail(@Param("userEmail") String userEmail);
}

package com.team03.ticketmon.notification.repository;

import com.team03.ticketmon.notification.domain.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}

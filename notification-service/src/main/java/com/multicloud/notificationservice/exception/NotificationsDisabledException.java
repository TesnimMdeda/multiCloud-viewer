package com.multicloud.notificationservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "Notifications are disabled for this user")
public class NotificationsDisabledException extends RuntimeException {
    public NotificationsDisabledException(String message) {
        super(message);
    }
}

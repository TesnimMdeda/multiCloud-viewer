package com.multicloud.cloudprofileservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/api/notifications/send")
    void sendNotification(@RequestBody NotificationRequest request);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class NotificationRequest {
        private String userEmail;
        private String title;
        private String message;
        private String type;
    }
}

package com.multicloud.cloudprofileservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class CloudProfileServiceApplication {

    static {
        System.setProperty("oci.sdk.http.provider", "jersey3");
    }

    public static void main(String[] args) {
        SpringApplication.run(CloudProfileServiceApplication.class, args);
    }
}
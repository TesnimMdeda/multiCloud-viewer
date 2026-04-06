package com.multicloud.authservice.entity;

public enum Role {
    SUPER_ADMIN,
    ADMIN,
    CLIENT;

    public boolean canCreate(Role targetRole) {
        return switch (this) {
            case SUPER_ADMIN -> true;
            case ADMIN -> targetRole == CLIENT;
            case CLIENT -> false;
        };
    }
}
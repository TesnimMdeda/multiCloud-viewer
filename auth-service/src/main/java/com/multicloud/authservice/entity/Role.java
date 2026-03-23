package com.multicloud.authservice.entity;

public enum Role {
    SUPER_ADMIN,
    ADMIN,
    CLIENT;

    public boolean canCreate(Role targetRole) {
        return switch (this) {
            case SUPER_ADMIN -> targetRole == ADMIN || targetRole == CLIENT;
            case ADMIN       -> targetRole == CLIENT;
            case CLIENT      -> false;
        };
    }
}
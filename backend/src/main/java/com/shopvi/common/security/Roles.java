package com.shopvi.common.security;

/**
 * Tên role dùng trong JWT claim {@value #CLAIM} và trong {@code hasRole(...)}.
 */
public final class Roles {

    public static final String CLAIM = "role";
    public static final String BUYER = "BUYER";
    public static final String SELLER = "SELLER";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}

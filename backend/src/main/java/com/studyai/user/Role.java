package com.studyai.user;

public enum Role {
    STUDENT,
    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}

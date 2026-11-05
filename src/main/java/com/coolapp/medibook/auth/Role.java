package com.coolapp.medibook.auth;

public enum Role {
    PATIENT, DOCTOR, RECEPTIONIST, ADMIN, SUPER_ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isStaff() {
        return this != PATIENT;
    }

    public boolean requiresTwoFactorOption() {
        return this == DOCTOR || this == ADMIN || this == SUPER_ADMIN;
    }
}

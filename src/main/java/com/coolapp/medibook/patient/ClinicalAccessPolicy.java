package com.coolapp.medibook.patient;

import com.coolapp.medibook.auth.Role;

/**
 * Need-to-know rule: clinical data (notes, diagnoses, prescriptions, history) is visible only to
 * the patient (or their guardian) and to doctors treating them. Receptionists and admins never.
 */
public final class ClinicalAccessPolicy {

    private ClinicalAccessPolicy() {}

    public static boolean canViewClinical(Role role, boolean selfOrGuardian, boolean treatingDoctor) {
        return switch (role) {
            case PATIENT -> selfOrGuardian;
            case DOCTOR -> treatingDoctor;
            case RECEPTIONIST, ADMIN, SUPER_ADMIN -> false;
        };
    }

    /** Medical history can be edited by the patient/guardian or a treating doctor. */
    public static boolean canEditHistory(Role role, boolean selfOrGuardian, boolean treatingDoctor) {
        return canViewClinical(role, selfOrGuardian, treatingDoctor);
    }

    /** Front desk may see demographics (to book and check in) but not clinical data. */
    public static boolean canViewDemographics(Role role, boolean selfOrGuardian, boolean treatingDoctor) {
        return switch (role) {
            case PATIENT -> selfOrGuardian;
            case DOCTOR -> treatingDoctor;
            case RECEPTIONIST, ADMIN, SUPER_ADMIN -> true;
        };
    }
}

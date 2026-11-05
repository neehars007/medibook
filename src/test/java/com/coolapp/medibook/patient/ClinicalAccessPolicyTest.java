package com.coolapp.medibook.patient;

import com.coolapp.medibook.auth.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalAccessPolicyTest {

    @Test
    void patientSeesOnlyOwnRecords() {
        assertThat(ClinicalAccessPolicy.canViewClinical(Role.PATIENT, true, false)).isTrue();
        assertThat(ClinicalAccessPolicy.canViewClinical(Role.PATIENT, false, false)).isFalse();
    }

    @Test
    void doctorNeedsTreatingRelationship() {
        assertThat(ClinicalAccessPolicy.canViewClinical(Role.DOCTOR, false, true)).isTrue();
        assertThat(ClinicalAccessPolicy.canViewClinical(Role.DOCTOR, false, false)).isFalse();
    }

    @Test
    void receptionAndAdminNeverSeeClinicalData() {
        for (Role r : new Role[]{Role.RECEPTIONIST, Role.ADMIN, Role.SUPER_ADMIN}) {
            assertThat(ClinicalAccessPolicy.canViewClinical(r, true, true)).isFalse();
            assertThat(ClinicalAccessPolicy.canEditHistory(r, true, true)).isFalse();
            assertThat(ClinicalAccessPolicy.canViewDemographics(r, false, false)).isTrue();
        }
    }
}

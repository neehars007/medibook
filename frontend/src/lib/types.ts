// Mirrors the Spring Boot DTOs (see backend *Dtos.java).
export type Role = 'PATIENT' | 'DOCTOR' | 'RECEPTIONIST' | 'ADMIN' | 'SUPER_ADMIN'
export type Mode = 'IN_PERSON' | 'VIDEO'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNDISCLOSED'
export type PaymentMode = 'ONLINE' | 'PAY_AT_CLINIC' | 'FREE'
export type AppointmentStatus =
  | 'PENDING_PAYMENT' | 'CONFIRMED' | 'CHECKED_IN' | 'IN_CONSULTATION'
  | 'COMPLETED' | 'EXPIRED' | 'CANCELLED' | 'NO_SHOW'

export interface AuthResponse { accessToken: string; expiresIn: number; userId: string; role: Role; email: string }

export interface Page<T> { content: T[]; page: { size: number; number: number; totalElements: number; totalPages: number } }

export interface Clinic {
  name: string; address: string | null; phone: string | null; timezone: string; logoUrl: string | null
  about: string | null; emergencyNotice: string | null; paymentPolicy: 'ONLINE_ONLY' | 'PAY_AT_CLINIC_ONLY' | 'EITHER'
}

export interface Specialty { id: string; name: string; description: string | null; icon: string | null }

export interface Doctor {
  id: string; fullName: string; gender: Gender | null; specialtyId: string; specialtyName: string | null
  qualifications: string | null; experienceYears: number; languages: string[]; bio: string | null
  consultationFee: number; photoUrl: string | null; rating: number | null; reviewCount: number
  nextAvailableSlot: string | null; active: boolean
}

export interface AppointmentType {
  id: string; specialtyId: string; name: string; durationMinutes: number; fee: number | null
  requiresRoomType: string | null; followUp: boolean; followUpWindowDays: number | null
  mode: Mode | null; intakeRequired: boolean; active: boolean
}

export interface Slot { doctorId: string; doctorName: string; startAt: string; endAt: string; mode: Mode }

export interface Hold { holdId: string; doctorId: string; startAt: string; endAt: string; mode: Mode; expiresAt: string }

export interface Patient {
  id: string; guardianPatientId: string | null; relationship: string | null; fullName: string; dob: string
  gender: Gender; phone: string | null; address: string | null; emergencyContactName: string | null
  emergencyContactPhone: string | null; bloodGroup: string | null; insuranceProvider: string | null; insurancePolicyNo: string | null
}

export interface IntakeForm {
  reason: string; symptoms: string; durationOfSymptoms: string; currentMeds: string
  allergiesConfirmed: boolean; extraNotes: string
}

export interface BookingResponse {
  appointmentId: string; status: AppointmentStatus; startAt: string; endAt: string; doctorName: string
  mode: Mode; paymentMode: PaymentMode; amount: number; paymentClientSecret: string | null
  clinicName: string; clinicAddress: string | null; mapUrl: string | null; videoVisit: boolean
}

export interface Appointment {
  id: string; patientId: string; patientName: string | null; doctorId: string; doctorName: string | null
  appointmentTypeId: string; typeName: string | null; startAt: string; endAt: string; mode: Mode
  status: AppointmentStatus; source: string; tokenNumber: number | null; paymentMode: PaymentMode
  paymentStatus: string | null; cancelledBy: string | null; cancelReason: string | null; roomName: string | null
  checkedInAt: string | null; hasVisitSummary: boolean
}

export interface Vitals { systolic?: number; diastolic?: number; pulse?: number; temperatureC?: number; weightKg?: number; spo2?: number }
export interface RxItem { medicine: string; dosage: string | null; frequency: string | null; durationDays: number | null; instructions: string | null }

export interface VisitSummary {
  appointmentId: string; visitDate: string; doctorName: string | null; typeName: string | null
  note: {
    id: string; chiefComplaint: string | null; findings: string | null; vitals: Vitals | null; diagnosis: string | null
    icdCode: string | null; advice: string | null; recommendedTests: string | null; followUpInDays: number | null
    finalizedAt: string | null; prescription: RxItem[]
    addendums: { id: string; text: string; createdAt: string; doctorName: string | null }[]
  }
}

-- V4__create_appointments_table.sql
-- Table for patient-doctor appointments.

CREATE TABLE appointments (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT      NOT NULL,
    doctor_id        BIGINT      NOT NULL,
    appointment_date DATE        NOT NULL,
    appointment_time TIME        NOT NULL,
    reason           VARCHAR(255) NOT NULL,
    notes            VARCHAR(2000),
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL,

    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patient_profiles (id),
    CONSTRAINT fk_appointments_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctor_profiles (id)
);

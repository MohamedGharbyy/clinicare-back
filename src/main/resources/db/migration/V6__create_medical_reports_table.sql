-- V6__create_medical_reports_table.sql
-- Table for medical reports created by doctors for their patients.

CREATE TABLE medical_reports (
    id            BIGSERIAL PRIMARY KEY,
    patient_id    BIGINT      NOT NULL,
    doctor_id     BIGINT      NOT NULL,
    appointment_id BIGINT,
    diagnosis     VARCHAR(255) NOT NULL,
    symptoms      VARCHAR(2000),
    notes         VARCHAR(2000),
    report_date   DATE        NOT NULL,
    created_at    TIMESTAMP   NOT NULL,

    CONSTRAINT fk_medical_reports_patient FOREIGN KEY (patient_id) REFERENCES patient_profiles (id),
    CONSTRAINT fk_medical_reports_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctor_profiles (id),
    CONSTRAINT fk_medical_reports_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id)
);

CREATE INDEX idx_medical_reports_doctor ON medical_reports (doctor_id);
CREATE INDEX idx_medical_reports_patient ON medical_reports (patient_id);
CREATE INDEX idx_medical_reports_appointment ON medical_reports (appointment_id);

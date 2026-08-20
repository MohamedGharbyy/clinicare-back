-- V5__create_prescriptions_tables.sql
-- Tables for prescriptions and their prescribed medications.

CREATE TABLE prescriptions (
    id            BIGSERIAL PRIMARY KEY,
    doctor_id     BIGINT      NOT NULL,
    patient_id    BIGINT      NOT NULL,
    creation_date TIMESTAMP   NOT NULL,

    CONSTRAINT fk_prescriptions_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctor_profiles (id),
    CONSTRAINT fk_prescriptions_patient FOREIGN KEY (patient_id) REFERENCES patient_profiles (id)
);

CREATE TABLE prescription_medications (
    id              BIGSERIAL PRIMARY KEY,
    prescription_id BIGINT      NOT NULL,
    medication_name VARCHAR(255) NOT NULL,
    dosage          VARCHAR(100) NOT NULL,
    frequency       VARCHAR(100) NOT NULL,
    duration        VARCHAR(100) NOT NULL,
    instructions    VARCHAR(1000) NOT NULL,

    CONSTRAINT fk_prescription_medications_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE CASCADE
);

CREATE INDEX idx_prescriptions_doctor ON prescriptions (doctor_id);
CREATE INDEX idx_prescriptions_patient ON prescriptions (patient_id);
CREATE INDEX idx_prescription_medications_prescription ON prescription_medications (prescription_id);

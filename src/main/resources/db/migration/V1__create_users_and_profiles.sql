-- V1__create_users_and_profiles.sql
-- Initial schema for application users and their patient/doctor profiles.

CREATE TABLE users (
    id            BIGSERIAL   PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE patient_profiles (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    date_of_birth DATE,
    phone_number  VARCHAR(30),
    CONSTRAINT fk_patient_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_patient_profiles_user UNIQUE (user_id)
);

CREATE TABLE doctor_profiles (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    specialty      VARCHAR(100),
    license_number VARCHAR(100),
    phone_number   VARCHAR(30),
    CONSTRAINT fk_doctor_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_doctor_profiles_user UNIQUE (user_id)
);
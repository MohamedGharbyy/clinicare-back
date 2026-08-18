package com.clinicare.service;

import com.clinicare.dto.DoctorResponseDTO;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.repository.DoctorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only lookup of the doctors offered to patients in the booking form.
 * <p>
 * The returned {@link DoctorResponseDTO#id()} is the real {@code doctor_profiles.id}
 * so the frontend can submit it as {@code doctorId} when creating an
 * appointment. Names are assembled from each doctor's linked user account.
 */
@Service
public class DoctorService {

    private static final Comparator<DoctorProfile> BY_NAME = Comparator
            .comparing((DoctorProfile d) -> d.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(d -> d.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER);

    private final DoctorProfileRepository doctorProfileRepository;

    public DoctorService(DoctorProfileRepository doctorProfileRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
    }

    /** Returns all registered doctors, ordered by last name. */
    @Transactional(readOnly = true)
    public List<DoctorResponseDTO> listDoctors() {
        return doctorProfileRepository.findAll().stream()
                .sorted(BY_NAME)
                .map(d -> new DoctorResponseDTO(
                        d.getId(),
                        d.getUser().getFirstName() + " " + d.getUser().getLastName(),
                        d.getSpecialty()))
                .toList();
    }
}
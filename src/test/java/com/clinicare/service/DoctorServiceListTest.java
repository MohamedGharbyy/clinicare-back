package com.clinicare.service;

import com.clinicare.dto.DoctorResponseDTO;
import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.DoctorProfile;
import com.clinicare.entity.Role;
import com.clinicare.entity.User;
import com.clinicare.repository.AppointmentRepository;
import com.clinicare.repository.DoctorProfileRepository;
import com.clinicare.repository.UserRepository;
import com.clinicare.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceListTest {

    @Mock private DoctorProfileRepository doctorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private DoctorService doctorService;

    private User user(long id, Role role, AccountStatus status) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setStatus(status);
        u.setFirstName("Test");
        u.setLastName("Doctor");
        u.setEmail("doctor" + id + "@example.com");
        return u;
    }

    private DoctorProfile profile(long id, User user) {
        DoctorProfile p = new DoctorProfile();
        p.setId(id);
        p.setUser(user);
        p.setSpecialty("Cardiology");
        return p;
    }

    @Test
    void listDoctors_returnsOnlyActiveDoctors() {
        User active = user(1L, Role.DOCTOR, AccountStatus.ACTIVE);
        User deleted = user(2L, Role.DOCTOR, AccountStatus.DELETED);
        User disabled = user(3L, Role.DOCTOR, AccountStatus.DISABLED);
        User banned = user(4L, Role.DOCTOR, AccountStatus.BANNED);

        when(doctorProfileRepository.findAll()).thenReturn(List.of(
                profile(1L, active),
                profile(2L, deleted),
                profile(3L, disabled),
                profile(4L, banned)
        ));

        List<DoctorResponseDTO> result = doctorService.listDoctors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("Test Doctor");
    }

    @Test
    void listDoctors_excludesDeletedDoctor() {
        User active = user(1L, Role.DOCTOR, AccountStatus.ACTIVE);
        User deleted = user(2L, Role.DOCTOR, AccountStatus.DELETED);

        when(doctorProfileRepository.findAll()).thenReturn(List.of(
                profile(1L, active),
                profile(2L, deleted)
        ));

        List<DoctorResponseDTO> result = doctorService.listDoctors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void listDoctors_returnsEmptyWhenNoActiveDoctors() {
        User deleted = user(1L, Role.DOCTOR, AccountStatus.DELETED);

        when(doctorProfileRepository.findAll()).thenReturn(List.of(profile(1L, deleted)));

        List<DoctorResponseDTO> result = doctorService.listDoctors();

        assertThat(result).isEmpty();
    }
}

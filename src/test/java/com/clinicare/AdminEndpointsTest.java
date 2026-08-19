package com.clinicare;

import com.clinicare.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AdminEndpointsTest {

    @Autowired
    private AdminService adminService;

    @Test
    void testAdminEndpoints() {
        System.out.println("Testing getDashboardSummary()...");
        System.out.println("Dashboard: " + adminService.getDashboardSummary());

        System.out.println("Testing listPatients()...");
        System.out.println("Patients: " + adminService.listPatients());

        System.out.println("Testing listDoctors()...");
        System.out.println("Doctors: " + adminService.listDoctors());

        System.out.println("Testing listAppointments()...");
        System.out.println("Appointments: " + adminService.listAppointments());
    }
}

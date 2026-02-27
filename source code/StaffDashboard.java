package assignment;

import javax.swing.*;
import java.awt.*;

public class StaffDashboard extends BaseDashboard {

    public StaffDashboard(String currentStaffUsername) {
        super(
            currentStaffUsername,
            "Staff Dashboard", new Color(0x004D40), new Color(0x26A69A)  
        );
    }

    @Override
    protected void addCustomSidebarButtons() {
        // Add staff-specific sidebar buttons
        JButton managePatientsBtn = createSidebarButton("Manage Patients");
        JButton bookAppointmentBtn = createSidebarButton("Book Appointment");
        JButton manageAppointmentsBtn = createSidebarButton("Manage Appointments");
        JButton paymentsBtn = createSidebarButton("Payments");

        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(managePatientsBtn);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(bookAppointmentBtn);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(manageAppointmentsBtn);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(paymentsBtn);

        // Event Listeners (lazy loading panels when clicked)
        managePatientsBtn.addActionListener(e -> {
            JPanel patientsPanel = new UserManagementPanel(new String[]{"patient"});
            contentPanel.add(patientsPanel, "patients");
            cardLayout.show(contentPanel, "patients");
        });

        bookAppointmentBtn.addActionListener(e -> {
            JPanel bookingPanel = new BookingApp(currentUsername);
            contentPanel.add(bookingPanel, "appointments");
            cardLayout.show(contentPanel, "appointments");
        });

        manageAppointmentsBtn.addActionListener(e -> {
            JPanel reschedulePanel = new RescheduleAppointment(currentUsername);
            contentPanel.add(reschedulePanel, "manageAppointments");
            cardLayout.show(contentPanel, "manageAppointments");
        });

        paymentsBtn.addActionListener(e -> {
            JPanel paymentPanel = new PaymentPanel(currentUsername);
            contentPanel.add(paymentPanel, "payments");
            cardLayout.show(contentPanel, "payments");
        });
    }
}

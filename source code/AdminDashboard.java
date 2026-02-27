package assignment;

import javax.swing.*;
import java.awt.*;

// Admin dashboard that extends BaseDashboard and adds user management features
public class AdminDashboard extends BaseDashboard {

    // Constructor sets title and colors for the admin dashboard
    public AdminDashboard(String username) {
        super(username, "Admin Dashboard", new Color(30, 30, 60), new Color(63, 81, 181));
    }

    // Adds custom sidebar buttons specific to admin role
    @Override
    protected void addCustomSidebarButtons() {
        JButton manageUsersBtn = createSidebarButton("Manage Users"); 
        manageUsersBtn.addActionListener(e -> cardLayout.show(contentPanel, "users")); 
        sidebar.add(Box.createVerticalStrut(10)); 
        sidebar.add(manageUsersBtn); 
        setupUserManagementPanel(); 
    }

    // Creates and attaches the user management panel to the content area
    private void setupUserManagementPanel() {
        JPanel manageUsersPanel = new UserManagementPanel(new String[]{"manager", "staff", "doctor"});
        contentPanel.add(manageUsersPanel, "users");
    }
}


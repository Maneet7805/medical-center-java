package assignment;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.border.*;

// DoctorDashboard extends BaseDashboard and provides a GUI for doctor-specific operations
public class DoctorDashboard extends BaseDashboard {
    
     // File paths for storing doctor, appointment, and feedback data
    private static final String DOCTORS_FILE = "doctors.txt";
    private static final String APPOINTMENTS_FILE = "appointments.txt";
    private static final String FEEDBACK_FILE = "feedback.txt";

    private String[] doctorData;
    private JTextField[] doctorProfileFields;
    private JTable appointmentTable;
    private JTable closedTable;
    private DefaultTableModel appointmentTableModel;
    private DefaultTableModel closedAppointmentTableModel;
    private final Map<String, String[]> appointmentCache = new HashMap<>();
    
    private JLabel patientsValueLabel;
    private JLabel ratingValueLabel;
    private JPanel upcomingContainer;
    private JPanel availabilityGrid;
   
    private JPanel homePanel;
    private JLabel avatarLabel,profileNameLabel,profileTitleLabel,profileEmailLabel,profilePhoneLabel;
    private JLabel profileGenderLabel,profileDobLabel,profileAddrLabel,profileSpecLabel,profileShiftLabel;

    // Constructor: initialize DoctorDashboard for a specific doctor username
    public DoctorDashboard(String currentDoctorUsername) {
        super(currentDoctorUsername, "Doctor Dashboard", new Color(44, 62, 80), new Color(52, 73, 94));
            
        // Setup GUI components asynchronously on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            setupHomeAndProfilePanel();
            setupDoctorPanels();      
            loadDoctorInfo();   
            loadHomeData();     
            cardLayout.show(contentPanel, "home");
        });
    }
    
    // Adds sidebar buttons for managing upcoming appointments, viewing closed appointments, and feedback.
    @Override
    protected void addCustomSidebarButtons() {
        JButton appointmentsBtn = createSidebarButton("Manage Appointments");
        appointmentsBtn.addActionListener(e -> {
            loadDoctorInfo();
            loadAppointmentsForDoctor("Upcoming");
            cardLayout.show(contentPanel, "appointments");
        });

        JButton closedAppointmentsBtn = createSidebarButton("View Closed Appointments");
        closedAppointmentsBtn.addActionListener(e -> {
            loadDoctorInfo();
            loadAppointmentsForDoctor("Completed");
            cardLayout.show(contentPanel, "viewAppointments");
        });
        
        JButton feedbackBtn = createSidebarButton("My Feedback");
        feedbackBtn.addActionListener(e -> {
            loadDoctorInfo();
            setupFeedbackPanel(doctorData[0]); 
            cardLayout.show(contentPanel, "doctorFeedback");
    });

        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(appointmentsBtn);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(closedAppointmentsBtn);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(feedbackBtn);
    }
    
    // Initializes panels for upcoming and closed appointments, including tables, filters, and action buttons.
    private void setupDoctorPanels() {
        // ------------------ Upcoming appointments panel ------------------
        JPanel apptPanel = new JPanel(new BorderLayout(12, 12));
        apptPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel title = new JLabel("Manage Upcoming Appointments");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        apptPanel.add(title, BorderLayout.NORTH);

        // Filter panel for upcoming appointments
        JPanel apptFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JTextField upNameField = new JTextField(15);
        JTextField upDateField = new JTextField(10);
        JTextField upTimeField = new JTextField(5);
        JButton upApplyBtn = new JButton("Apply Filter");
        JButton upClearBtn = new JButton("Clear Filter");
        apptFilterPanel.add(new JLabel("Name:")); apptFilterPanel.add(upNameField);
        apptFilterPanel.add(new JLabel("Date:")); apptFilterPanel.add(upDateField);
        apptFilterPanel.add(new JLabel("Time:")); apptFilterPanel.add(upTimeField);
        apptFilterPanel.add(upApplyBtn); apptFilterPanel.add(upClearBtn);

        String[] cols = {"ApptID", "PatientID", "First Name", "Last Name", "Date", "Time", "Created By", "Action"};
        appointmentTableModel = new DefaultTableModel(null, cols) {
            @Override public boolean isCellEditable(int r, int c) { return c == 7; }
        };
        appointmentTable = new JTable(appointmentTableModel);
        appointmentTable.setRowHeight(36);
        appointmentTable.setFillsViewportHeight(true);
        hideColumn(appointmentTable, 0);
        hideColumn(appointmentTable, 1);
        appointmentTable.getColumn("Action").setCellRenderer(new ButtonRenderer("View"));
        appointmentTable.getColumn("Action").setCellEditor(
            new ButtonEditor(new JCheckBox(), "View",
                row -> handleViewAppointment(appointmentTableModel, "appointments", row))
        );

        // Combine filter + table
        JPanel apptCenter = new JPanel(new BorderLayout());
        apptCenter.add(apptFilterPanel, BorderLayout.NORTH);
        apptCenter.add(new JScrollPane(appointmentTable), BorderLayout.CENTER);
        apptPanel.add(apptCenter, BorderLayout.CENTER);
        contentPanel.add(apptPanel, "appointments");

        // Filter button actions for upcoming
        upApplyBtn.addActionListener(e ->
            applyAppointmentFilter(appointmentTableModel, appointmentCache,
                upNameField.getText().trim(),
                upDateField.getText().trim(),
                upTimeField.getText().trim())
        );
        upClearBtn.addActionListener(e -> {
            upNameField.setText(""); upDateField.setText(""); upTimeField.setText("");
            applyAppointmentFilter(appointmentTableModel, appointmentCache, "", "", "");
        });

        // Predictive writing for Upcoming
        JPopupMenu upNamePopup = new JPopupMenu();
        upNameField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) {
                Set<String> names = new HashSet<>();
                for (String[] appt : appointmentCache.values()) {
                    names.add(nz(appt,2) + " " + nz(appt,3));
                }
                showSuggestions(upNameField, upNamePopup, names);
            }
        });

        // ------------------ Closed appointments panel ------------------
        JPanel closedApptPanel = new JPanel(new BorderLayout(12, 12));
        closedApptPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel closedTitle = new JLabel("Closed Appointments");
        closedTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        closedApptPanel.add(closedTitle, BorderLayout.NORTH);

        // Filter panel for closed appointments
        JPanel closedFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JTextField clNameField = new JTextField(15);
        JTextField clDateField = new JTextField(10);
        JTextField clTimeField = new JTextField(5);
        JButton clApplyBtn = new JButton("Apply Filter");
        JButton clClearBtn = new JButton("Clear Filter");
        closedFilterPanel.add(new JLabel("Name:")); closedFilterPanel.add(clNameField);
        closedFilterPanel.add(new JLabel("Date:")); closedFilterPanel.add(clDateField);
        closedFilterPanel.add(new JLabel("Time:")); closedFilterPanel.add(clTimeField);
        closedFilterPanel.add(clApplyBtn); closedFilterPanel.add(clClearBtn);

        String[] closedCols = {"ApptID", "PatientID", "First Name", "Last Name", "Date", "Time", "Created By", "Action"};
        closedAppointmentTableModel = new DefaultTableModel(null, closedCols) {
            @Override public boolean isCellEditable(int r, int c) { return c == 7; }
        };
        closedTable = new JTable(closedAppointmentTableModel);
        closedTable.setRowHeight(36);
        closedTable.setFillsViewportHeight(true);
        hideColumn(closedTable, 0);
        hideColumn(closedTable, 1);
        closedTable.getColumn("Action").setCellRenderer(new ButtonRenderer("View"));
        closedTable.getColumn("Action").setCellEditor(
            new ButtonEditor(new JCheckBox(), "View",
                row -> handleViewAppointment(closedAppointmentTableModel, "viewAppointments", row))
        );

        // Combine filter + table
        JPanel closedCenter = new JPanel(new BorderLayout());
        closedCenter.add(closedFilterPanel, BorderLayout.NORTH);
        closedCenter.add(new JScrollPane(closedTable), BorderLayout.CENTER);
        closedApptPanel.add(closedCenter, BorderLayout.CENTER);
        contentPanel.add(closedApptPanel, "viewAppointments");

        // Filter button actions for closed
        clApplyBtn.addActionListener(e ->
            applyAppointmentFilter(closedAppointmentTableModel, appointmentCache,
                clNameField.getText().trim(),
                clDateField.getText().trim(),
                clTimeField.getText().trim())
        );
        clClearBtn.addActionListener(e -> {
            clNameField.setText(""); clDateField.setText(""); clTimeField.setText("");
            applyAppointmentFilter(closedAppointmentTableModel, appointmentCache, "", "", "");
        });

        // Predictive writing for Closed
        JPopupMenu clNamePopup = new JPopupMenu();
        clNameField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) {
                Set<String> names = new HashSet<>();
                for (String[] appt : appointmentCache.values()) {
                    names.add(nz(appt,2) + " " + nz(appt,3));
                }
                showSuggestions(clNameField, clNamePopup, names);
            }
        });
    }
        
    // Sets up the home and profile UI panels, including metrics cards, profile card, appointments, and availability.
    private void setupHomeAndProfilePanel() {
        // -------------------- Home Panel --------------------
        homePanel = new JPanel(new BorderLayout(12, 12));
        homePanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Header row: welcome + metric cards
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel welcomeLabel = new JLabel("Welcome, Doctor");
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        header.add(welcomeLabel, BorderLayout.WEST);

        // Metric cards (colored)
        JPanel metricsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        metricsRow.setOpaque(false);
        JPanel patientsCard = createMetricCard("Patients", "0", new Color(52, 152, 219));
        JPanel ratingCard = createMetricCard("Rating", "5.0", new Color(46, 204, 113));
        // save references to each value label
        patientsValueLabel = (JLabel) patientsCard.getClientProperty("valueLabel");
        ratingValueLabel = (JLabel) ratingCard.getClientProperty("valueLabel");
        metricsRow.add(patientsCard);
        metricsRow.add(ratingCard);
        header.add(metricsRow, BorderLayout.EAST);

        homePanel.add(header, BorderLayout.NORTH);

        // Center split: left profile card, right appointments + availability
        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8,8,8,8);
        gbc.fill = GridBagConstraints.BOTH;

        // Left profile card
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));

        JPanel profileCard = new JPanel(new BorderLayout());
        profileCard.setBackground(Color.WHITE);
        profileCard.setBorder(new CompoundBorder(new LineBorder(new Color(220,220,220),1,true), new EmptyBorder(12,12,12,12)));

        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        avatarRow.setOpaque(false);
        avatarLabel = new JLabel("DR");
        avatarLabel.setPreferredSize(new Dimension(64,64));
        avatarLabel.setOpaque(true);
        avatarLabel.setBackground(new Color(52,152,219));
        avatarLabel.setForeground(Color.WHITE);
        avatarLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        avatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
        avatarLabel.setBorder(new LineBorder(new Color(200,200,200),1,true));
        avatarRow.add(avatarLabel);

        JPanel namePane = new JPanel();
        namePane.setLayout(new BoxLayout(namePane, BoxLayout.Y_AXIS));
        namePane.setOpaque(false);
        profileNameLabel = new JLabel("Dr. —");
        profileNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        profileTitleLabel = new JLabel("—");
        profileTitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        profileTitleLabel.setForeground(Color.DARK_GRAY);
        namePane.add(profileNameLabel); 
        namePane.add(Box.createVerticalStrut(4)); 
        namePane.add(profileTitleLabel);
        avatarRow.add(namePane);
        profileCard.add(avatarRow, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0,1,4,4));
        details.setOpaque(false);
        profileEmailLabel = new JLabel("Email: —");
        profilePhoneLabel = new JLabel("Phone: —");
        profileGenderLabel = new JLabel("Gender: —");
        profileDobLabel = new JLabel("DOB: —");
        profileAddrLabel = new JLabel("Address: —");
        profileSpecLabel = new JLabel("Specialization: —");
        profileShiftLabel = new JLabel("Shift: —");
        details.add(profileEmailLabel); 
        details.add(profilePhoneLabel); 
        details.add(profileGenderLabel);
        details.add(profileDobLabel); 
        details.add(profileAddrLabel); 
        details.add(profileSpecLabel); 
        details.add(profileShiftLabel);
        profileCard.add(details, BorderLayout.CENTER);

        JButton editBtn = new JButton("Edit Profile");
        editBtn.setFocusPainted(false);
        editBtn.setBackground(new Color(52,152,219));
        editBtn.setForeground(Color.WHITE);
        editBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        editBtn.addActionListener(e -> openEditProfileDialog());
        JPanel editPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        editPane.setOpaque(false);
        editPane.add(editBtn);
        profileCard.add(editPane, BorderLayout.SOUTH);

        leftCol.add(profileCard);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.36; gbc.weighty = 1.0;
        center.add(leftCol, gbc);

        // Right: appointments + availability
        JPanel rightCol = new JPanel(new BorderLayout(10,10));
        rightCol.setBorder(new EmptyBorder(0,0,0,0));
        JLabel apptTitle = new JLabel("Appointments");
        apptTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        rightCol.add(apptTitle, BorderLayout.NORTH);

        upcomingContainer = new JPanel();
        upcomingContainer.setLayout(new BoxLayout(upcomingContainer, BoxLayout.Y_AXIS));
        upcomingContainer.setOpaque(false);
        JScrollPane upScroll = new JScrollPane(upcomingContainer, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        upScroll.setBorder(BorderFactory.createEmptyBorder());
        upScroll.getVerticalScrollBar().setUnitIncrement(12);
        rightCol.add(upScroll, BorderLayout.CENTER);

        availabilityGrid = new JPanel(new GridLayout(2,2,8,8));
        availabilityGrid.setOpaque(false);
        JPanel availWrap = new JPanel(new BorderLayout());
        availWrap.setBorder(new CompoundBorder(new TitledBorder("Availability (next 4 days)"), new EmptyBorder(8,8,8,8)));
        availWrap.add(availabilityGrid, BorderLayout.CENTER);
        rightCol.add(availWrap, BorderLayout.SOUTH);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.64; gbc.weighty = 1.0;
        center.add(rightCol, gbc);

        homePanel.add(center, BorderLayout.CENTER);
        contentPanel.add(homePanel, "home");

        // -------------------- Profile Panel --------------------
        JPanel profilePanel = new JPanel(new BorderLayout(16,16));
        profilePanel.setBorder(BorderFactory.createEmptyBorder(30,40,30,40));
        JLabel profileTitle = new JLabel("My Profile");
        profileTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        profilePanel.add(profileTitle, BorderLayout.NORTH);

        String[] labels = {"ID","Username","Name","Gender","DOB (Age)","Email","Contact","Address","Specialization","Shift"};
        JTextField[] fields = new JTextField[labels.length];
        for (int i=0;i<labels.length;i++) {
            fields[i] = new JTextField();
            fields[i].setEditable(false);
            fields[i].setFont(new Font("Segoe UI", Font.PLAIN, 15));
            fields[i].setBackground(Color.WHITE);
            fields[i].setDisabledTextColor(Color.BLACK);
            fields[i].setPreferredSize(new Dimension(350,28));
        }
        this.doctorProfileFields = fields;

        JPanel formPanel = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints(); 
        gbc.insets = new Insets(8,16,8,16); 
        gbc.fill = GridBagConstraints.HORIZONTAL;
        for (int i=0;i<labels.length;i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0.2;
            JLabel lbl = new JLabel(labels[i] + ":");
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
            formPanel.add(lbl, gbc);
            gbc.gridx = 1; gbc.weightx = 0.8;
            formPanel.add(fields[i], gbc);
        }
        profilePanel.add(formPanel, BorderLayout.CENTER);

        JButton editProfileBtn = new JButton("Edit My Profile");
        editProfileBtn.setFocusPainted(false);
        editProfileBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        editProfileBtn.setBackground(new Color(76,175,80));
        editProfileBtn.setForeground(Color.WHITE);
        editProfileBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        editProfileBtn.setPreferredSize(new Dimension(180,40));
        editProfileBtn.addActionListener(e -> openEditProfileDialog());

        JPanel profBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        profBottom.add(editProfileBtn);
        profilePanel.add(profBottom, BorderLayout.SOUTH);

        contentPanel.add(profilePanel, "profile");
    }
    
    // Creates a colored metric card panel displaying a title and a numeric value.
    private JPanel createMetricCard(String title, String value, Color bg) {
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(140,72));
        card.setBackground(bg);
        card.setOpaque(true);
        card.setBorder(new CompoundBorder(new LineBorder(new Color(200,200,200)), new EmptyBorder(8,8,8,8)));

        JLabel val = new JLabel(value, SwingConstants.CENTER);
        val.setFont(new Font("Segoe UI", Font.BOLD, 20));
        val.setForeground(Color.WHITE);
        JLabel ttl = new JLabel(title, SwingConstants.CENTER);
        ttl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ttl.setForeground(Color.WHITE);

        card.add(val, BorderLayout.CENTER);
        card.add(ttl, BorderLayout.SOUTH);

        card.putClientProperty("valueLabel", val);
        return card;
    }
    
    // Hides a column in a JTable by setting its width to zero.
    private void hideColumn(JTable table, int modelIndex) {
        // attempt to hide by view index mapped from model index
        TableColumnModel colModel = table.getColumnModel();
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            TableColumn col = colModel.getColumn(i);
            if (col.getModelIndex() == modelIndex) {
                col.setMinWidth(0);
                col.setMaxWidth(0);
                col.setPreferredWidth(0);
                col.setResizable(false);
                return;
            }
        }
    }
    
    // Loads the current doctor's profile data from file and updates the home and profile panels.
    private void loadDoctorInfo() {
        doctorData = findDoctorByUsername(currentUsername);
        if (doctorData != null && doctorProfileFields != null) {
            doctorProfileFields[0].setText(nz(doctorData,0));
            doctorProfileFields[1].setText(nz(doctorData,1));
            doctorProfileFields[2].setText(nz(doctorData,3) + " " + nz(doctorData,4));
            doctorProfileFields[3].setText(nz(doctorData,5));
            doctorProfileFields[4].setText(nz(doctorData,6) + " (Age: " + nz(doctorData,7) + ")");
            doctorProfileFields[5].setText(nz(doctorData,8));
            doctorProfileFields[6].setText(nz(doctorData,9));
            doctorProfileFields[7].setText(nz(doctorData,10) + ", " + nz(doctorData,11) + ", " + nz(doctorData,12));
            doctorProfileFields[8].setText(nz(doctorData,13));
            doctorProfileFields[9].setText(nz(doctorData,14));
        } else {
            if (doctorData == null) {
                JOptionPane.showMessageDialog(this, "Failed to load doctor profile.");
            }
        }
        
        if (homePanel != null && profileNameLabel != null) {
            String full = (nz(doctorData,3) + " " + nz(doctorData,4)).trim();
            profileNameLabel.setText("Dr. " + full);
            profileTitleLabel.setText(nz(doctorData,13));
            profileEmailLabel.setText("Email: " + nz(doctorData,8));
            profilePhoneLabel.setText("Phone: " + nz(doctorData,9));
            profileGenderLabel.setText("Gender: " + nz(doctorData,5));
            profileDobLabel.setText("DOB: " + nz(doctorData,6));
            profileAddrLabel.setText("Address: " + nz(doctorData,10) + ", " + nz(doctorData,11) + ", " + nz(doctorData,12));
            profileSpecLabel.setText("Specialization: " + nz(doctorData,13));
            profileShiftLabel.setText("Shift: " + nz(doctorData,14));
            String fn = nz(doctorData,3); String ln = nz(doctorData,4);
            String initials = "DR";
            if (!fn.isEmpty() || !ln.isEmpty()) initials = ((fn.isEmpty() ? "" : fn.substring(0,1)) + (ln.isEmpty() ? "" : ln.substring(0,1))).toUpperCase();
            avatarLabel.setText(initials);
        }
    }

    // Searches the doctors file for a doctor by username and returns the profile data array.
    private String[] findDoctorByUsername(String username) {
        Path p = Paths.get(DOCTORS_FILE);
        if (!Files.exists(p)) return null;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(Pattern.quote("|"), -1);
                if (parts.length >= 15 && parts[1].equalsIgnoreCase(username)) return parts;
            }
        } catch (IOException e) {}
        return null;
    }

     // Opens a modal dialog to edit the current doctor's profile and updates the file if saved.
    private void openEditProfileDialog() {
        if (doctorData == null) { JOptionPane.showMessageDialog(this, "Doctor profile not loaded."); return; }
        String[] fieldNames = {"ID","Username","Password","First Name","Last Name","Gender","DOB","Age","Email","Contact","Address","Postcode","State","Specialization","Shift"};
        UpdateUserDialog dlg = new UpdateUserDialog(this, "Edit Profile - " + nz(doctorData,3), fieldNames, doctorData, updated -> {
            if (updated != null) {
                UserFileHandler.updateUserById("doctor", updated[0], updated);
                doctorData = updated;
                loadDoctorInfo();
                JOptionPane.showMessageDialog(this, "Profile updated.");
            }
        });
        dlg.setModal(true); dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); dlg.setVisible(true);
    }

     // Loads appointments for the doctor filtered by status ("Upcoming" or "Completed") into the appropriate table.
    private void loadAppointmentsForDoctor(String statusFilter) {
        if (doctorData == null || doctorData.length < 1) {
            JOptionPane.showMessageDialog(this, "Doctor profile not loaded.");
            return;
        }

        DefaultTableModel model;
        if ("Upcoming".equalsIgnoreCase(statusFilter)) {
            model = appointmentTableModel;
        } else {
            model = closedAppointmentTableModel;
        }

        model.setRowCount(0);
        appointmentCache.clear();

        Path p = Paths.get(APPOINTMENTS_FILE);
        if (!Files.exists(p)) return;

        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] a = line.split(Pattern.quote("|"), -1);
                if (a.length >= 13 && safeEq(a, 6, nz(doctorData,0))) {
                    String status = a[12];

                    if ("Upcoming".equalsIgnoreCase(statusFilter)) {
                        if ("Upcoming".equalsIgnoreCase(status) || "Rescheduled".equalsIgnoreCase(status)) {
                            appointmentCache.put(a[0], a);
                            model.addRow(new Object[]{
                                a[0], a[1], nz(a,2), nz(a,3),
                                nz(a,4), nz(a,5), nz(a,11), "View"
                            });
                        }
                    }

                    else if ("Completed".equalsIgnoreCase(statusFilter)) {
                        if ("Completed".equalsIgnoreCase(status)) {
                            appointmentCache.put(a[0], a);
                            model.addRow(new Object[]{
                                a[0], a[1], nz(a,2), nz(a,3),
                                nz(a,4), nz(a,5), nz(a,11), "View"
                            });
                        }
                    }
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to load appointments: " + e.getMessage());
        }
    }

    // Opens the detailed view for a selected appointment from the given table row.
    private void handleViewAppointment(DefaultTableModel model, String returnPanel, int row) {
        String apptId = String.valueOf(model.getValueAt(row, 0));
        String[] a = appointmentCache.get(apptId);

        if (a == null) {
            Path p = Paths.get(APPOINTMENTS_FILE);
            if (!Files.exists(p)) return;
            try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] q = line.split(Pattern.quote("|"), -1);
                    if (q.length >= 1 && apptId.equals(q[0])) {
                        a = q;
                        break;
                    }
                }
            } catch (IOException ex) {}
        }

        if (a != null) {
            AppointmentDetailsPanel detailsPanel = new AppointmentDetailsPanel(
                a, nz(doctorData,0), nz(doctorData,1),
                () -> cardLayout.show(contentPanel, returnPanel),
                () -> cardLayout.show(contentPanel, returnPanel)
            );
            contentPanel.add(detailsPanel, "details");
            cardLayout.show(contentPanel, "details");
        }
    }
    
    // Filters appointment data based on name, date, and time, and updates the table display.
    private void applyAppointmentFilter(DefaultTableModel model, Map<String, String[]> cache, String nameFilter, String dateFilter, String timeFilter) {
        model.setRowCount(0); // clear table

        for (String[] a : cache.values()) {
            boolean matches = true;

            // Filter by name
            if (nameFilter != null && !nameFilter.isEmpty()) {
                String fullName = (nz(a,2) + " " + nz(a,3)).toLowerCase();
                if (!fullName.contains(nameFilter.toLowerCase())) matches = false;
            }

            // Filter by date
            if (dateFilter != null && !dateFilter.isEmpty()) {
                if (!nz(a,4).equals(dateFilter)) matches = false;
            }

            // Filter by time
            if (timeFilter != null && !timeFilter.isEmpty()) {
                if (!nz(a,5).equals(timeFilter)) matches = false;
            }

            if (matches) {
                model.addRow(new Object[]{a[0], a[1], nz(a,2), nz(a,3), nz(a,4), nz(a,5), nz(a,11), "View"});
            }
        }
    }
    
     // Loads dashboard metrics (patients, ratings), upcoming appointments, and next 4 days availability.
    private void loadHomeData() {
        if (doctorData == null) loadDoctorInfo();
        if (doctorData == null) return;

        // load appointments and fill cache
        appointmentCache.clear();
        List<String[]> allAppts = new ArrayList<>();
        Path ap = Paths.get(APPOINTMENTS_FILE);
        if (Files.exists(ap)) {
            try (BufferedReader br = Files.newBufferedReader(ap, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] a = line.split(Pattern.quote("|"), -1);
                    if (a.length >= 7 && safeEq(a,6,nz(doctorData,0))) {
                        allAppts.add(a);
                        appointmentCache.put(nz(a,0), a);
                    }
                }
            } catch (IOException ignored) {}
        }

        // metrics: unique patients
        Set<String> uniquePatients = new HashSet<>();
        for (String[] a : allAppts) uniquePatients.add(nz(a,1));
        if (patientsValueLabel != null)
            patientsValueLabel.setText(String.valueOf(uniquePatients.size()));

        // rating from feedback (now using doctorId + numeric rating)
        int sum = 0, count = 0;
        Path fp = Paths.get(FEEDBACK_FILE);
        if (Files.exists(fp)) {
            try (BufferedReader br = Files.newBufferedReader(fp, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] f = line.split(Pattern.quote("|"), -1);
                    // format: appointmentId|doctorId|patientId|rating|comments
                    if (f.length >= 4) {
                        if (f[1].trim().equalsIgnoreCase(nz(doctorData,0))) {
                            try {
                                int rating = Integer.parseInt(f[3].trim());
                                if (rating < 1) rating = 1;
                                if (rating > 5) rating = 5;
                                sum += rating;
                                count++;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        if (ratingValueLabel != null) {
            if (count > 0) {
                double avg = (double) sum / count;
                ratingValueLabel.setText(String.format("%.1f", avg));
            } else {
                ratingValueLabel.setText("N/A");
            }
        }

        // upcoming appointments (today and future)
        LocalDate today = LocalDate.now();
        List<String[]> display = new ArrayList<>();
        List<String> include = Arrays.asList("Upcoming", "Rescheduled", "Confirmed");
        for (String[] a : allAppts) {
            LocalDate d = parseDateFlex(nz(a,4));
            if (d == null) continue;
            if ((d.isAfter(today) || d.equals(today)) && include.contains(nz(a,12)))
                display.add(a);
        }
        display.sort(Comparator.comparing((String[] a) -> {
            LocalDate d = parseDateFlex(nz(a,4));
            return d == null ? LocalDate.MIN : d;
        }).thenComparing(a -> {
            try { return LocalTime.parse(nz(a,5)); }
            catch (Exception e) { return LocalTime.MIDNIGHT; }
        }));

        upcomingContainer.removeAll();
        if (display.isEmpty()) {
            JLabel none = new JLabel("No upcoming appointments");
            none.setHorizontalAlignment(SwingConstants.CENTER);
            none.setForeground(Color.GRAY); 
            upcomingContainer.add(Box.createVerticalStrut(8));
            upcomingContainer.add(none);
        } else {
            for (String[] a : display) {
                upcomingContainer.add(createReadOnlyAppointmentCard(a));
                upcomingContainer.add(Box.createVerticalStrut(8));
            }
        }
        upcomingContainer.revalidate();
        upcomingContainer.repaint();

        // availability: show shift text for next 4 days
        availabilityGrid.removeAll();
        ShiftBounds sb = parseShiftBounds(nz(doctorData,14));
        String shiftText = formatShiftText(sb);
        LocalDate d0 = LocalDate.now();
        for (int i=0;i<4;i++) {
            LocalDate d = d0.plusDays(i);
            JPanel box = new JPanel();
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setBackground(Color.WHITE);
            box.setBorder(new CompoundBorder(
                new LineBorder(new Color(200,200,200),1,true),
                new EmptyBorder(8,8,8,8)
            ));
            JLabel dateLbl = new JLabel(d.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
            dateLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            dateLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel shiftLbl = new JLabel(shiftText);
            shiftLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            shiftLbl.setForeground(Color.DARK_GRAY);
            shiftLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            box.add(dateLbl);
            box.add(Box.createVerticalStrut(6));
            box.add(shiftLbl);
            availabilityGrid.add(box);
        }
        availabilityGrid.revalidate();
        availabilityGrid.repaint();
    }

    // Creates a read-only card panel displaying appointment information (patient, date, time).
    private JPanel createReadOnlyAppointmentCard(String[] a) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(new LineBorder(new Color(210,210,210),1,true), new EmptyBorder(10,10,10,10)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        String patient = nz(a,2) + " " + nz(a,3);
        String date = nz(a,4);
        String time = nz(a,5);

        JPanel left = new JPanel(new BorderLayout()); left.setOpaque(false);
        JLabel timeLbl = new JLabel(time); timeLbl.setFont(new Font("Segoe UI", Font.BOLD, 18)); timeLbl.setHorizontalAlignment(SwingConstants.CENTER);
        left.add(timeLbl, BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(96,64));

        JPanel mid = new JPanel(new GridLayout(0,1)); mid.setOpaque(false);
        JLabel pLbl = new JLabel(patient); pLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JLabel dLbl = new JLabel(date + " • " + nz(a,8)); dLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12)); dLbl.setForeground(Color.DARK_GRAY);
        mid.add(pLbl); mid.add(dLbl);

        card.add(left, BorderLayout.WEST); card.add(mid, BorderLayout.CENTER);
        return card;
    }
    
     // Sets up the feedback panel UI to display ratings and comments for the given doctor.
    private void setupFeedbackPanel(String doctorId) {
        if (contentPanel.getComponentZOrder(contentPanel.getComponent(contentPanel.getComponentCount() - 1)) != -1
            && "doctorFeedback".equals(contentPanel.getComponent(contentPanel.getComponentCount() - 1).getName())) {
            return;
        }

        JPanel feedbackPanel = new JPanel(new BorderLayout(12, 12));
        feedbackPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        feedbackPanel.setName("doctorFeedback");
        
        JLabel title = new JLabel("My Feedback");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        feedbackPanel.add(title, BorderLayout.NORTH);

        // Table: Only Rating + Comments
        String[] cols = {"Rating", "Comments"};
        DefaultTableModel feedbackModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable feedbackTable = new JTable(feedbackModel);
        feedbackTable.setRowHeight(30);
        feedbackTable.setFillsViewportHeight(true);
        feedbackTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        feedbackTable.getColumnModel().getColumn(1).setPreferredWidth(400);

        JScrollPane scroll = new JScrollPane(feedbackTable);
        feedbackPanel.add(scroll, BorderLayout.CENTER);

        // Load feedback for this doctor
        loadDoctorFeedback(doctorId, feedbackModel);

        // Add to your content panel
        contentPanel.add(feedbackPanel, "doctorFeedback");
    }
    
    // Loads feedback from the file for the given doctor and populates the feedback table model.
    private void loadDoctorFeedback(String doctorId, DefaultTableModel model) {
        model.setRowCount(0);
        File feedbackFile = new File("feedback.txt");
        if (!feedbackFile.exists()) {
            model.addRow(new Object[]{"No feedback yet", ""});
            return;
        }

        boolean found = false;
        try (BufferedReader br = new BufferedReader(new FileReader(feedbackFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split("\\|", -1);
                // format: appointmentId|doctorId|patientId|rating|comments
                if (f.length >= 5 && f[1].equals(doctorId)) {
                    model.addRow(new Object[]{f[3], f[4]});
                    found = true;
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error loading feedback: " + e.getMessage());
        }

        if (!found) {
            model.addRow(new Object[]{"No feedback yet", ""});
        }
    }
    
    // Custom renderer for table buttons.
    static class ButtonRenderer extends JButton implements TableCellRenderer {
        ButtonRenderer(String label) {
            setText(label);
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
            setForeground(Color.WHITE);
            setBackground(new Color(44, 62, 80));
            setFocusPainted(false);
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            return this;
        }
    }
    
    // Custom editor for table buttons with click action handling.
    static class ButtonEditor extends DefaultCellEditor {
        protected final JButton button;
        private final String label;
        private boolean clicked;
        private int row;
        private final RowActionListener listener;

        interface RowActionListener { void onAction(int rowIndex); }

        ButtonEditor(JCheckBox chk, String label, RowActionListener l) {
            super(chk);
            this.label = label;
            this.listener = l;
            button = new JButton(label);
            button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            button.setForeground(Color.WHITE);
            button.setBackground(new Color(63,81,181));
            button.setFocusPainted(false);
            button.addActionListener(e -> {
                clicked = true;
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object v, boolean s, int r, int c) {
            this.row = r;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (clicked) listener.onAction(row);
            clicked = false;
            return label;
        }
    }
    
    // Converts shift code ("A", "B", "C") to start and end LocalTime bounds.
    private static class ShiftBounds { final LocalTime start, end; ShiftBounds(LocalTime s, LocalTime e) { start = s; end = e; } }
    private ShiftBounds parseShiftBounds(String raw) {
        if (raw == null) return new ShiftBounds(LocalTime.of(8,0), LocalTime.of(15,30));
        String s = raw.trim().toUpperCase();
        if (s.contains("A")) return new ShiftBounds(LocalTime.of(8,0), LocalTime.of(15,30));
        if (s.contains("B")) return new ShiftBounds(LocalTime.of(16,0), LocalTime.of(23,30));
        if (s.contains("C")) return new ShiftBounds(LocalTime.of(0,0), LocalTime.of(7,30));
        return new ShiftBounds(LocalTime.of(8,0), LocalTime.of(15,30));
    }
    
    // Formats a ShiftBounds object into a readable string showing start and end times.
    private String formatShiftText(ShiftBounds sb) {
        if (sb == null) return "";
        return friendlyTime(sb.start) + " till " + friendlyTime(sb.end);
    }
    
    // Converts LocalTime to a friendly 12-hour format string with am/pm.
    private String friendlyTime(LocalTime t) {
        int hour = t.getHour();
        int minute = t.getMinute();
        String mer = hour >= 12 ? "pm" : "am";
        int display = hour % 12 == 0 ? 12 : hour % 12;
        if (minute == 0) return display + " " + mer;
        return display + ":" + String.format("%02d", minute) + " " + mer;
    }

    // ---------------- Small helpers ---------------- //
    // Attempts to parse a date string in multiple formats and returns LocalDate.
    private LocalDate parseDateFlex(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return LocalDate.parse(raw); } catch (DateTimeParseException ignore) {}
        try { DateTimeFormatter f = DateTimeFormatter.ofPattern("d/M/yyyy"); return LocalDate.parse(raw, f); } catch (Exception ignore) {}
        return null;
    }
    
    // Returns array element at index or empty string if null/out of bounds.
    private static String nz(String[] arr, int idx) { 
        return (arr != null && idx < arr.length && arr[idx] != null) ? arr[idx] : ""; 
    }
    
    // Checks if array element at index equals given string, ignoring case.
    private static boolean safeEq(String[] arr, int idx, String cmp) { 
        return idx < arr.length && arr[idx] != null && arr[idx].equalsIgnoreCase(cmp); 
    }
}

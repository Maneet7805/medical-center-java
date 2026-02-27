package assignment;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.io.*;

public class ManagerDashboard extends BaseDashboard {

    private JTable allAppointmentsTable;
    private DefaultTableModel allAppointmentsModel;
    private final Map<String, String[]> appointmentCache = new HashMap<>();

    private JTable feedbackTable;
    private DefaultTableModel feedbackModel;

    // Constructor for ManagerDashboard; initializes the dashboard with sidebar and panels.
    public ManagerDashboard(String username) {
        super(username, "Manager Dashboard", new Color(0, 100, 0), new Color(76, 175, 80));
    }

     // Adds manager-specific buttons to the sidebar and sets up corresponding panels.
    @Override
    protected void addCustomSidebarButtons() {
        // --- Manage Users ---
        JButton manageUsersBtn = createSidebarButton("Manage Users");
        manageUsersBtn.addActionListener(e -> cardLayout.show(contentPanel, "users"));
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(manageUsersBtn);

        // --- All Appointments ---
        JButton allAppointmentsBtn = createSidebarButton("View All Appointments");
        allAppointmentsBtn.addActionListener(e -> {
            loadAllAppointments();
            cardLayout.show(contentPanel, "allAppointments");
        });
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(allAppointmentsBtn);

        // --- Feedback ---
        JButton feedbackBtn = createSidebarButton("View Feedback");
        feedbackBtn.addActionListener(e -> {
            loadAllFeedback();
            cardLayout.show(contentPanel, "feedback");
        });
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(feedbackBtn);

        // --- Reports ---
        JButton reportsBtn = createSidebarButton("View Reports");
        reportsBtn.addActionListener(e -> {
            cardLayout.show(contentPanel, "reports");
        });
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(reportsBtn);
        
        // --- Recipets ---
        JButton receiptsBtn = createSidebarButton("View Receipts");
        receiptsBtn.addActionListener(e -> {
            cardLayout.show(contentPanel, "receipts");
        });
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(receiptsBtn);

        // Setup panels
        setupUserManagementPanel();
        setupAllAppointmentsPanel();
        setupFeedbackPanel();
        setupReportsPanel();
        setupReceiptsPanel();
    }

    // ------------------- User Management -------------------
    // Sets up the "Manage Users" panel for staff and doctors.
    private void setupUserManagementPanel() {
        JPanel manageUsersPanel = new UserManagementPanel(new String[]{"staff", "doctor"});
        contentPanel.add(manageUsersPanel, "users");
    }

    // ------------------- Reports Panel -------------------
    // Sets up the "Reports" panel using ReportGenerator.
    private void setupReportsPanel() {
        ReportGenerator reportPanel = new ReportGenerator();
        contentPanel.add(reportPanel, "reports");
    }
    
    // ------------------- Receipts Panel -------------------
    // Sets up the "Receipts" panel using ReceiptPanel.
    private void setupReceiptsPanel() {
        ReceiptPanel receiptPanel = new ReceiptPanel();
        contentPanel.add(receiptPanel, "receipts");
    }
    
    // ------------------- Appointments Panel -------------------
    // Creates the "All Appointments" panel with table, filters, and predictive name suggestions.
    private void setupAllAppointmentsPanel() {
        JPanel apptPanel = new JPanel(new BorderLayout(12, 12));
        apptPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("All Appointments");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        apptPanel.add(title, BorderLayout.NORTH);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JTextField nameField = new JTextField(15);
        JTextField dateField = new JTextField(10);
        JTextField timeField = new JTextField(5);
        JButton applyBtn = new JButton("Apply Filter");
        JButton clearBtn = new JButton("Clear Filter");

        filterPanel.add(new JLabel("Name:"));
        filterPanel.add(nameField);
        filterPanel.add(new JLabel("Date:"));
        filterPanel.add(dateField);
        filterPanel.add(new JLabel("Time:"));
        filterPanel.add(timeField);
        filterPanel.add(applyBtn);
        filterPanel.add(clearBtn);

        String[] cols = {"ApptID", "PatientID", "First Name", "Last Name", "Date", "Time",
                "DoctorID", "Doctor Name", "Specialization", "Shift", "Created On", "Created By", "Status"};

        allAppointmentsModel = new DefaultTableModel(null, cols) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        allAppointmentsTable = new JTable(allAppointmentsModel);
        allAppointmentsTable.setRowHeight(28);
        allAppointmentsTable.setFillsViewportHeight(true);

        allAppointmentsTable.getColumn("Status").setCellRenderer(new StatusRenderer());

        JPanel center = new JPanel(new BorderLayout());
        center.add(filterPanel, BorderLayout.NORTH);
        center.add(new JScrollPane(allAppointmentsTable), BorderLayout.CENTER);
        apptPanel.add(center, BorderLayout.CENTER);

        contentPanel.add(apptPanel, "allAppointments");

        applyBtn.addActionListener(e -> applyAppointmentFilter(
                allAppointmentsModel, appointmentCache,
                nameField.getText().trim(), dateField.getText().trim(), timeField.getText().trim()
        ));

        clearBtn.addActionListener(e -> {
            nameField.setText(""); dateField.setText(""); timeField.setText("");
            applyAppointmentFilter(allAppointmentsModel, appointmentCache, "", "", "");
        });

        // Predictive writing
        JPopupMenu namePopup = new JPopupMenu();
        nameField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                Set<String> names = new HashSet<>();
                for (String[] appt : appointmentCache.values()) {
                    names.add(nz(appt, 2) + " " + nz(appt, 3)); // patient
                    names.add(nz(appt, 7)); // doctor
                }
                showSuggestions(nameField, namePopup, names);
            }
        });
    }

    // Loads all appointments from "appointments.txt" into the table and cache.
    private void loadAllAppointments() {
        allAppointmentsModel.setRowCount(0);
        appointmentCache.clear();

        Path p = Paths.get("appointments.txt");
        if (!Files.exists(p)) return;

        try (var br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] a = line.split(Pattern.quote("|"), -1);
                if (a.length >= 13) {
                    appointmentCache.put(a[0], a);
                    allAppointmentsModel.addRow(new Object[]{
                            a[0], a[1], nz(a,2), nz(a,3), nz(a,4), nz(a,5),
                            nz(a,6), nz(a,7), nz(a,8), nz(a,9), nz(a,10), nz(a,11), nz(a,12)
                    });
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to load appointments: " + e.getMessage());
        }
    }

    // Filters the appointments table based on patient/doctor name, date, and time.
    private void applyAppointmentFilter(DefaultTableModel model, Map<String, String[]> cache,
                                        String nameFilter, String dateFilter, String timeFilter) {
        model.setRowCount(0);
        for (String[] a : cache.values()) {
            boolean matches = true;

            if (!nameFilter.isEmpty()) {
                String patientName = (nz(a,2) + " " + nz(a,3)).toLowerCase();
                String doctorName = nz(a,7).toLowerCase();
                if (!patientName.contains(nameFilter.toLowerCase()) && !doctorName.contains(nameFilter.toLowerCase()))
                    matches = false;
            }
            if (!dateFilter.isEmpty() && !nz(a,4).equals(dateFilter)) matches = false;
            if (!timeFilter.isEmpty() && !nz(a,5).equals(timeFilter)) matches = false;

            if (matches) {
                model.addRow(new Object[]{
                        a[0], a[1], nz(a,2), nz(a,3), nz(a,4), nz(a,5),
                        nz(a,6), nz(a,7), nz(a,8), nz(a,9), nz(a,10), nz(a,11), nz(a,12)
                });
            }
        }
    }

    // ------------------- Feedback Panel -------------------
    // Creates the "Feedback" panel with table, filter options, and sorting.
    private void setupFeedbackPanel() {
        JPanel feedbackPanel = new JPanel(new BorderLayout(12, 12));
        feedbackPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Patient Feedback");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        feedbackPanel.add(title, BorderLayout.NORTH);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JTextField idField = new JTextField(15); // doctorId or patientId
        JComboBox<String> ratingFilter = new JComboBox<>(new String[]{"All Ratings", "1", "2", "3", "4", "5"});
        JButton applyBtn = new JButton("Apply Filter");
        JButton clearBtn = new JButton("Clear Filter");

        filterPanel.add(new JLabel("ID:"));
        filterPanel.add(idField);
        filterPanel.add(new JLabel("Rating:"));
        filterPanel.add(ratingFilter);
        filterPanel.add(applyBtn);
        filterPanel.add(clearBtn);

        String[] cols = {"Appointment ID", "Doctor ID", "Patient ID", "Rating", "Comments"};
        feedbackModel = new DefaultTableModel(null, cols) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        feedbackTable = new JTable(feedbackModel);
        feedbackTable.setRowHeight(28);
        feedbackTable.setFillsViewportHeight(true);

        JPanel center = new JPanel(new BorderLayout());
        center.add(filterPanel, BorderLayout.NORTH);
        center.add(new JScrollPane(feedbackTable), BorderLayout.CENTER);
        feedbackPanel.add(center, BorderLayout.CENTER);

        contentPanel.add(feedbackPanel, "feedback");

        loadAllFeedback();

        applyBtn.addActionListener(e -> {
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(feedbackModel);
            feedbackTable.setRowSorter(sorter);

            List<RowFilter<DefaultTableModel, Integer>> filters = new ArrayList<>();

            String idText = idField.getText().trim();
            if (!idText.isEmpty()) {
                filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(idText), 0, 1, 2));
            }

            String selectedRating = ratingFilter.getSelectedItem().toString();
            if (!selectedRating.equals("All Ratings")) {
                filters.add(RowFilter.regexFilter("^" + selectedRating + "$", 3));
            }

            RowFilter<DefaultTableModel, Integer> compound = RowFilter.andFilter(filters);
            sorter.setRowFilter(compound);
        });

        clearBtn.addActionListener(e -> {
            idField.setText("");
            ratingFilter.setSelectedIndex(0);
            feedbackTable.setRowSorter(null);
        });
        
        clearBtn.addActionListener(e -> {
            idField.setText("");
            ratingFilter.setSelectedIndex(0);
            feedbackTable.setRowSorter(null);
        });

    }

    // Loads all feedback from "feedback.txt" into the feedback table.
    private void loadAllFeedback() {
        feedbackModel.setRowCount(0);
        File feedbackFile = new File("feedback.txt");
        if (!feedbackFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(feedbackFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(Pattern.quote("|"), -1);
                if (f.length >= 5) {
                    feedbackModel.addRow(new Object[]{
                            nz(f,0), // appointmentId
                            nz(f,1), // doctorId
                            nz(f,2), // patientId
                            nz(f,3), // rating
                            nz(f,4)  // comments
                    });
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error reading feedback: " + e.getMessage());
        }
    }

    // ------------------- Status Renderer -------------------
    // Custom cell renderer for appointment status; colors cells based on status value.
    static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (value != null) {
                String status = value.toString();
                switch (status.toLowerCase()) {
                    case "upcoming" -> c.setBackground(Color.RED);
                    case "rescheduled" -> c.setBackground(Color.YELLOW);
                    case "completed" -> c.setBackground(Color.GREEN);
                    default -> c.setBackground(Color.WHITE);
                }
                if (isSelected) c.setBackground(c.getBackground().darker());
            }
            return c;
        }
    }

    // ------------------- Helpers -------------------
    // Safe retrieval from array: returns empty string if index is invalid or null.
    private static String nz(String[] arr, int idx) {
        return (arr != null && idx < arr.length && arr[idx] != null) ? arr[idx] : "";
    }

    class ReceiptPanel extends JPanel {
        private final JTable table;
        private final DefaultTableModel model;
        private static final String PAYMENTS_FILE = "payments.txt";

        // Constructor: sets up receipts table, buttons, and loads receipt data.
        public ReceiptPanel() {
            setLayout(new BorderLayout());

            // Table setup
            model = new DefaultTableModel(new String[]{
                    "InvoiceNo", "AppointmentID", "Patient", "Doctor", "Amount", "Method", "Date", "Staff"
            }, 0);

            table = new JTable(model);
            add(new JScrollPane(table), BorderLayout.CENTER);

            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton viewBtn = new JButton("View Receipt");
            buttonPanel.add(viewBtn);
            add(buttonPanel, BorderLayout.SOUTH);

            // Load receipts
            loadReceipts();

            // View button action
            viewBtn.addActionListener(e -> viewReceipt());
        }

        // Loads payment/receipt records from "payments.txt" into the table.
        private void loadReceipts() {
            model.setRowCount(0);
            try {
                List<String> lines = Files.readAllLines(Paths.get(PAYMENTS_FILE));
                for (String line : lines) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 7) {
                        String invoiceNo = parts[0];
                        String apptId = parts[1];
                        String patientId = parts[2];
                        String amount = parts[3];
                        String method = parts[4];
                        String date = parts[5];
                        String staff = parts[6];

                        String patientName = getPatientName(patientId);
                        String doctorName = getDoctorNameByAppt(apptId);

                        model.addRow(new Object[]{invoiceNo, apptId, patientName, doctorName, "RM " + amount, method, date, staff});
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error loading receipts.");
            }
        }

        // Retrieves patient name by ID from "patients.txt"; returns ID if not found.
        private String getPatientName(String patientId) {
            try {
                for (String line : Files.readAllLines(Paths.get("patients.txt"))) {
                    String[] p = line.split("\\|");
                    if (p[0].equals(patientId)) return p[3] + " " + p[4];
                }
            } catch (IOException e) {}
            return patientId;
        }

        // Retrieves doctor name by appointment ID from "appointments_records.txt".
        private String getDoctorNameByAppt(String apptId) {
            try {
                for (String line : Files.readAllLines(Paths.get("appointments_records.txt"))) {
                    String[] parts = line.split("\\|");
                    if (parts[0].equals(apptId)) {
                        String doctorId = parts[2];
                        return getDoctorName(doctorId);
                    }
                }
            } catch (IOException e) {}
            return "Unknown Doctor";
        }

        // Retrieves doctor name by ID from "doctors.txt"; returns ID if not found.
        private String getDoctorName(String doctorId) {
            try {
                for (String line : Files.readAllLines(Paths.get("doctors.txt"))) {
                    String[] d = line.split("\\|");
                    if (d[0].equals(doctorId)) return d[3] + " " + d[4];
                }
            } catch (IOException e) {}
            return doctorId;
        }

        // Displays a selected receipt in a formatted popup dialog.
        private void viewReceipt() {
            int row = table.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select a receipt to view.");
                return;
            }

            String invoiceNo = model.getValueAt(row, 0).toString();
            String patient = model.getValueAt(row, 2).toString();
            String doctor = model.getValueAt(row, 3).toString();
            String amount = model.getValueAt(row, 4).toString();
            String method = model.getValueAt(row, 5).toString();
            String date = model.getValueAt(row, 6).toString();
            String staff = model.getValueAt(row, 7).toString();

            // Show receipt in popup
            StringBuilder sb = new StringBuilder();
            sb.append("========== Dental Clinic ==========\n");
            sb.append("Invoice No: ").append(invoiceNo).append("\n");
            sb.append("Date: ").append(date).append("\n\n");
            sb.append("Patient: ").append(patient).append("\n");
            sb.append("Doctor: ").append(doctor).append("\n");
            sb.append("Amount: ").append(amount).append("\n");
            sb.append("Payment Method: ").append(method).append("\n");
            sb.append("Staff: ").append(staff).append("\n");
            sb.append("===================================\n");

            JTextArea area = new JTextArea(sb.toString());
            area.setFont(new Font("Monospaced", Font.PLAIN, 14));
            area.setEditable(false);

            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Receipt", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

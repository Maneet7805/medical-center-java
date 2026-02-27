package assignment;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

// Patient dashboard GUI for displaying patient info, appointments, payments, and feedback
public class PatientDashboard extends BaseDashboard {
    
    // --------------------- Constants & Fields ---------------------
    private static final String PATIENTS_FILE = "patients.txt";
    private static final String APPOINTMENTS_FILE = "appointments.txt";
    private static final String APPT_RECORDS_FILE = "appointments_records.txt";
    private static final String PAYMENTS_FILE = "payments.txt";

    private String[] patientData;
    private JTextField[] profileFields;

    private DefaultTableModel apptTableModel;
    private JTable apptTable;

    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 16);
    private static final Font FIELD_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Color LIGHT_BLUE = new Color(230, 245, 255);

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_TIME_HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FILE_TIME_HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Modular feedback class name 
    private static final String FEEDBACK_CLASS_NAME = "assignment.PatientFeedback";
    
    // --------------------- Constructor ---------------------
    public PatientDashboard(String currentPatientUsername) {
        super(currentPatientUsername, "Patient Dashboard", new Color(0, 137, 123), new Color(38, 166, 154));

        try {
            loadPatientInfo();
            buildAppointmentsPanel();
            buildViewAppointmentsPanel();
            buildHome();
            cardLayout.show(contentPanel, "home");
        } catch (Throwable t) {
            System.err.println("PatientDashboard init warning: " + t.getMessage());
        }
    }

    // --------------------- Sidebar Buttons ---------------------
    @Override
    protected void addCustomSidebarButtons() {
        JButton historyBtn = createSidebarButton("Appointment History");
        historyBtn.addActionListener(e -> {
            loadPatientInfo();
            buildViewAppointmentsPanel();
            cardLayout.show(contentPanel, "viewAppointments");
        });

        JButton feedbackBtn = createSidebarButton("Feedback");
        feedbackBtn.addActionListener(e -> openFeedbackModule());

        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(historyBtn);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(feedbackBtn);

        sidebar.revalidate();
        sidebar.repaint();
    }
    
    // Opens a dialog to allow the patient to edit their profile
    private void openEditProfileDialog() {
        if (patientData == null) {
            JOptionPane.showMessageDialog(this, "Patient profile not loaded.");
            return;
        }
        String[] fieldNames = {"ID", "Username", "Password", "First Name", "Last Name", "Gender", "DOB", "Age", "Email", "Contact", "Address", "Postcode", "State"};
        UpdateUserDialog dlg = new UpdateUserDialog(this, "Edit Profile - " + getField(patientData, 3), fieldNames, patientData, updated -> {
            if (updated != null) {
                UserFileHandler.updateUserById("patient", updated[0], updated);
                patientData = updated;
                loadPatientInfo();
                buildHome();
                JOptionPane.showMessageDialog(this, "Profile updated successfully.");
            }
        });
        dlg.setModal(true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setVisible(true);
    }

    // Builds the appointments overview panel with a table
    private void buildAppointmentsPanel() {
        for (Component c : contentPanel.getComponents()) if ("appointments".equals(c.getName())) { contentPanel.remove(c); break; }

        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setName("appointments");
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        p.setBackground(Color.WHITE);

        String[] cols = {"Appointment ID", "Doctor ID", "Doctor Name", "Date", "Time", "Created By"};
        apptTableModel = new DefaultTableModel(cols, 0);
        apptTable = new JTable(apptTableModel);
        apptTable.setFont(FIELD_FONT);
        apptTable.setRowHeight(30);
        p.add(new JScrollPane(apptTable), BorderLayout.CENTER);

        contentPanel.add(p, "appointments");
    }

    // Builds the detailed view appointments panel showing history
    private void buildViewAppointmentsPanel() {
        for (Component c : contentPanel.getComponents()) {
            if ("viewAppointments".equals(c.getName())) {
                contentPanel.remove(c);
                break;
            }
        }

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setName("viewAppointments");

        JLabel title = new JLabel("Appointment History");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        panel.add(title, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Doctor", "Date", "Time", "Treatment", "Medicine", "Frequency", "Meal", "Doctor Feedback"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // Make all columns read-only
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);

        loadPatientCompletedAppointments(model);

        table.getColumnModel().getColumn(5).setPreferredWidth(120); // Medicine
        table.getColumnModel().getColumn(6).setPreferredWidth(100); // Frequency
        table.getColumnModel().getColumn(7).setPreferredWidth(100); // Meal
        table.getColumnModel().getColumn(8).setPreferredWidth(200); // Treatments

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        contentPanel.add(panel, "viewAppointments");
    }

    // Loads completed appointments for the patient into a table model
    private void loadPatientCompletedAppointments(DefaultTableModel model) {
        String patientId = getField(patientData, 0); 
        model.setRowCount(0); // clear old data

        Path path = Paths.get("appointments_records.txt");
        if (!Files.exists(path)) return;

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] fields = line.split("\\|", -1);
                if (fields.length >= 11 && fields[1].equals(patientId)) {

                    // Split medicine info (index 9)
                    String[] medParts = fields[9].split("~", -1);
                    String medName = medParts.length > 0 ? medParts[0] : "-";
                    String medFreq = medParts.length > 1 ? medParts[1] : "-";
                    String medMeal = medParts.length > 2 ? medParts[2] : "-";

                    // Add row to table
                    model.addRow(new Object[]{
                            fields[0],  // Appointment ID
                            fields[3],  // Doctor Name
                            fields[4],  // Date
                            fields[5],  // Time
                            fields[7],  // Status
                            medName,    // Medicine
                            medFreq,    // Frequency
                            medMeal,    // Meal
                            fields[8]   // Treatments
                    });
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load appointments: " + ex.getMessage());
        }
    }
        
    // Builds the main home panel/dashboard for the patient
    private void buildHome() {
        loadPatientInfo();

        // Remove old home panel if exists
        for (Component c : contentPanel.getComponents()) {
            if ("home".equals(c.getName())) {
                contentPanel.remove(c);
                break;
            }
        }

        JPanel home = new JPanel(new BorderLayout(12,12));
        home.setName("home");
        home.setBorder(new EmptyBorder(12,12,12,12));
        home.setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        JLabel welcome = new JLabel("Welcome, " + (patientData != null ? (getField(patientData,3) + " " + getField(patientData,4)) : "Patient"));
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 22));
        header.add(welcome, BorderLayout.WEST);

        JButton editProfileBtn = new JButton("Edit Profile"); stylePrimary(editProfileBtn);
        editProfileBtn.addActionListener(e -> openEditProfileDialog());
        JPanel hr = new JPanel(new FlowLayout(FlowLayout.RIGHT)); hr.setOpaque(false); hr.add(editProfileBtn);
        header.add(hr, BorderLayout.EAST);
        home.add(header, BorderLayout.NORTH);

        // Top row stats
        JPanel topRow = new JPanel(new GridLayout(1,3,12,12)); topRow.setOpaque(false); topRow.setPreferredSize(new Dimension(1000, 120));
        topRow.add(createStatCardWithButton("Payments Made", String.format("RM %.2f", getPaymentsMade()), new Color(46,125,50), "View details", e -> showPaymentsMadeDialog()));
        topRow.add(createStatCardWithButton("Payment Due", String.format("RM %.2f", getPaymentsDueFiltered()), new Color(211,47,47), "View details", e -> showPaymentsDueDialogFiltered()));
        topRow.add(createStatCard("Total Appointments", String.valueOf(countAppointments()), new Color(33,150,243)));

        // Upcoming appointments
        JPanel upcomingPanel = new JPanel(new BorderLayout(8,8)); upcomingPanel.setOpaque(false);
        JLabel upTitle = new JLabel("Upcoming Appointments"); upTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        upcomingPanel.add(upTitle, BorderLayout.NORTH);

        JPanel upcomingRow = new JPanel(new GridLayout(1,2,12,12)); upcomingRow.setOpaque(false);
        List<String[]> upcoming = fetchUpcomingAppointments(2);
        if (upcoming.isEmpty()) { upcomingRow.add(createBlankCard()); upcomingRow.add(createBlankCard()); }
        else if (upcoming.size() == 1) { upcomingRow.add(createAppointmentCard(upcoming.get(0))); upcomingRow.add(createBlankCard()); }
        else { upcomingRow.add(createAppointmentCard(upcoming.get(0))); upcomingRow.add(createAppointmentCard(upcoming.get(1))); }
        upcomingPanel.add(upcomingRow, BorderLayout.CENTER);

        // Recent appointments
        JPanel recent = createRecentAppointmentsBox(8);

        // Center panel
        JPanel centerAll = new JPanel(); centerAll.setLayout(new BoxLayout(centerAll, BoxLayout.Y_AXIS)); centerAll.setOpaque(false);
        centerAll.add(topRow); centerAll.add(Box.createVerticalStrut(12)); centerAll.add(upcomingPanel); centerAll.add(Box.createVerticalStrut(12)); centerAll.add(recent);

        home.add(centerAll, BorderLayout.CENTER);

        // Add to content panel once
        contentPanel.add(home, "home");
        cardLayout.show(contentPanel, "home");
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    // Creates a panel listing recent appointments
    private JPanel createRecentAppointmentsBox(int limit) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(LIGHT_BLUE);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200,220,235)), new EmptyBorder(12,12,12,12)));
        JLabel title = new JLabel("Recent Appointments"); title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        box.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel(); list.setOpaque(false); list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS)); list.setBorder(new EmptyBorder(6,6,6,6));

        List<String[]> rec = fetchRecentAppointments(limit);
        if (rec.isEmpty()) {
            JLabel none = new JLabel("No recent appointments.");
            none.setFont(FIELD_FONT); none.setForeground(new Color(80,80,80));
            JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); p.add(none); list.add(p);
        } else {
            for (String[] a : rec) { JPanel r = buildRecentRow(a); list.add(r); list.add(Box.createVerticalStrut(8)); }
        }
        box.add(list, BorderLayout.CENTER);
        return box;
    }

    // Builds a row panel representing a single recent appointment
    private JPanel buildRecentRow(String[] a) {
        String dateRaw = getField(a,4);
        String timeRaw = getField(a,5);
        String doc = getField(a,7);
        String status = deriveStatus(a);
        String dateLabel;
        try { dateLabel = LocalDate.parse(dateRaw, FILE_DATE).format(DateTimeFormatter.ofPattern("dd MMM yyyy")); } catch (Exception e) { dateLabel = dateRaw; }

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(true);
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200,200,200)), new EmptyBorder(10,10,10,10)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,86)); row.setPreferredSize(new Dimension(Integer.MAX_VALUE,86));

        JPanel left = new JPanel(); left.setOpaque(false); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel d = new JLabel(dateLabel); d.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JLabel t = new JLabel(timeRaw.isEmpty() ? "-" : timeRaw); t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        left.add(d); left.add(Box.createVerticalStrut(6)); left.add(t); left.setPreferredSize(new Dimension(180,60));
        row.add(left, BorderLayout.WEST);

        JPanel center = new JPanel(); center.setOpaque(false); center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        JLabel docLbl = new JLabel(doc.isEmpty() ? "-" : doc); docLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        center.add(docLbl);
        row.add(center, BorderLayout.CENTER);

        JPanel right = new JPanel(); right.setOpaque(false); right.setLayout(new GridBagLayout());
        JLabel sLbl = new JLabel(status); sLbl.setFont(new Font("Segoe UI", Font.BOLD, 13)); sLbl.setForeground(statusColorOf(status));
        right.setPreferredSize(new Dimension(160,60)); right.add(sLbl);
        row.add(right, BorderLayout.EAST);

        return row;
    }

    // Creates a card-style panel for an upcoming appointment
    private JPanel createAppointmentCard(String[] a) {
        String doc = getField(a,7);
        String specialty = a.length > 8 ? getField(a,8) : "";
        String date = getField(a,4);
        String time = getField(a,5);
        String status = deriveStatus(a);
        Color statusColor = statusColorOf(status);

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0,6,0,0, statusColor),
                BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,220,220)), new EmptyBorder(12,12,12,12))
        ));
        p.setPreferredSize(new Dimension(380,160));

        String dateLabel;
        try { dateLabel = LocalDate.parse(date, FILE_DATE).format(CARD_DATE); } catch (Exception ex) { dateLabel = date; }

        JLabel dateLbl = new JLabel(dateLabel); dateLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel docLbl = new JLabel("Doctor: " + (doc.isEmpty() ? "-" : doc)); docLbl.setFont(LABEL_FONT);
        JLabel specLbl = new JLabel(specialty.isEmpty() ? "" : ("Specialty: " + specialty)); specLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13)); specLbl.setForeground(new Color(110,110,110));
        JLabel timeLbl = new JLabel("Time: " + (time.isEmpty() ? "-" : time)); timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JLabel statusLbl = new JLabel("Status: " + status); statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13)); statusLbl.setForeground(statusColor);

        JPanel center = new JPanel(); center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(dateLbl); center.add(Box.createVerticalStrut(8)); center.add(docLbl); center.add(specLbl); center.add(Box.createVerticalStrut(8)); center.add(timeLbl); center.add(Box.createVerticalStrut(6)); center.add(statusLbl);

        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // Creates a blank card panel used when no appointments exist
    private JPanel createBlankCard() {
        JPanel p = new JPanel();
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,220,220)), new EmptyBorder(12,12,12,12)));
        p.setPreferredSize(new Dimension(380,160));
        return p;
    }

    // Loads patient profile info into local variables and UI fields
    private void loadPatientInfo() {
        patientData = findPatientByUsername(currentUsername);
        if (profileFields != null) {
            if (patientData != null) {
                profileFields[0].setText(getField(patientData,0));
                profileFields[1].setText(getField(patientData,1));
                profileFields[2].setText(getField(patientData,3) + " " + getField(patientData,4));
                profileFields[3].setText(getField(patientData,5));
                profileFields[4].setText(getField(patientData,6) + " (Age: " + getField(patientData,7) + ")");
                profileFields[5].setText(getField(patientData,8));
                profileFields[6].setText(getField(patientData,9));
                profileFields[7].setText(getField(patientData,10) + ", " + getField(patientData,11) + ", " + getField(patientData,12));
            } else {
                for (JTextField tf : profileFields) tf.setText("N/A");
            }
        }
    }
    
    // Finds a patient record by username
    private String[] findPatientByUsername(String username) {
        Path p = Paths.get(PATIENTS_FILE);
        if (!Files.exists(p)) return null;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] parts = ln.split(Pattern.quote("|"), -1);
                if (parts.length >= 13 && parts[1].equalsIgnoreCase(username)) return parts;
            }
        } catch (IOException ex) {}
        return null;
    }

    // Counts the number of appointments for the patient
    private int countAppointments() {
        if (patientData == null) return 0;
        Path p = Paths.get(APPOINTMENTS_FILE);
        if (!Files.exists(p)) return 0;
        int cnt = 0;
        String patientId = getField(patientData,0);
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] a = ln.split(Pattern.quote("|"), -1);
                if (a.length >= 2 && getField(a,1).equals(patientId)) cnt++;
            }
        } catch (IOException ex) {}
        return cnt;
    }

    // Parses date and time strings into a LocalDateTime object
    private LocalDateTime parseAppointmentDateTime(String dateStr, String timeStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            LocalDate d = LocalDate.parse(dateStr.trim(), FILE_DATE);
            LocalTime t;
            if (timeStr == null || timeStr.trim().isEmpty()) t = LocalTime.of(23,59);
            else {
                String ts = timeStr.trim();
                try { t = LocalTime.parse(ts, FILE_TIME_HM); }
                catch (DateTimeParseException e1) {
                    try { t = LocalTime.parse(ts, FILE_TIME_HMS); }
                    catch (DateTimeParseException e2) {
                        String[] parts = ts.split(":");
                        int hh = Integer.parseInt(parts[0]); int mm = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        hh = Math.max(0, Math.min(23, hh)); mm = Math.max(0, Math.min(59, mm));
                        t = LocalTime.of(hh, mm);
                    }
                }
            }
            return LocalDateTime.of(d, t);
        } catch (NumberFormatException ex) { return null; }
    }
    
    // Checks if an appointment is in the past
    private boolean isAppointmentPastByDatetime(String[] a) {
        LocalDateTime dt = parseAppointmentDateTime(getField(a,4), getField(a,5));
        return dt != null && dt.isBefore(LocalDateTime.now());
    }

    // Derives the status of an appointment (Completed, Rescheduled, Upcoming)
    private String deriveStatus(String[] a) {
        if (a == null) return "";
        LocalDateTime dt = parseAppointmentDateTime(getField(a,4), getField(a,5));
        if (dt != null && dt.isBefore(LocalDateTime.now())) return "Completed";
        for (int i = Math.min(a.length - 1, 12); i >= 8; i--) {
            if (i >= 0 && i < a.length) {
                String s = getField(a,i);
                if (s.isBlank()) continue;
                String lower = s.trim().toLowerCase();
                if (lower.contains("resched") || lower.contains("reschedule") || lower.contains("rescheduled")) return "Rescheduled";
            }
        }
        return "Upcoming";
    }

    // Fetches upcoming appointments for the patient (limited number)
    private List<String[]> fetchUpcomingAppointments(int limit) {
        List<String[]> out = new ArrayList<>();
        if (patientData == null) return out;
        Path p = Paths.get(APPOINTMENTS_FILE);
        if (!Files.exists(p)) return out;
        String patientId = getField(patientData,0);
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] a = ln.split(Pattern.quote("|"), -1);
                if (a.length >= 2 && getField(a,1).equals(patientId) && !isAppointmentPastByDatetime(a)) out.add(a);
            }
        } catch (IOException ex) {}
        out.sort(Comparator.comparing(a -> {
            LocalDateTime dt = parseAppointmentDateTime(getField(a,4), getField(a,5));
            return dt == null ? LocalDateTime.MAX : dt;
        }));
        if (out.size() > limit) out = out.subList(0, limit);
        return out;
    }

    // Fetches recent appointments for the patient (limited number)
    private List<String[]> fetchRecentAppointments(int limit) {
        List<String[]> out = new ArrayList<>();
        if (patientData == null) return out;
        Path p = Paths.get(APPOINTMENTS_FILE);
        if (!Files.exists(p)) return out;
        String patientId = getField(patientData,0);
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln; while ((ln = br.readLine()) != null) {
                String[] a = ln.split(Pattern.quote("|"), -1);
                if (a.length >= 2 && getField(a,1).equals(patientId)) out.add(a);
            }
        } catch (IOException ex) {}
        out.sort((x,y) -> {
            LocalDateTime dx = parseAppointmentDateTime(getField(x,4), getField(x,5));
            LocalDateTime dy = parseAppointmentDateTime(getField(y,4), getField(y,5));
            if (dx == null && dy == null) return 0;
            if (dx == null) return 1;
            if (dy == null) return -1;
            return dy.compareTo(dx);
        });
        if (out.size() > limit) out = out.subList(0, limit);
        return out;
    }
    
    private static class Payment { 
        String id, appointmentId, method, timestamp; double amount; }
    private static class ApptRecord { 
        String appointmentId, date, time, treatments, feedback, medicines, closedAt; 
        double amount; }
    
    // Loads all payment records for the patient
    private List<Payment> loadPaymentsForPatient() {
        List<Payment> out = new ArrayList<>();
        if (patientData == null) return out;
        Path p = Paths.get(PAYMENTS_FILE);
        if (!Files.exists(p)) return out;
        String patientId = getField(patientData,0);
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln; while ((ln = br.readLine()) != null) {
                String[] a = ln.split(Pattern.quote("|"), -1);
                if (a.length >= 4 && getField(a,2).equals(patientId)) {
                    Payment pay = new Payment(); pay.id = getField(a,0); pay.appointmentId = getField(a,1); getField(a,2);
                    try { pay.amount = Double.parseDouble(getField(a,3)); } catch (NumberFormatException ex) { pay.amount = 0d; }
                    pay.method = getField(a,4); pay.timestamp = getField(a,5);
                    out.add(pay);
                }
            }
        } catch (IOException ex) {}
        return out;
    }

    // Calculates the total payments made by the patient
    private double getPaymentsMade() {
        double sum = 0d;
        for (Payment p : loadPaymentsForPatient()) sum += p.amount;
        return sum;
    }

    // Loads all appointment records for the patient
    private List<ApptRecord> loadAppointmentRecordsForPatient() {
        List<ApptRecord> out = new ArrayList<>();
        if (patientData == null) return out;
        Path p = Paths.get(APPT_RECORDS_FILE);
        if (!Files.exists(p)) return out;
        String patientId = getField(patientData,0);
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln; while ((ln = br.readLine()) != null) {
                String[] a = ln.split(Pattern.quote("|"), -1);
                if (a.length >= 2 && getField(a,1).equals(patientId)) {
                    ApptRecord r = new ApptRecord();
                    r.appointmentId = getField(a,0); getField(a,1); getField(a,2);
                    r.date = getField(a,4); r.time = getField(a,5);
                    try { r.amount = Double.parseDouble(getField(a,6)); } catch (NumberFormatException ex) { r.amount = 0d; }
                    r.treatments = a.length >= 8 ? getField(a,7) : "";
                    r.feedback = a.length >= 9 ? getField(a,8) : "";
                    r.medicines = a.length >= 10 ? getField(a,9) : "";
                    r.closedAt = a.length >= 11 ? getField(a,10) : "";
                    out.add(r);
                }
            }
        } catch (IOException ex) {}
        return out;
    }

    // Calculates the total payment due for the patient
    private double getPaymentsDueFiltered() {
        double totalDue = 0d;
        List<ApptRecord> recs = loadAppointmentRecordsForPatient();
        List<Payment> payments = loadPaymentsForPatient();
        for (ApptRecord r : recs) {
            double paid = 0d;
            for (Payment p : payments) if (p.appointmentId.equals(r.appointmentId)) paid += p.amount;
            double outstanding = r.amount - paid;
            if (outstanding > 0.0001) totalDue += outstanding;
        }
        return totalDue;
    }

    // Displays a dialog showing all payments made
    private void showPaymentsMadeDialog() {
        List<Payment> pays = loadPaymentsForPatient();
        String[] cols = {"Payment ID","Appointment ID","Amount (RM)","Method","Timestamp"};
        DefaultTableModel model = new DefaultTableModel(cols,0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        double total = 0d;
        for (Payment p : pays) { model.addRow(new Object[]{p.id, p.appointmentId, String.format("%.2f", p.amount), p.method, p.timestamp}); total += p.amount; }
        JTable t = new JTable(model); t.setRowHeight(26); t.setFont(FIELD_FONT); t.getTableHeader().setFont(LABEL_FONT);
        JPanel panel = new JPanel(new BorderLayout()); panel.add(new JScrollPane(t), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bottom.add(new JLabel("Total payments: RM " + String.format("%.2f", total))); panel.add(bottom, BorderLayout.SOUTH);
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Payments Made", Dialog.ModalityType.APPLICATION_MODAL); dlg.setContentPane(panel); dlg.setSize(800,420); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
    }

    // Displays a dialog showing payment dues for appointments
    private void showPaymentsDueDialogFiltered() {
        List<ApptRecord> recs = loadAppointmentRecordsForPatient();
        List<Payment> payments = loadPaymentsForPatient();
        String[] cols = {"Appt ID","Date","Amount","Paid","Outstanding","Treatments"};
        DefaultTableModel model = new DefaultTableModel(cols,0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        double totalDue = 0d;
        for (ApptRecord r : recs) {
            double paid = 0d;
            for (Payment p : payments) if (p.appointmentId.equals(r.appointmentId)) paid += p.amount;
            double outstanding = r.amount - paid;
            if (outstanding <= 0.0001) continue;
            model.addRow(new Object[]{r.appointmentId, r.date + " " + r.time, String.format("%.2f", r.amount), String.format("%.2f", paid), String.format("%.2f", outstanding), r.treatments});
            totalDue += outstanding;
        }
        JTable t = new JTable(model); t.setRowHeight(26); t.setFont(FIELD_FONT); t.getTableHeader().setFont(LABEL_FONT);
        JPanel panel = new JPanel(new BorderLayout()); panel.add(new JScrollPane(t), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bottom.add(new JLabel("Total outstanding due: RM " + String.format("%.2f", totalDue))); panel.add(bottom, BorderLayout.SOUTH);
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Payment Dues", Dialog.ModalityType.APPLICATION_MODAL); dlg.setContentPane(panel); dlg.setSize(900,480); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
    }

    // Safely retrieves a field from a string array (handles nulls/bounds)
    private static String getField(String[] a, int i) { return (a != null && i >= 0 && i < a.length && a[i] != null) ? a[i] : ""; }

    // Styles a button with primary theme colors
    private void stylePrimary(AbstractButton b) {
        b.setBackground(new Color(0,123,200));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
    }
   
    // Opens the feedback module panel for the patient
    private void openFeedbackModule() {
        if (patientData == null) loadPatientInfo();
        String patientId = getField(patientData, 0);
        for (Component c : contentPanel.getComponents()) {
            if ("feedbackPanel".equals(c.getName())) {
                contentPanel.remove(c);
                break;
            }
        }
        try {
            Class<?> cls = Class.forName(FEEDBACK_CLASS_NAME);
            Constructor<?> ctor = cls.getConstructor(String.class); 
            Object instance = ctor.newInstance(patientId);
            if (instance instanceof JPanel feedbackPanel) {
                feedbackPanel.setName("feedbackPanel");
                contentPanel.add(feedbackPanel, "feedbackPanel");
                cardLayout.show(contentPanel, "feedbackPanel");
                contentPanel.revalidate();
                contentPanel.repaint();
            } else {
                JOptionPane.showMessageDialog(this,
                    "Feedback class exists but is not a JPanel.",
                    "Feedback error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (ClassNotFoundException cnf) {
            JOptionPane.showMessageDialog(this,
                "Feedback module not found.\nExpected class: " + FEEDBACK_CLASS_NAME,
                "Feedback missing", JOptionPane.INFORMATION_MESSAGE);
        } catch (NoSuchMethodException nsme) {
            JOptionPane.showMessageDialog(this,
                "Feedback class found but constructor signature not matching.\nExpected: (String patientId)",
                "Feedback error", JOptionPane.ERROR_MESSAGE);
        } catch (HeadlessException | IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to open feedback: " + ex.getMessage(),
                "Feedback error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    // Creates a small statistic card panel
    private JPanel createStatCard(String title, String value, Color bg) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true); card.setBackground(bg); card.setBorder(new EmptyBorder(12,12,12,12));
        JLabel t = new JLabel(title); t.setForeground(Color.WHITE); t.setFont(LABEL_FONT);
        JLabel v = new JLabel(value); v.setForeground(Color.WHITE); v.setFont(new Font("Segoe UI", Font.BOLD, 24));
        card.add(t, BorderLayout.NORTH); card.add(v, BorderLayout.CENTER);
        return card;
    }
    
    
    // Creates a statistic card panel with a button
    private JPanel createStatCardWithButton(String title, String value, Color bg, String btnText, ActionListener action) {
        JPanel card = createStatCard(title, value, bg);
        JButton b = new JButton(btnText); b.setFont(new Font("Segoe UI", Font.BOLD, 12)); b.addActionListener(action);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT)); south.setOpaque(false); south.add(b);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    // Returns a color corresponding to the appointment status
    private Color statusColorOf(String status) {
        if (status == null) return new Color(33,150,243);
        String s = status.toLowerCase();
        if (s.contains("completed")) return new Color(46,125,50);
        if (s.contains("resched")) return new Color(255,193,7);
        return new Color(33,150,243);
    }
}

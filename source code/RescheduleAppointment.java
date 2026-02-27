package assignment;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class RescheduleAppointment extends JPanel {
    static class Doctor {
        String id, shift;
        Set<String> bookedSlots = new HashSet<>();
        Doctor(String id, String username, String firstName, String lastName, String specialization, String shift) {
            this.id = id == null ? "" : id;
            this.shift = shift == null ? "" : shift;
        }
        boolean isAvailable(String dateTimeKey) { return dateTimeKey!=null && !bookedSlots.contains(dateTimeKey); }
        void bookSlot(String dateTimeKey) { if (dateTimeKey!=null && !dateTimeKey.isBlank()) bookedSlots.add(dateTimeKey); }
        void unbookSlot(String dateTimeKey) { if (dateTimeKey!=null) bookedSlots.remove(dateTimeKey); }
        static Doctor fromLine(String line) {
            if (line == null || line.isBlank()) return null;
            String[] p = line.split("\\|", -1);
            String id = p.length>0?p[0]:"";
            String username = p.length>1?p[1]:"";
            String firstName = p.length>2?p[2]:"";
            String lastName = p.length>3?p[3]:"";
            String specialization = p.length>13?p[13]:"";
            String shift = p.length>14?p[14]:"";
            return new Doctor(id, username, firstName, lastName, specialization, shift);
        }
    }

    private static final String APPT_FILE = "appointments.txt";
    private static final String ARCHIVE_FILE = "appointments_deleted.txt";
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter stampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Map<String, Doctor> doctorMap = new HashMap<>();
    private DefaultTableModel model;
    private JTable table;
    private final JTextField txtAppointmentID = new JTextField(10);
    private final JTextArea logArea = new JTextArea();

    private final JPanel calendarPanel = new JPanel();
    private final JPanel slotPanel = new JPanel();
    private final JLabel monthLabel = new JLabel();
    private YearMonth currentYearMonth = YearMonth.now();
    private LocalDate selectedDate = null;

    private String selectedApptId = null;
    private String selectedDoctorId = null;
    private String oldSlotKey = null;

    private final String currentStaffUsername;

    // right panel (calendar/slots)
    private final JPanel rightPanel = new JPanel(new BorderLayout());
    private final JLabel rescheduleInfoLabel = new JLabel();
    private JSplitPane splitPane;
    private boolean restoreMode = false;
    private ArchivedEntry restoreEntry = null;

    private static class ArchivedEntry { String timestamp, deletedBy, originalLine; String[] originalParts; }

    public RescheduleAppointment(String currentStaffUsername) {
        this.currentStaffUsername = currentStaffUsername == null ? "unknown" : currentStaffUsername;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1000, 700));
        loadDoctors();
        loadAppointmentsIntoDoctors();
        setupUI();
        loadAppointmentsTable();
        generateCalendar();
    }
    
    // Initializes UI components: toolbar, table, split panes, calendar, slot panels, log area
    private void setupUI() {
        // top: title + legend
        JPanel top = new JPanel(new BorderLayout());
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftTop.setBorder(new EmptyBorder(6,6,6,6));
        JLabel title = new JLabel("Manage Appointments (Reschedule / Delete / Restore)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        leftTop.add(title);
        top.add(leftTop, BorderLayout.WEST);

        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,6));
        rightTop.add(colorLegend(Color.RED,"Upcoming"));
        rightTop.add(colorLegend(new Color(0xFFF59D),"Rescheduled"));
        rightTop.add(colorLegend(new Color(0x4CAF50),"Completed"));
        top.add(rightTop, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        // center: split
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.65);
        splitPane.setContinuousLayout(true);

        // left panel: toolbar + table
        JPanel left = new JPanel(new BorderLayout(6,6));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JLabel("Appointment ID:"));
        toolbar.add(txtAppointmentID);
        JButton btnSearch = new JButton("Search");
        JButton btnLoadAll = new JButton("Load All");
        JButton btnView = new JButton("View");
        JButton btnViewDeleted = new JButton("View Deleted");
        toolbar.add(btnSearch); toolbar.add(btnLoadAll); toolbar.add(btnView); toolbar.add(btnViewDeleted);
        left.add(toolbar, BorderLayout.NORTH);

        String[] cols = {"Appt ID","Patient ID","First Name","Last Name",
                "Date","Time","Doctor ID","Doctor Name","Faculty","Shift","Booked On","Booked By","Status","Reschedule","Delete"};
        model = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c) { return false; } // make non-editable — we handle clicks via mouse
        };
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        int[] pref = {80,80,110,110,90,60,80,130,120,70,160,90,90,100,80};
        for (int i=0;i<pref.length && i<table.getColumnModel().getColumnCount();i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(pref[i]);
        }

        // Status renderer (colored) and button-like renderers for columns 13 & 14
        table.getColumn("Status").setCellRenderer(new StatusRenderer());
        table.getColumn("Reschedule").setCellRenderer(new ActionButtonRenderer("Reschedule"));
        table.getColumn("Delete").setCellRenderer(new ActionButtonRenderer("Delete"));

        JScrollPane sc = new JScrollPane(table);
        sc.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        left.add(sc, BorderLayout.CENTER);
        splitPane.setLeftComponent(left);

        // mouse listener to handle clicks on "Reschedule" and "Delete"
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewRow == -1 || viewCol == -1) return;

                // convert to model indices (in case table is sorted)
                int modelRow = table.convertRowIndexToModel(viewRow);
                int modelCol = table.convertColumnIndexToModel(viewCol);

                String colName = model.getColumnName(modelCol);
                if ("Reschedule".equals(colName)) {
                    // if this row is completed, ignore
                    if (isRowCompleted(modelRow)) { JOptionPane.showMessageDialog(RescheduleAppointment.this, "Completed appointment cannot be rescheduled."); return; }
                    selectedApptId = String.valueOf(model.getValueAt(modelRow,0));
                    selectedDoctorId = String.valueOf(model.getValueAt(modelRow,6));
                    String dateStr = String.valueOf(model.getValueAt(modelRow,4));
                    String timeStr = String.valueOf(model.getValueAt(modelRow,5));
                    try { selectedDate = LocalDate.parse(dateStr, dateFmt); } catch (Exception ex) { selectedDate = LocalDate.now(); }
                    oldSlotKey = dateStr + "-" + timeStr;
                    currentYearMonth = YearMonth.of(selectedDate.getYear(), selectedDate.getMonthValue());
                    updateMonthLabel(); generateCalendar(); loadSlotsForDate(selectedDate);
                    rescheduleInfoLabel.setText("Rescheduling Appt " + selectedApptId + " — select new date/time");
                    restoreMode = false; restoreEntry = null; showRightPanel();
                } else if ("Delete".equals(colName)) {
                    String apptId = String.valueOf(model.getValueAt(modelRow,0));
                    int confirm = JOptionPane.showConfirmDialog(RescheduleAppointment.this, "Delete appointment " + apptId + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        if (deleteAppointmentAndArchive(apptId)) {
                            JOptionPane.showMessageDialog(RescheduleAppointment.this, "Deleted and archived.");
                            loadAppointmentsTable(); loadAppointmentsIntoDoctors(); hideRightPanel();
                        } else JOptionPane.showMessageDialog(RescheduleAppointment.this, "Delete failed.");
                    }
                }
            }
        });

        // toolbar actions
        btnLoadAll.addActionListener(e -> { loadAppointmentsTable(); log("Loaded all appointments"); });
        btnSearch.addActionListener(e -> {
            String id = txtAppointmentID.getText().trim();
            if (id.isEmpty()) { JOptionPane.showMessageDialog(this,"Enter Appointment ID or press Load All."); return; }
            loadAppointmentsTableFiltered(id); log("Searched " + id);
        });
        btnView.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r==-1) { JOptionPane.showMessageDialog(this,"Select a row first."); return; }
            showAppointmentDetails(table.convertRowIndexToModel(r));
        });
        btnViewDeleted.addActionListener(e -> showDeletedDialog());

        // right panel (calendar + slots) setup — hidden initially
        JPanel calHeader = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton prev = new JButton("<"), next = new JButton(">");
        monthLabel.setFont(new Font("Arial", Font.BOLD, 14));
        calHeader.add(prev); calHeader.add(monthLabel); calHeader.add(next);
        prev.addActionListener(e -> { currentYearMonth = currentYearMonth.minusMonths(1); updateMonthLabel(); generateCalendar(); });
        next.addActionListener(e -> { currentYearMonth = currentYearMonth.plusMonths(1); updateMonthLabel(); generateCalendar(); });

        calendarPanel.setLayout(new GridLayout(0,7,5,5));
        calendarPanel.setBorder(BorderFactory.createTitledBorder("Select a Date (within 30 days)"));

        slotPanel.setLayout(new GridLayout(0,3,8,8));
        slotPanel.setBorder(BorderFactory.createTitledBorder("Available Slots"));

        JScrollPane slotScroll = new JScrollPane(slotPanel);
        slotScroll.setPreferredSize(new Dimension(380,240));

        JPanel topRight = new JPanel(new BorderLayout());
        rescheduleInfoLabel.setBorder(new EmptyBorder(6,6,6,6));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> { restoreMode = false; restoreEntry = null; hideRightPanel(); });
        topRight.add(rescheduleInfoLabel, BorderLayout.WEST);
        topRight.add(cancel, BorderLayout.EAST);

        rightPanel.add(topRight, BorderLayout.NORTH);
        JPanel calWrap = new JPanel(new BorderLayout());
        calWrap.add(calHeader, BorderLayout.NORTH);
        calWrap.add(calendarPanel, BorderLayout.CENTER);
        rightPanel.add(calWrap, BorderLayout.CENTER);
        rightPanel.add(slotScroll, BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(420,420));
        rightPanel.setVisible(false);

        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);
        logArea.setEditable(false);
        JScrollPane logSc = new JScrollPane(logArea);
        logSc.setPreferredSize(new Dimension(getWidth(), 120));
        add(logSc, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow==-1) return;
            int modelRow = table.convertRowIndexToModel(viewRow);
            selectedApptId = String.valueOf(model.getValueAt(modelRow,0));
            selectedDoctorId = String.valueOf(model.getValueAt(modelRow,6));
            String dateStr = String.valueOf(model.getValueAt(modelRow,4));
            String timeStr = String.valueOf(model.getValueAt(modelRow,5));
            try {
                selectedDate = LocalDate.parse(dateStr, dateFmt);
                oldSlotKey = dateStr + "-" + timeStr;
                currentYearMonth = YearMonth.of(selectedDate.getYear(), selectedDate.getMonthValue());
            } catch (Exception ignored) {}
        });
    }

    // Show the right panel (calendar + slots) for rescheduling or restoring
    private void showRightPanel() {
        SwingUtilities.invokeLater(() -> {
            splitPane.setRightComponent(rightPanel);
            rightPanel.setVisible(true);
            try {
                splitPane.setDividerLocation(0.65);
            } catch (Exception ex) {
                int width = splitPane.getWidth();
                if (width > 0) splitPane.setDividerLocation((int)(width * 0.65));
                else splitPane.setDividerLocation(650);
            }
            rightPanel.revalidate(); rightPanel.repaint();
        });
    }
    
    // Hide the right panel
    private void hideRightPanel() {
        SwingUtilities.invokeLater(() -> {
            rightPanel.setVisible(false);
            try { splitPane.setDividerLocation(1.0); } catch (Exception ignored) {}
            splitPane.revalidate(); splitPane.repaint();
        });
    }

    // Create a small colored legend box for status indicators
    private Component colorLegend(Color c, String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        JLabel box = new JLabel("  "); box.setOpaque(true); box.setBackground(c); box.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        p.add(box); p.add(new JLabel(label)); return p;
    }
    
    // Update the month label in calendar header
    private void updateMonthLabel() { 
        monthLabel.setText(currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))); 
    }
    
    // Append a message to the log area
    private void log(String s) { 
        logArea.append(s + "\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); 
    }

    // Show detailed information for a selected appointment in a dialog
    private void showAppointmentDetails(int modelRow) {
        if (modelRow<0 || modelRow>=model.getRowCount()) return;
        StringBuilder sb = new StringBuilder();
        for (int c=0;c<model.getColumnCount();c++) sb.append(model.getColumnName(c)).append(": ").append(String.valueOf(model.getValueAt(modelRow,c))).append("\n");
        JTextArea ta = new JTextArea(sb.toString()); ta.setEditable(false); ta.setRows(14); ta.setColumns(50);
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Appointment Details", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------------- DOCTOR DATA ----------------
    // Load doctor metadata from "doctors.txt" into doctorMap
    private void loadDoctors() {
        doctorMap.clear();
        File f = new File("doctors.txt");
        if (!f.exists()) { log("doctors.txt not found - continuing without doctor metadata."); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ln; while ((ln = br.readLine()) != null) {
                Doctor d = Doctor.fromLine(ln); if (d != null && d.id != null && !d.id.isBlank()) doctorMap.put(d.id, d);
            }
        } catch (IOException e) { JOptionPane.showMessageDialog(this,"Error reading doctors.txt: " + e.getMessage()); }
    }

    // Populate booked slots for each doctor based on existing appointments
    private void loadAppointmentsIntoDoctors() {
        for (Doctor d : doctorMap.values()) d.bookedSlots.clear();
        File f = new File(APPT_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ln; while ((ln = br.readLine()) != null) {
                if (ln.trim().isEmpty()) continue;
                String[] p = ln.split("\\|", -1);
                if (p.length >= 7) {
                    Doctor d = doctorMap.get(p[6]);
                    if (d != null && p.length > 5) d.bookSlot(p[4] + "-" + p[5]);
                }
            }
        } catch (IOException ex) { log("Error reading appointments: " + ex.getMessage()); }
    }

    // Determine the effective status of an appointment (Completed / Upcoming / Rescheduled)
    private String effectiveStatus(String fileStatus, String dateStr, String timeStr) {
        try {
            LocalDate d = LocalDate.parse(dateStr, dateFmt);
            LocalTime t = LocalTime.parse(timeStr, timeFmt);
            LocalDate today = LocalDate.now(); LocalTime now = LocalTime.now();
            if (d.isBefore(today) || (d.isEqual(today) && t.isBefore(now))) return "Completed";
        } catch (Exception ignored) {}
        if (fileStatus != null && !fileStatus.isBlank()) return fileStatus;
        return "Upcoming";
    }

    // ---------------- APPOINTMENT TABLE ----------------
    // Load all appointments into the JTable
    private void loadAppointmentsTable() {
        model.setRowCount(0);
        File f = new File(APPT_FILE);
        if (!f.exists()) { log("appointments.txt not found."); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ln; while ((ln = br.readLine()) != null) {
                if (ln.trim().isEmpty()) continue;
                String[] p = ln.split("\\|", -1);
                Object[] row = new Object[model.getColumnCount()];
                for (int i=0;i<12;i++) row[i] = i < p.length ? p[i] : "";
                String fileStatus = p.length >= 13 ? p[12] : "";
                row[12] = effectiveStatus(fileStatus, String.valueOf(row[4]), String.valueOf(row[5]));
                row[13] = "Reschedule"; row[14] = "Delete";
                model.addRow(row);
            }
        } catch (IOException ex) { JOptionPane.showMessageDialog(this,"Error loading appointments: " + ex.getMessage()); }
    }

    // Load only appointments matching a specific ID into the JTable
    private void loadAppointmentsTableFiltered(String apptId) {
        model.setRowCount(0);
        File f = new File(APPT_FILE);
        if (!f.exists()) { log("appointments.txt not found."); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ln; while ((ln = br.readLine()) != null) {
                if (ln.trim().isEmpty()) continue;
                String[] p = ln.split("\\|", -1);
                if (p.length >= 1 && p[0].equals(apptId)) {
                    Object[] row = new Object[model.getColumnCount()];
                    for (int i=0;i<12;i++) row[i] = i < p.length ? p[i] : "";
                    String fileStatus = p.length >= 13 ? p[12] : "";
                    row[12] = effectiveStatus(fileStatus, String.valueOf(row[4]), String.valueOf(row[5]));
                    row[13] = "Reschedule"; row[14] = "Delete";
                    model.addRow(row);
                }
            }
        } catch (IOException ex) { JOptionPane.showMessageDialog(this,"Error searching appointments: " + ex.getMessage()); }
    }

    // ---------------- CALENDAR & SLOTS ----------------
    // Generate calendar buttons for current month, highlighting selectable dates
    private void generateCalendar() {
        calendarPanel.removeAll();
        updateMonthLabel();
        String[] headers = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        for (String h : headers) { JLabel l = new JLabel(h, SwingConstants.CENTER); l.setFont(new Font("Arial", Font.BOLD, 12)); calendarPanel.add(l); }
        LocalDate firstOf = currentYearMonth.atDay(1);
        int pad = firstOf.getDayOfWeek().getValue() % 7;
        for (int i=0;i<pad;i++) calendarPanel.add(new JLabel(""));
        LocalDate today = LocalDate.now();
        LocalDate max = today.plusDays(30);
        int len = currentYearMonth.lengthOfMonth();
        for (int d=1; d<=len; d++) {
            LocalDate date = currentYearMonth.atDay(d);
            JButton b = new JButton(String.valueOf(d));
            b.setMargin(new Insets(2,4,2,4)); b.setFocusPainted(false); b.setFont(new Font("SansSerif", Font.PLAIN, 12));
            if (date.isBefore(today) || date.isAfter(max)) { b.setEnabled(false); b.setBackground(Color.LIGHT_GRAY); }
            else { b.setBackground(new Color(204,255,204)); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                b.addActionListener(e -> { selectedDate = date; loadSlotsForDate(date); });
            }
            if (selectedDate != null && selectedDate.equals(date)) b.setBorder(BorderFactory.createLineBorder(Color.ORANGE,2));
            calendarPanel.add(b);
        }
        calendarPanel.revalidate(); calendarPanel.repaint();
    }

    // Load available time slots for a selected date and doctor
    private void loadSlotsForDate(LocalDate date) {
        slotPanel.removeAll();
        if (date == null) return;
        loadDoctors(); loadAppointmentsIntoDoctors();

        String docId = restoreMode && restoreEntry != null && restoreEntry.originalParts!=null && restoreEntry.originalParts.length>6
                ? restoreEntry.originalParts[6] : selectedDoctorId;
        if (docId == null || docId.isBlank()) { JOptionPane.showMessageDialog(this,"No doctor selected for this operation."); return; }
        Doctor doc = doctorMap.get(docId);
        if (doc == null) { JOptionPane.showMessageDialog(this,"Doctor data missing for appointment (" + docId + ")."); return; }

        String shift = doc.shift == null ? "" : doc.shift.toLowerCase().trim();
        LocalTime shiftStart, shiftEnd;
        switch (shift) {
            case "shift c" -> {
                shiftStart = LocalTime.MIDNIGHT; shiftEnd = LocalTime.of(7,30);
            }
            case "shift a" -> {
                shiftStart = LocalTime.of(8,0); shiftEnd = LocalTime.of(15,30);
            }
            case "shift b" -> {
                shiftStart = LocalTime.of(16,0); shiftEnd = LocalTime.of(23,30);
            }
            default -> {
                JOptionPane.showMessageDialog(this,"Invalid shift for doctor: " + doc.shift); return;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cur = LocalDateTime.of(date, shiftStart);
        LocalDateTime end = LocalDateTime.of(date, shiftEnd);

        while (!cur.isAfter(end)) {
            String ts = cur.toLocalTime().format(timeFmt);
            String key = cur.toLocalDate().toString() + "-" + ts;
            JButton slotBtn = new JButton(ts); slotBtn.setEnabled(false); slotBtn.setOpaque(true);

            if (cur.toLocalDate().isEqual(now.toLocalDate()) && cur.toLocalTime().isBefore(now.toLocalTime())) {
                slotBtn.setBackground(Color.DARK_GRAY); slotPanel.add(slotBtn); cur = cur.plusMinutes(30); continue;
            }

            boolean isOld = oldSlotKey != null && oldSlotKey.equals(key) && !restoreMode;

            if (doc.isAvailable(key) || isOld) {
                slotBtn.setBackground(Color.GREEN); slotBtn.setEnabled(true);
                final LocalDate chosenDate = cur.toLocalDate(); final String chosenTime = ts;
                slotBtn.addActionListener(e -> {
                    if (!restoreMode) {
                        int viewRow = table.getSelectedRow(); if (viewRow == -1) { JOptionPane.showMessageDialog(this,"Select an appointment first."); return; }
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        if (isRowCompleted(modelRow)) { JOptionPane.showMessageDialog(this,"Completed appointments cannot be rescheduled."); return; }
                        String apptId = String.valueOf(model.getValueAt(modelRow,0));
                        String oldDate = String.valueOf(model.getValueAt(modelRow,4));
                        String oldTime = String.valueOf(model.getValueAt(modelRow,5));
                        int confirm = JOptionPane.showConfirmDialog(this,
                                "Reschedule " + apptId + "\nFrom: " + oldDate + " " + oldTime + "\nTo: " + chosenDate + " " + chosenTime + "\nConfirm?",
                                "Confirm Reschedule", JOptionPane.YES_NO_OPTION);
                        if (confirm != JOptionPane.YES_OPTION) return;
                        loadAppointmentsIntoDoctors();
                        Doctor checkDoc = doctorMap.get(selectedDoctorId);
                        boolean still = checkDoc != null && (checkDoc.isAvailable(chosenDate + "-" + chosenTime) || isOld);
                        if (!still) { JOptionPane.showMessageDialog(this,"Slot taken. Choose another."); loadSlotsForDate(selectedDate); return; }
                        if (performReschedule(apptId, chosenDate, chosenTime)) {
                            JOptionPane.showMessageDialog(this,"Rescheduled.");
                            loadAppointmentsTable(); loadAppointmentsIntoDoctors();
                            oldSlotKey = chosenDate + "-" + chosenTime;
                            selectedDate = chosenDate; currentYearMonth = YearMonth.of(chosenDate.getYear(), chosenDate.getMonthValue());
                            updateMonthLabel(); generateCalendar(); loadSlotsForDate(selectedDate);
                            hideRightPanel();
                        }
                    } else {
                        if (restoreEntry == null) { JOptionPane.showMessageDialog(this,"Restore context missing."); return; }
                        loadAppointmentsIntoDoctors();
                        String docIndex = restoreEntry.originalParts.length>6 ? restoreEntry.originalParts[6] : null;
                        Doctor checkDoc = docIndex!=null?doctorMap.get(docIndex):null;
                        if (checkDoc == null || !checkDoc.isAvailable(chosenDate + "-" + chosenTime)) {
                            JOptionPane.showMessageDialog(this,"Slot taken. Choose another."); loadSlotsForDate(selectedDate); return;
                        }
                        finalizeRestoreWithSelection(chosenDate, chosenTime);
                    }
                });
            } else slotBtn.setBackground(Color.RED);

            slotPanel.add(slotBtn);
            cur = cur.plusMinutes(30);
        }
        slotPanel.revalidate(); slotPanel.repaint();
    }

    // Perform the actual rescheduling: update appointment file, update doctor slots
    private boolean performReschedule(String apptId, LocalDate newDate, String newTime) {
        if (apptId == null || apptId.isBlank()) { JOptionPane.showMessageDialog(this,"No appointment selected."); return false; }
        File in = new File(APPT_FILE); if (!in.exists()) { JOptionPane.showMessageDialog(this,"appointments.txt not found."); return false; }
        File tmp = new File(APPT_FILE + ".tmp");
        boolean found = false;
        String nowStamp = LocalDateTime.now().format(stampFmt);
        LocalDate today = LocalDate.now(); LocalTime now = LocalTime.now();

        try (BufferedReader r = new BufferedReader(new FileReader(in)); BufferedWriter w = new BufferedWriter(new FileWriter(tmp))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.trim().isEmpty()) { w.write(ln + System.lineSeparator()); continue; }
                String[] p = ln.split("\\|", -1);
                if (p.length >= 1 && p[0].equals(apptId)) {
                    found = true;
                    String fileStatus = p.length >= 13 ? p[12] : "";
                    boolean statusCompleted = "completed".equalsIgnoreCase(fileStatus);
                    boolean elapsed = false;
                    try {
                        LocalDate d = LocalDate.parse(p[4], dateFmt);
                        LocalTime t2 = LocalTime.parse(p[5], timeFmt);
                        elapsed = d.isBefore(today) || (d.isEqual(today) && t2.isBefore(now));
                    } catch (Exception ignored) {}
                    if (statusCompleted || elapsed) { JOptionPane.showMessageDialog(this,"Completed appointments cannot be rescheduled."); w.write(ln + System.lineSeparator()); continue; }

                    String[] newParts = Arrays.copyOf(p, Math.max(p.length, 13));
                    newParts[4] = newDate.toString();
                    newParts[5] = newTime;
                    newParts[10] = nowStamp;
                    newParts[11] = currentStaffUsername;
                    newParts[12] = "Rescheduled";
                    w.write(String.join("|", newParts) + System.lineSeparator());

                    if (p.length >= 7) {
                        Doctor dd = doctorMap.get(p[6]);
                        if (dd != null && p.length > 5) { dd.unbookSlot(p[4] + "-" + p[5]); dd.bookSlot(newDate.toString() + "-" + newTime); }
                    }
                } else w.write(ln + System.lineSeparator());
            }
        } catch (IOException ex) { JOptionPane.showMessageDialog(this,"Error rescheduling: " + ex.getMessage()); return false; }

        if (!found) { tmp.delete(); JOptionPane.showMessageDialog(this,"Appointment ID not found."); return false; }
        if (!in.delete()) { JOptionPane.showMessageDialog(this,"Could not delete original file."); return false; }
        if (!tmp.renameTo(in)) { JOptionPane.showMessageDialog(this,"Could not rename temp file."); return false; }
        return true;
    }

    // Delete an appointment and archive it into "appointments_deleted.txt"
    private boolean deleteAppointmentAndArchive(String apptId) {
        if (apptId == null || apptId.isBlank()) { JOptionPane.showMessageDialog(this,"No appointment selected."); return false; }
        File in = new File(APPT_FILE); if (!in.exists()) { JOptionPane.showMessageDialog(this,"appointments.txt missing."); return false; }
        File tmp = new File(APPT_FILE + ".tmp");
        File arch = new File(ARCHIVE_FILE);
        boolean found = false;
        String ts = LocalDateTime.now().format(stampFmt);

        try (BufferedReader r = new BufferedReader(new FileReader(in));
             BufferedWriter w = new BufferedWriter(new FileWriter(tmp));
             BufferedWriter a = new BufferedWriter(new FileWriter(arch, true))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.trim().isEmpty()) { w.write(ln + System.lineSeparator()); continue; }
                String[] p = ln.split("\\|", -1);
                if (p.length >= 1 && p[0].equals(apptId)) {
                    found = true;
                    if (p.length >= 7) {
                        Doctor dd = doctorMap.get(p[6]);
                        if (dd != null && p.length > 5) dd.unbookSlot(p[4] + "-" + p[5]);
                    }
                    a.write(ts + "|" + currentStaffUsername + "|" + ln + System.lineSeparator());
                    continue;
                }
                w.write(ln + System.lineSeparator());
            }
        } catch (IOException ex) { JOptionPane.showMessageDialog(this,"Error deleting appointment: " + ex.getMessage()); return false; }

        if (!found) { tmp.delete(); JOptionPane.showMessageDialog(this,"Appointment ID not found."); return false; }
        if (!in.delete()) { JOptionPane.showMessageDialog(this,"Could not delete original file."); return false; }
        if (!tmp.renameTo(in)) { JOptionPane.showMessageDialog(this,"Could not rename temp file."); return false; }
        loadAppointmentsIntoDoctors();
        return true;
    }

    // Show dialog listing deleted/archived appointments
    private void showDeletedDialog() {
        File arch = new File(ARCHIVE_FILE);
        if (!arch.exists()) { JOptionPane.showMessageDialog(this,"No archived appointments found."); return; }
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(arch))) { String ln; while ((ln = br.readLine()) != null) if (!ln.trim().isEmpty()) lines.add(ln); }
        catch (IOException ex) { JOptionPane.showMessageDialog(this,"Error reading archive: " + ex.getMessage()); return; }
        if (lines.isEmpty()) { JOptionPane.showMessageDialog(this,"No archived appointments found."); return; }

        String[] cols = {"Index","Deleted On","Deleted By","ApptID","Patient","Date","Time","Doctor"};
        DefaultTableModel am = new DefaultTableModel(cols,0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        List<ArchivedEntry> entries = new ArrayList<>();
        for (int i=0;i<lines.size();i++) {
            String line = lines.get(i);
            String[] tri = line.split("\\|",3);
            if (tri.length < 3) continue;
            String ts = tri[0], deletedBy = tri[1], original = tri[2];
            String[] p = original.split("\\|", -1);
            String apptID = p.length>0?p[0]:"";
            String patient = (p.length>2? p[2] : "") + " " + (p.length>3? p[3] : "");
            String date = p.length>4? p[4] : "";
            String time = p.length>5? p[5] : "";
            String doctorName = p.length>7? p[7] : "";
            am.addRow(new Object[]{i+1, ts, deletedBy, apptID, patient, date, time, doctorName});
            ArchivedEntry ae = new ArchivedEntry(); ae.timestamp = ts; ae.deletedBy = deletedBy; ae.originalLine = original; ae.originalParts = p;
            entries.add(ae);
        }

        JTable at = new JTable(am); at.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sc = new JScrollPane(at); sc.setPreferredSize(new Dimension(900,300));
        JButton restoreBtn = new JButton("Restore Selected"), closeBtn = new JButton("Close");
        JPanel btm = new JPanel(new FlowLayout(FlowLayout.RIGHT)); btm.add(restoreBtn); btm.add(closeBtn);

        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Archived Appointments", true);
        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(sc, BorderLayout.CENTER); dlg.getContentPane().add(btm, BorderLayout.SOUTH);
        dlg.pack(); dlg.setLocationRelativeTo(this);

        restoreBtn.addActionListener(ev -> {
            int sel = at.getSelectedRow();
            if (sel == -1) { JOptionPane.showMessageDialog(dlg,"Select an archived appointment to restore."); return; }
            dlg.dispose();
            restoreEntry = entries.get(sel);
            SwingUtilities.invokeLater(() -> initiateRestore(restoreEntry));
        });
        closeBtn.addActionListener(ev -> dlg.dispose());
        dlg.setVisible(true);
    }

    // Initiate restore workflow for an archived appointment
    private void initiateRestore(ArchivedEntry ae) {
        if (ae == null) return;
        String[] p = ae.originalParts;
        if (p == null || p.length < 6) { JOptionPane.showMessageDialog(this,"Corrupt archived appointment."); restoreEntry = null; return; }
        boolean elapsed = false;
        try {
            LocalDate d = LocalDate.parse(p[4], dateFmt);
            LocalTime t = LocalTime.parse(p[5], timeFmt);
            LocalDate today = LocalDate.now(); LocalTime now = LocalTime.now();
            if (d.isBefore(today) || (d.isEqual(today) && t.isBefore(now))) elapsed = true;
        } catch (Exception e) { elapsed = true; }

        if (elapsed) {
            int ans = JOptionPane.showConfirmDialog(this,
                    "Appointment has elapsed.\nDo you want to choose a new date/time?",
                    "Restore - Choose New Date/Time?", JOptionPane.YES_NO_OPTION);
            if (ans != JOptionPane.YES_OPTION) { restoreEntry = null; return; }
            restoreMode = true;
            rescheduleInfoLabel.setText("Restoring archived appointment: " + p[0] + " — pick a new date/time");
            selectedDoctorId = p.length>6 ? p[6] : null;
            selectedDate = LocalDate.now();
            currentYearMonth = YearMonth.from(selectedDate);
            updateMonthLabel(); generateCalendar(); loadSlotsForDate(selectedDate);
            showRightPanel();
        } else {
            String docId = p.length>6 ? p[6] : null;
            String key = p.length>5 ? (p[4] + "-" + p[5]) : null;
            loadDoctors(); loadAppointmentsIntoDoctors();
            Doctor doc = docId!=null?doctorMap.get(docId):null;
            if (doc == null) {
                JOptionPane.showMessageDialog(this,"Doctor info not available - choose new slot.");
                restoreMode = true; rescheduleInfoLabel.setText("Restoring archived appointment: choose new slot");
                selectedDoctorId = docId; selectedDate = LocalDate.now(); currentYearMonth = YearMonth.from(selectedDate);
                updateMonthLabel(); generateCalendar(); loadSlotsForDate(selectedDate); showRightPanel();
                return;
            }
            if (key == null || !doc.isAvailable(key)) {
                int ans = JOptionPane.showConfirmDialog(this, "Original slot is taken. Choose a new slot!", "Slot taken", JOptionPane.YES_NO_OPTION);
                if (ans != JOptionPane.YES_OPTION) { restoreEntry = null; return; }
                restoreMode = true; rescheduleInfoLabel.setText("Restoring archived appointment: choose new slot");
                selectedDoctorId = docId; selectedDate = LocalDate.now(); currentYearMonth = YearMonth.from(selectedDate);
                updateMonthLabel(); generateCalendar(); loadSlotsForDate(selectedDate); showRightPanel();
                return;
            }
            // immediate restore
            restoreEntry = ae;
            finalizeRestoreWithSelection(LocalDate.parse(p[4], dateFmt), p[5]);
        }
    }

    // Finalize restore after user selects new date/time
    private void finalizeRestoreWithSelection(LocalDate chosenDate, String chosenTime) {
        if (restoreEntry == null) { JOptionPane.showMessageDialog(this,"Restore context missing."); return; }
        String[] orig = restoreEntry.originalParts;
        String desiredId = orig.length>0 ? orig[0] : "";
        String assignedId = desiredId;
        try {
            Set<String> existing = new HashSet<>();
            File f = new File(APPT_FILE);
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String ln; while ((ln = br.readLine()) != null) { if (ln.trim().isEmpty()) continue; String[] p = ln.split("\\|", -1); if (p.length>0) existing.add(p[0]); }
                }
            }
            if (assignedId == null || assignedId.isBlank() || existing.contains(assignedId)) assignedId = generateUniqueApptId(existing);
        } catch (IOException ignored) {}

        String[] newParts = Arrays.copyOf(orig, Math.max(orig.length, 13));
        newParts[0] = assignedId;
        newParts[4] = chosenDate.toString();
        newParts[5] = chosenTime;
        newParts[10] = LocalDateTime.now().format(stampFmt);
        newParts[11] = currentStaffUsername;
        newParts[12] = "Upcoming";

        String newLine = String.join("|", newParts);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(APPT_FILE, true))) { bw.write(newLine + System.lineSeparator()); }
        catch (IOException ex) { JOptionPane.showMessageDialog(this,"Error restoring appointment: " + ex.getMessage()); return; }

        if (newParts.length >= 7) {
            Doctor d = doctorMap.get(newParts[6]); if (d!=null) d.bookSlot(newParts[4] + "-" + newParts[5]);
        }

        removeArchivedLine(restoreEntry.timestamp + "|" + restoreEntry.deletedBy + "|" + restoreEntry.originalLine);
        restoreMode = false; restoreEntry = null; hideRightPanel();
        loadAppointmentsIntoDoctors(); loadAppointmentsTable();
        JOptionPane.showMessageDialog(this, "Appointment restored (ID: " + newParts[0] + ")");
    }

    // ---------------- HELPERS ----------------
    // Check if a row in the JTable represents a completed appointment
    private boolean isRowCompleted(int row) { try { String s = String.valueOf(model.getValueAt(row,12)); return "Completed".equalsIgnoreCase(s); } catch (Exception e) { return false; } }

    // Generate a unique appointment ID not conflicting with existing ones
    private String generateUniqueApptId(Set<String> existing) {
        int max = 0;
        for (String id : existing) {
            if (id!=null && id.matches("A\\d+")) {
                try { int v = Integer.parseInt(id.substring(1)); if (v>max) max=v; } catch (NumberFormatException ignored) {}
            }
        }
        int next = max + 1;
        return String.format("A%05d", next);
    }

    // ---------------- TABLE RENDERERS ----------------
    // Custom renderer for Status column (Completed/Rescheduled/Upcoming) with color
    private class StatusRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
        StatusRenderer() { setOpaque(true); setHorizontalAlignment(SwingConstants.CENTER); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String s = value == null ? "" : value.toString();
            setText(s);
            switch (s.toLowerCase()) {
                case "completed": setBackground(new Color(0x4CAF50)); setForeground(Color.BLACK); break;
                case "rescheduled": setBackground(new Color(0xFFF59D)); setForeground(Color.BLACK); break;
                case "upcoming": default: setBackground(new Color(0xF44336)); setForeground(Color.WHITE); break;
            }
            if (isSelected) setBorder(BorderFactory.createLineBorder(Color.BLUE,2)); else setBorder(null);
            return this;
        }
    }

    // Custom renderer for action buttons (Reschedule / Delete) in table
    private class ActionButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        private final String label;
        ActionButtonRenderer(String label) { this.label = label; setOpaque(true); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setText(label);
            // If using sorting, row here is view row - but rendering only cares about appearance.
            int modelRow = table.convertRowIndexToModel(row);
            if ("Reschedule".equals(label)) setEnabled(!isRowCompleted(modelRow));
            else setEnabled(true);
            return this;
        }
    }

    // Remove a specific line from the archive file after successful restore
    private void removeArchivedLine(String fullArchivedLine) {
        File arch = new File(ARCHIVE_FILE);
        if (!arch.exists()) return;
        File tmp = new File(ARCHIVE_FILE + ".tmp");
        try (BufferedReader r = new BufferedReader(new FileReader(arch)); BufferedWriter w = new BufferedWriter(new FileWriter(tmp))) {
            String ln; while ((ln = r.readLine()) != null) { if (ln.equals(fullArchivedLine)) continue; w.write(ln + System.lineSeparator()); }
        } catch (IOException ex) { log("Error cleaning archive: " + ex.getMessage()); return; }
        if (!arch.delete()) { log("Could not delete original archive file."); return; }
        if (!tmp.renameTo(arch)) log("Could not rename archive temp file.");
    }
}
package assignment;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

// BookingApp main panel for managing doctor appointments
public final class BookingApp extends JPanel {
    Map<String, Doctor> doctorMap = new HashMap<>();
    JTextField searchField = new JTextField(20);
    JComboBox<String> specializationBox;
    JComboBox<String> shiftBox;
    JTextArea logArea = new JTextArea(5, 30);
    JPanel calendarPanel = new JPanel();
    JPanel slotPanel = new JPanel();
    JLabel selectedDoctorLabel = new JLabel("No doctor selected");
    Doctor selectedDoctor = null;
    LocalDate selectedDate = null;
    String currentStaffUsername;
    boolean autoAssignMode = false;
    
    private YearMonth currentYearMonth = YearMonth.now();
    private JLabel monthLabel, pic;

    // Constructor: initialize app with the current staff username
    public BookingApp(String currentStaffUsername) {
        this.currentStaffUsername = currentStaffUsername;
        loadDoctors();
        loadAppointments();
        setupUI();
    }
   
    // Inner class representing a Doctor
    static class Doctor {
        String id, specialization, shift, fullName;
        Set<String> bookedSlots = new HashSet<>();
        
        // Constructor for a doctor object
        Doctor(String id, String username, String firstName, String lastName, String specialization, String shift) {
            this.id = id;
            this.specialization = specialization;
            this.shift = shift;
            this.fullName = firstName + " " + lastName;
        }

        // Check if a specific date-time slot is available
        boolean isAvailable(String dateTime) {
            return !bookedSlots.contains(dateTime);
        }

         // Mark a date-time slot as booked
        void bookSlot(String dateTime) {
            bookedSlots.add(dateTime);
        }

        // Return a descriptive string with name, specialization, and shift
        String getDetails() {
            return fullName + " (" + specialization + ", " + shift + ")";
        }

        // Create a Doctor object from a line in doctors.txt
        static Doctor fromLine(String line) {
            String[] p = line.split("\\|");
            if (p.length < 15) return null;
            return new Doctor(p[0], p[1], p[3], p[4], p[13], p[14]);
        }
        
        // Get the file path of the doctor's profile image if exists
        String getImagePath() {
            File folder = new File("doctor_images");

            if (!folder.exists() || !folder.isDirectory()) {
                return null;
            }

            String[] extensions = {".jpg", ".png", ".jpeg"};
            for (String ext : extensions) {
                File f = new File(folder, id + ext);
                if (f.exists()) {
                    return f.getAbsolutePath();
                }
            }
            return null;
        }
    }

    // Inner class representing a Patient
    class Patient {
        String id, fname, lname;

        Patient(String id, String fname, String lname) {
            this.id = id;
            this.fname = fname;
            this.lname = lname;
        }

        String getFullName() {
            return fname + " " + lname;
        }

        @Override
        public String toString() {
            return getFullName() + " (" + id + ")";
        }
    }

    // Load all patients from patients.txt and return as a list
    List<Patient> loadPatients() {
        List<Patient> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("patients.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length >= 5) {
                    list.add(new Patient(p[0], p[3], p[4]));
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Missing patients.txt file.");
        }
        return list;
    }
    
    // Load all doctors from doctors.txt into doctorMap
    void loadDoctors() {
        try (BufferedReader br = new BufferedReader(new FileReader("doctors.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                Doctor d = Doctor.fromLine(line);
                if (d != null) doctorMap.put(d.id, d);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Missing doctors.txt file.");
        }
    }

    // Load all existing appointments and mark booked slots for each doctor
    void loadAppointments() {
        for (Doctor d : doctorMap.values()) {
            d.bookedSlots.clear();  // Clear any old cached data
        }

        try (BufferedReader br = new BufferedReader(new FileReader("appointments.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length >= 7) {
                    String docId = p[6];
                    Doctor d = doctorMap.get(docId);
                    if (d != null) {
                        String slotKey = p[4] + "-" + p[5];  // date + time
                        d.bookSlot(slotKey);
                    }
                }
            }
        } catch (IOException e) {}
    }
    
    // Initialize the GUI components: search panel, calendar, slots, log area
    void setupUI() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel doctorInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        doctorInfo.setBorder(new EmptyBorder(10, 10, 10, 10));

        pic = new JLabel();
        pic.setPreferredSize(new Dimension(100, 120));
        pic.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        doctorInfo.add(pic);
        doctorInfo.add(selectedDoctorLabel);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        specializationBox = new JComboBox<>(new String[]{"-- Select Specialization --", "Dermatology", "Dental", "Oncology", "Radiology", "Cardiology","Neurology", "Pediatrics", "Psychiatry", "General Surgery", "Orthopedics"});
        shiftBox = new JComboBox<>(new String[]{"-- Select Shift --", "Shift C", "Shift A", "Shift B"});
        
        searchPanel.add(new JLabel("Search Doctor:"));
        searchPanel.add(searchField);
        
        JPopupMenu doctorSuggestPopup = new JPopupMenu();
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                List<String> names = new ArrayList<>();
                for (Doctor d : doctorMap.values()) names.add(d.fullName);
                showSuggestions(searchField, doctorSuggestPopup, names);
            }
        });
        
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> {
            autoAssignMode = false; 
            searchDoctor();
        });
        searchPanel.add(searchBtn);

        JButton autoBtn = new JButton("Auto Assign");
        autoBtn.addActionListener(e -> {
            selectedDoctor = null;
            autoAssignMode = true; 
            selectedDoctorLabel.setText("Auto-assignment enabled");
            specializationBox.setEnabled(true);
            shiftBox.setEnabled(true);
        });
        searchPanel.add(autoBtn);
        
        searchPanel.add(new JLabel("Specialization:"));
        specializationBox = new JComboBox<>(new String[]{"-- Select Specialization --", "General Practice", "Dental", "Pediatrics", "Dermatology", "ENT"});
        searchPanel.add(specializationBox);

        searchPanel.add(new JLabel("Shift:"));
        shiftBox = new JComboBox<>(new String[]{"-- Select Shift --", "Shift C", "Shift A", "Shift B"});
        searchPanel.add(shiftBox);

        specializationBox.addActionListener(e -> {
            if (autoAssignMode && selectedDate != null) {
                loadSlotsForDate(selectedDate);
            }
        });

        shiftBox.addActionListener(e -> {
            if (autoAssignMode && selectedDate != null) {
                loadSlotsForDate(selectedDate);
            }
        });

        topPanel.add(doctorInfo, BorderLayout.WEST);
        topPanel.add(searchPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2));

        // Create wrapper to hold calendar header + calendar grid
        JPanel calendarWrapper = new JPanel(new BorderLayout());

        // Calendar header with navigation
        JPanel calendarHeader = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton prevMonthBtn = new JButton("<");
        JButton nextMonthBtn = new JButton(">"
                + "");

        monthLabel = new JLabel();  // Already declared as class field
        monthLabel.setFont(new Font("Arial", Font.BOLD, 16));
        updateMonthLabel(); // Set initial label

        prevMonthBtn.addActionListener(e -> {
            currentYearMonth = currentYearMonth.minusMonths(1);
            generateCalendar();
        });
        nextMonthBtn.addActionListener(e -> {
            currentYearMonth = currentYearMonth.plusMonths(1);
            generateCalendar();
        });

        calendarHeader.add(prevMonthBtn);
        calendarHeader.add(monthLabel);
        calendarHeader.add(nextMonthBtn);

        // Setup calendar grid
        calendarPanel.setLayout(new GridLayout(0, 7, 5, 5));  // 7 columns
        calendarPanel.setBorder(BorderFactory.createTitledBorder("Select a Date"));

        // Assemble calendar
        calendarWrapper.add(calendarHeader, BorderLayout.NORTH);
        calendarWrapper.add(calendarPanel, BorderLayout.CENTER);

        // Add full calendar section to center
        centerPanel.add(calendarWrapper);


        slotPanel.setLayout(new GridLayout(0, 3, 10, 10));
        slotPanel.setBorder(BorderFactory.createTitledBorder("Available Slots"));
        centerPanel.add(slotPanel);

        add(centerPanel, BorderLayout.CENTER);
    
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        generateCalendar();
    }

    // Search for a doctor by name and select them, updating UI and image
    void searchDoctor() {
        String input = searchField.getText().trim().toLowerCase();
        List<Doctor> matches = new ArrayList<>();

        for (Doctor d : doctorMap.values()) {
            if (d.fullName.toLowerCase().contains(input)) {
                matches.add(d);
            }
        }

        if (matches.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No matching doctor found.");
            return;
        }

        Doctor selected = null;

        if (matches.size() == 1) {
            selected = matches.get(0);
        } else {
            String[] options = matches.stream().map(d -> d.fullName).toArray(String[]::new);
            String chosen = (String) JOptionPane.showInputDialog(this, "Select Doctor", "Multiple Matches", JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (chosen != null) {
                for (Doctor d : matches) {
                    if (d.fullName.equals(chosen)) {
                        selected = d;
                        break;
                    }
                }
            }
        }

        if (selected != null) {
            selectedDoctor = selected;
            autoAssignMode = false;
            selectedDoctorLabel.setText("Selected: " + selectedDoctor.getDetails());
            autoAssignMode = false;

            String imagePath = selectedDoctor.getImagePath();
            pic.setIcon(loadProfileImage(imagePath, 100, 120));

            specializationBox.setSelectedItem(selectedDoctor.specialization);
            shiftBox.setSelectedItem(selectedDoctor.shift);

            specializationBox.setEnabled(false);
            shiftBox.setEnabled(false);
        }
    }

    // Generate calendar UI for the current month with navigation buttons
    void generateCalendar() {
        calendarPanel.removeAll();
        updateMonthLabel();

        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        DayOfWeek firstDayOfWeek = firstOfMonth.getDayOfWeek();
        int daysInMonth = currentYearMonth.lengthOfMonth();

        // Add weekday headers (Sun to Sat)
        String[] headers = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String header : headers) {
            JLabel dayLabel = new JLabel(header, SwingConstants.CENTER);
            dayLabel.setFont(new Font("Arial", Font.BOLD, 12));
            calendarPanel.add(dayLabel);
        }

        int dayOfWeekIndex = firstDayOfWeek.getValue() % 7; // Sunday=0
        for (int i = 0; i < dayOfWeekIndex; i++) {
            calendarPanel.add(new JLabel("")); // empty cells before 1st day
        }

        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(30);  // Limit to 30 days

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentYearMonth.atDay(day);
            JButton btn = new JButton(String.valueOf(day));
            btn.setMargin(new Insets(2, 4, 2, 4));
            btn.setFocusPainted(false);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 12));

            if (date.isBefore(today)) {
                btn.setEnabled(false);
                btn.setBackground(Color.LIGHT_GRAY);
            } else if (date.isAfter(maxDate)) {
                btn.setEnabled(false);
                btn.setBackground(new Color(230, 230, 230)); // light gray for future unselectable
            } else {
                btn.setBackground(new Color(204, 255, 204)); // light green = selectable
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                btn.addActionListener(e -> {
                    selectedDate = date;
                    loadSlotsForDate(date);
                });
            }

            calendarPanel.add(btn);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    // Update the month label to reflect currentYearMonth
    void updateMonthLabel() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy");
        monthLabel.setText(currentYearMonth.format(fmt));
    }

    // Generate and display available time slots for the selected date
    void loadSlotsForDate(LocalDate date) {
    slotPanel.removeAll();
    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    LocalDateTime now = LocalDateTime.now();
    
    LocalTime shiftStartTime;
    LocalTime shiftEndTime;
    String shift;

    if (selectedDoctor != null) {
        shift = selectedDoctor.shift;
    } else {
        shift = (String) shiftBox.getSelectedItem();
        if (shift == null || shift.trim().isEmpty() || shift.startsWith("--")) {
            JOptionPane.showMessageDialog(this, "Please select a shift first.");
            return;
        }
    }

    String shiftKey = shift.toLowerCase().trim();

    switch (shiftKey) {
        case "shift c" -> {
            shiftStartTime = LocalTime.MIDNIGHT;
            shiftEndTime = LocalTime.of(7, 30);
        }
        case "shift a" -> {
            shiftStartTime = LocalTime.of(8, 0);
            shiftEndTime = LocalTime.of(15, 30);
        }
        case "shift b" -> {
            shiftStartTime = LocalTime.of(16, 0);
            shiftEndTime = LocalTime.of(23, 30);
        }
        default -> {
            JOptionPane.showMessageDialog(this, "Invalid shift selected.");
            return;
        }
    }

    LocalDateTime current = LocalDateTime.of(date, shiftStartTime);
    LocalDateTime end = LocalDateTime.of(date, shiftEndTime);

    while (!current.isAfter(end)) {
        String timeSlot = current.toLocalTime().format(timeFmt);
        String slotKey = current.toLocalDate() + "-" + timeSlot;

        JButton slotBtn = new JButton(timeSlot);
        slotBtn.setEnabled(false);

        final LocalDate slotDate = current.toLocalDate();
        final String slotTime = timeSlot;

        boolean available = false;
        
        // Prevent booking past time slots for today
        if (current.toLocalDate().isEqual(now.toLocalDate()) &&
            current.toLocalTime().isBefore(now.toLocalTime())) {
            slotBtn.setBackground(Color.DARK_GRAY); // Past slot
            slotPanel.add(slotBtn);
            current = current.plusMinutes(30);
            continue;
        }
        
        if (selectedDoctor != null) {
            if (selectedDoctor.isAvailable(slotKey)) {
                slotBtn.setBackground(Color.GREEN); // Available
                slotBtn.setEnabled(true);
                slotBtn.addActionListener(e -> bookSlot(slotDate, slotTime, selectedDoctor));
                available = true;
            } else {
                slotBtn.setBackground(Color.RED); // Booked
            }
        } else if (autoAssignMode) {
            String specialization = (String) specializationBox.getSelectedItem();
            if (specialization == null || specialization.startsWith("--")) {
                JOptionPane.showMessageDialog(this, "Please select a specialization.");
                return;
            }

            Doctor auto = findAvailableDoctor(specialization, slotKey, shift);
            if (auto != null) {
                slotBtn.setBackground(Color.GREEN);
                slotBtn.setEnabled(true);
                slotBtn.addActionListener(e -> bookSlot(slotDate, slotTime, auto));
                available = true;
            } else {
                slotBtn.setBackground(Color.RED);
            }
        } else {
            // Not selected anything, just show gray
            slotBtn.setBackground(Color.LIGHT_GRAY);
        }

        if (!available && slotBtn.getBackground() != Color.RED) {
            slotBtn.setBackground(new Color(200, 200, 255)); // Blue-ish if no logic applies
        }

        slotPanel.add(slotBtn);
        current = current.plusMinutes(30);
    }

    slotPanel.revalidate();
    slotPanel.repaint();
}

    // Find an available doctor for auto-assignment given specialization, slot, and shift
    Doctor findAvailableDoctor(String specialization, String slotKey, String shift) {
        Doctor selected = null;
        int minCount = Integer.MAX_VALUE;
        Map<String, Integer> counts = getBookingCounts();

        for (Doctor d : doctorMap.values()) {
            if (d.specialization.equalsIgnoreCase(specialization) &&
                d.shift.equalsIgnoreCase(shift)) {

                if (d.isAvailable(slotKey)) {
                    int count = counts.getOrDefault(d.id, 0);
                    if (count < minCount) {
                        minCount = count;
                        selected = d;
                    }
                }
            }
        }
        return selected;
    }

    // Count the number of appointments per doctor from appointments.txt
    Map<String, Integer> getBookingCounts() {
        Map<String, Integer> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("appointments.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue; // Skip empty lines
                String[] p = line.split("\\|");
                if (p.length >= 7) { // Minimum fields needed for Doctor ID
                    String doctorId = p[6].trim();  // Doctor ID is index 6
                    map.put(doctorId, map.getOrDefault(doctorId, 0) + 1);
                }
            }
        } catch (IOException e) {}
        return map;
    }
    
    // Generate a unique appointment ID by checking existing appointments
    private String generateAppointmentId() {
        Random rnd = new Random();
        String id;
        Set<String> existingIds = new HashSet<>();

        // Load existing appointment IDs to avoid duplicates
        Path path = Paths.get("appointments.txt");
        if (Files.exists(path)) {
            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length > 0) existingIds.add(parts[0]);
                }
            } catch (IOException e) {
            }
        }

        // Generate a random ID and ensure it's unique
        do {
            int num = rnd.nextInt(90000) + 10000; // 10000–99999
            id = "A" + num;
        } while (existingIds.contains(id));

        return id;
    }

    // Book a slot for a patient with a doctor, update file, log, and UI
    void bookSlot(LocalDate date, String time, Doctor doc) {
         String typed = promptPatientName();
        if (typed == null) return; 
        typed = typed.trim();
        if (typed.isEmpty()) return;

        List<Patient> allPatients = loadPatients();
        List<Patient> matches = new ArrayList<>();
        String typedLower = typed.toLowerCase();

        for (Patient p : allPatients) {
            if (p.getFullName().toLowerCase().startsWith(typedLower) || p.getFullName().toLowerCase().contains(typedLower)) {
                matches.add(p);
            }
        }

        if (matches.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No matching patient found.");
            return;
        }

        Patient selectedPatient = matches.size() == 1 ? matches.get(0) :
            (Patient) JOptionPane.showInputDialog(this, "Select Patient", "Multiple Matches",
                JOptionPane.PLAIN_MESSAGE, null, matches.toArray(new Patient[0]), matches.get(0));

        if (selectedPatient == null) return;

        String appointmentId = generateAppointmentId();
        String createdDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String slotKey = date + "-" + time;
        
        if (!doc.isAvailable(slotKey)) {
            JOptionPane.showMessageDialog(this, "This slot has already been booked.");
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile("appointments.txt", "rw");
             FileChannel channel = raf.getChannel()) {

            FileLock lock = channel.lock(); 
            try {
                raf.seek(raf.length()); // Move to end
                raf.writeBytes(String.join("|",
                    appointmentId,
                    selectedPatient.id,
                    selectedPatient.fname,
                    selectedPatient.lname,
                    date.toString(),
                    time,
                    doc.id,
                    doc.fullName,
                    doc.specialization,
                    doc.shift,
                    createdDate,
                    currentStaffUsername,
                    "Upcoming",
                    ""
                ) + "\n");
            } finally {
                lock.release(); 
            }

            doc.bookSlot(slotKey); 
            logArea.append("Booked: " + selectedPatient.getFullName() + " with " +
                doc.getDetails() + " at " + date + " " + time + "\n");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving appointment.");
        }

        loadSlotsForDate(date); // refresh UI
    }
    
    // Prompt user to type/select a patient name with auto-suggestions
    private String promptPatientName() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Enter Patient Name", true);
        dlg.setLayout(new BorderLayout(8, 8));
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField tf = new JTextField(30);
        center.add(new JLabel("Patient name:"));
        center.add(tf);

        JPopupMenu popup = new JPopupMenu();
        List<Patient> all = loadPatients();
        List<String> names = new ArrayList<>();
        for (Patient p : all) names.add(p.getFullName().trim());

        tf.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                showSuggestions(tf, popup, names);
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        bottom.add(ok);
        bottom.add(cancel);

        final String[] result = new String[1];
        ok.addActionListener(ev -> {
            result[0] = tf.getText().trim();
            dlg.dispose();
        });
        cancel.addActionListener(ev -> {
            result[0] = null;
            dlg.dispose();
        });

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        return result[0];
    }
    
    // Show auto-complete suggestions in a popup for a given text field
    private void showSuggestions(JTextField field, JPopupMenu popup, Collection<String> data) {
        popup.removeAll();
        String txt = field.getText().trim().toLowerCase();
        if (txt.isEmpty()) {
            popup.setVisible(false);
            return;
        }
        int count = 0;
        for (String s : data) {
            if (s == null) continue;
            if (s.toLowerCase().startsWith(txt)) {  // predictive match from beginning
                JMenuItem item = new JMenuItem(s);
                item.addActionListener(e -> {
                    field.setText(s);
                    popup.setVisible(false);
                });
                popup.add(item);
                if (++count >= 10) break; // max 10 suggestions
            }
        }
        if (popup.getComponentCount() > 0) {
            popup.show(field, 0, field.getHeight());
        } else {
            popup.setVisible(false);
        }
    }
    
    // Load a doctor's profile image, resizing and centering it; return placeholder if missing
    private ImageIcon loadProfileImage(String path, int width, int height) {
        BufferedImage placeholder = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = placeholder.createGraphics();
        pg.setColor(Color.LIGHT_GRAY);
        pg.fillRect(0, 0, width, height);
        pg.setColor(Color.DARK_GRAY);
        FontMetrics fm = pg.getFontMetrics();
        String msg = "No Photo";
        int tx = (width - fm.stringWidth(msg)) / 2;
        int ty = (height - fm.getHeight()) / 2 + fm.getAscent();
        pg.drawString(msg, tx, ty);
        pg.dispose();

        if (path == null) {
            return new ImageIcon(placeholder);
        }

        try {
            BufferedImage img = ImageIO.read(new File(path));
            if (img == null) {
                return new ImageIcon(placeholder);
            }

            // compute scale preserving aspect ratio
            double scale = Math.min((double) width / img.getWidth(), (double) height / img.getHeight());
            int nw = (int) Math.round(img.getWidth() * scale);
            int nh = (int) Math.round(img.getHeight() * scale);

            // draw onto a fixed canvas so the image is centered
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            // background (optional)
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(0, 0, width, height);

            int x = (width - nw) / 2;
            int y = (height - nh) / 2;
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                               java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, x, y, nw, nh, null);
            g.dispose();

            return new ImageIcon(canvas);
        } catch (IOException ex) {
            return new ImageIcon(placeholder);
        }
    }
}

package assignment;

//import all packages
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// Defines a panel for displaying and managing appointment details, including treatments, medicines, and feedback.
public class AppointmentDetailsPanel extends JPanel {
    
    // Stores the appointment details passed to this panel
    private String[] appt;
    
    // Identifiers for the doctor associated with this appointment
    private final String doctorId;
    private final String doctorUsername;
    
    // Callbacks for when the appointment is saved or the panel is closed
    private final Runnable onSaved;
    private final Runnable onClose;
    
    // UI components for treatments, medicines, feedback, and controls
    private JTextField treatField;
    private JButton addTreatmentBtn;
    private JButton addMedBtn;
    private JButton btnSaveClose;
    private JButton btnClose;
    private JLabel lockLabel;
    private JPanel selectedTreatmentPanel;
    private JLabel totalLabel;
    private JTextArea feedbackArea;
    private JPanel prescriptionListPanel;
    
    // Data structures for managing treatments and medicines
    private final List<MedicineRow> newMedicineRows = new ArrayList<>();
    private final Map<String, Treatment> treatmentsByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final List<Treatment> existingTreatments = new ArrayList<>();
    private final List<Treatment> newTreatments = new ArrayList<>();
    
    // Formatting utilities for timestamps and currency
    private final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final NumberFormat currencyFmt = NumberFormat.getCurrencyInstance(new Locale("ms", "MY"));
    
    // Filter for protecting part of the feedback from edits
    private final PrefixProtectedFilter feedbackFilter = new PrefixProtectedFilter();
    private int protectedFeedbackLen = 0;
    
    // File paths for storing appointments, treatments, and medicines
    private static final String APPT_RECORDS_FILE = "appointments_records.txt";
    private static final String TREATMENTS_FILE = "treatments.txt";
    private static final String MEDICINES_FILE = "medicines.txt";
    
    // Constructor: sets up the panel with appointment data, doctor info, and callbacks; loads treatments and medicines; restores existing records; and locks the panel if the appointment is completed.
    public AppointmentDetailsPanel(String[] appointmentData, String doctorId, String doctorUsername, Runnable onSaved, Runnable onClose) {
        this.appt = appointmentData;
        this.doctorId = doctorId;
        this.doctorUsername = doctorUsername;
        this.onSaved = onSaved;
        this.onClose = onClose;

        loadTreatments();
        initUI();
        loadMedicinesCatalog();
        restoreRecordOnOpen();
        
        String status = nz(appt, 12);  // assuming 13th field in appointments.txt = status
        if ("Completed".equalsIgnoreCase(status)) {
            applyLockState();
        }
    }
    

    // Initializes the user interface: sets up patient info, treatment selection, feedback area, medicine prescriptions, and action buttons with layout and event handlers.
    private void initUI() {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextArea patientInfo = new JTextArea(4, 80);
        patientInfo.setText(getPatientDetails(nz(appt, 1)));
        patientInfo.setEditable(false);
        patientInfo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        patientInfo.setBorder(new TitledBorder("Patient Information"));
        main.add(patientInfo);

        lockLabel = new JLabel("");
        lockLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lockLabel.setForeground(new Color(180, 0, 0));
        lockLabel.setVisible(false);
        main.add(lockLabel);

        JPanel treatPanel = new JPanel(new BorderLayout(8, 8));
        treatPanel.setBorder(new TitledBorder("Select Treatments"));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        treatField = new JTextField(28);
        JPopupMenu suggestions = new JPopupMenu();
        treatField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                showSuggestions(treatField, suggestions, treatmentsByName.keySet());
            }
        });

        addTreatmentBtn = new JButton("Add Treatment");
        addTreatmentBtn.addActionListener(e -> addTreatmentByName(treatField.getText().trim()));

        searchRow.add(new JLabel("Treatment:"));
        searchRow.add(treatField);
        searchRow.add(addTreatmentBtn);
        treatPanel.add(searchRow, BorderLayout.NORTH);

        selectedTreatmentPanel = new JPanel();
        selectedTreatmentPanel.setLayout(new BoxLayout(selectedTreatmentPanel, BoxLayout.Y_AXIS));
        treatPanel.add(new JScrollPane(selectedTreatmentPanel), BorderLayout.CENTER);

        totalLabel = new JLabel("Total: RM 0.00");
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        treatPanel.add(totalLabel, BorderLayout.SOUTH);
        main.add(treatPanel);

        feedbackArea = new JTextArea(8, 80);
        feedbackArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        feedbackArea.setLineWrap(true);
        feedbackArea.setWrapStyleWord(true);
        feedbackArea.setBorder(new TitledBorder("Feedback: "));
        main.add(new JScrollPane(feedbackArea));

        JPanel medWrapper = new JPanel();
        medWrapper.setLayout(new BoxLayout(medWrapper, BoxLayout.Y_AXIS));
        prescriptionListPanel = new JPanel();
        prescriptionListPanel.setLayout(new BoxLayout(prescriptionListPanel, BoxLayout.Y_AXIS));
        prescriptionListPanel.setBorder(new TitledBorder("Prescribed Medicines"));

        addMedBtn = new JButton("Add Medicine");
        addMedBtn.addActionListener(e -> addMedicineRow(null, true));
        medWrapper.add(addMedBtn);
        medWrapper.add(prescriptionListPanel);
        main.add(medWrapper);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnSaveClose = new JButton("Save & Close");
        btnClose = new JButton("Close");
        stylePrimary(btnSaveClose);
        styleDanger(btnClose);

        btnSaveClose.addActionListener(e -> {
            doSaveAndClose();  
            if (onSaved != null) onSaved.run();  
        });

        btnClose.addActionListener(e -> {
            if (onClose != null) onClose.run();  
        });


        bottom.add(btnSaveClose);
        bottom.add(btnClose);
        main.add(bottom);

        JScrollPane sp = new JScrollPane(main);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        this.setLayout(new BorderLayout());
        this.add(sp, BorderLayout.CENTER);
    }

    // Utility methods to apply consistent styling to buttons: stylePrimary for main actions and styleDanger for destructive actions.
    private void stylePrimary(AbstractButton b) {
        b.setBackground(new Color(33, 150, 243));
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
    private void styleDanger(AbstractButton b) {
        b.setBackground(new Color(244, 67, 54));
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
    
    // Loads treatment data from file and populates the treatmentsByName map with treatment names and prices.
    private void loadTreatments() {
        Path p = Paths.get(TREATMENTS_FILE);
        if (!Files.exists(p)) return;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(Pattern.quote("|"), -1);
                if (parts.length >= 3) {
                    String name = parts[1];
                    double price = parseDoubleSafe(nz(parts, 2));
                    treatmentsByName.put(name, new Treatment(name, price));
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to load treatments: " + e.getMessage());
        }
    }
    
    // Adds a treatment by name: validates input, checks for duplicates, adds it to new treatments, updates UI, and recalculates total cost.
    private void addTreatmentByName(String q) {
        if (q == null || q.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a treatment name.");
            return;
        }
        Treatment found = treatmentsByName.get(q);
        if (found == null)
            for (String n : treatmentsByName.keySet())
                if (n.toLowerCase().startsWith(q.toLowerCase())) {
                    found = treatmentsByName.get(n);
                    break;
                }
        if (found == null) {
            JOptionPane.showMessageDialog(this, "Treatment not found.");
            return;
        }
        for (Treatment t : existingTreatments)
            if (t.name.equalsIgnoreCase(found.name)) {
                JOptionPane.showMessageDialog(this, "Treatment already exists (saved)");
                return;
            }
        for (Treatment t : newTreatments)
            if (t.name.equalsIgnoreCase(found.name)) {
                JOptionPane.showMessageDialog(this, "Treatment already added");
                return;
            }
        newTreatments.add(found);
        addTreatmentChip(found, true);
        recalcTotal();
    }

    // Creates a visual “chip” for a treatment in the UI, optionally allowing it to be removed and updating the total cost accordingly.
    private void addTreatmentChip(Treatment t, boolean removable) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        chip.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        JLabel lbl = new JLabel(t.name + " - " + formatCurrency(t.price) + (removable ? "" : "  (saved)"));
        JButton rm = new JButton("Remove");
        rm.addActionListener(e -> {
            if (removable) {
                newTreatments.remove(t);
                selectedTreatmentPanel.remove(chip);
                selectedTreatmentPanel.revalidate();
                selectedTreatmentPanel.repaint();
                recalcTotal();
            }
        });
        rm.setEnabled(removable);

        chip.add(lbl);
        chip.add(rm);
        selectedTreatmentPanel.add(chip);
        selectedTreatmentPanel.revalidate();
        selectedTreatmentPanel.repaint();
    }
    
    // Recalculates and updates the total cost of all existing and newly added treatments in the UI.
    private void recalcTotal() {
        double total = 0;
        for (Treatment t : existingTreatments) total += t.price;
        for (Treatment t : newTreatments) total += t.price;
        totalLabel.setText("Total: " + formatCurrency(total));
    }
    
    // Loads the list of available medicines from file into the medicinesCatalog list.
    private final List<String> medicinesCatalog = new ArrayList<>();
    private void loadMedicinesCatalog() {
        Path p = Paths.get(MEDICINES_FILE);
        if (!Files.exists(p)) return;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(Pattern.quote("|"), -1);
                if (parts.length >= 2) medicinesCatalog.add(parts[1]);
            }
        } catch (IOException e) {}
    }

    // Adds a row for prescribing a medicine with fields for name, frequency, and meal timing; supports optional preset values and removal for editable rows.
    private void addMedicineRow(DecodedMedicine preset, boolean editable) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JTextField medField = new JTextField(15);
        if (preset != null && preset.name != null) medField.setText(preset.name);

        JPopupMenu sugg = new JPopupMenu();
        medField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                showSuggestions(medField, sugg, medicinesCatalog);
            }
        });

        JComboBox<String> freqBox = new JComboBox<>(new String[]{"Select Frequency", "Once", "Twice", "Thrice"});
        JComboBox<String> mealBox = new JComboBox<>(new String[]{"Select Meal", "Before Meal", "After Meal", "Any"});

        if (preset != null) {
            if (!nz(preset.frequency).isEmpty()) freqBox.setSelectedItem(preset.frequency);
            if (!nz(preset.meal).isEmpty()) mealBox.setSelectedItem(preset.meal);
        }

        JButton rm = new JButton("Remove");
        rm.addActionListener(e -> {
            newMedicineRows.removeIf(mr -> mr.rowPanel == row);
            prescriptionListPanel.remove(row);
            prescriptionListPanel.revalidate();
            prescriptionListPanel.repaint();
        });

        row.add(new JLabel("Medicine:"));
        row.add(medField);
        row.add(new JLabel("Frequency:"));
        row.add(freqBox);
        row.add(new JLabel("Meal:"));
        row.add(mealBox);
        if (editable) row.add(rm);

        prescriptionListPanel.add(row);

        if (editable) {
            newMedicineRows.add(new MedicineRow(medField, freqBox, mealBox, row, rm));
        } else {
            medField.setEditable(false);
            freqBox.setEnabled(false);
            mealBox.setEnabled(false);
        }

        prescriptionListPanel.revalidate();
        prescriptionListPanel.repaint();
    }
    
    // Displays a popup with autocomplete suggestions for a text field based on the provided data collection.
    private void showSuggestions(JTextField field, JPopupMenu popup, Collection<String> data) {
        popup.removeAll();
        String txt = field.getText().trim().toLowerCase();
        if (txt.isEmpty()) {
            popup.setVisible(false);
            return;
        }
        int count = 0;
        for (String s : data) {
            if (s.toLowerCase().startsWith(txt)) {  
                JMenuItem item = new JMenuItem(s);
                item.addActionListener(e -> {
                    field.setText(s);
                    popup.setVisible(false);
                });
                popup.add(item);
                if (++count >= 10) break; 
            }
        }
        if (popup.getComponentCount() > 0)
            popup.show(field, 0, field.getHeight());
        else
            popup.setVisible(false);
    }
    
    // Saves the current appointment data, updates status and timestamp, writes to file, and triggers callbacks.
    private void doSaveAndClose() {
        if (appt == null) return;
        upsertAppointmentRecordNow();

        // Ensure appointment array has enough slots for status + timestamp
        if (appt.length < 14) {
            appt = Arrays.copyOf(appt, 14);
        }

        // Set status and timestamp
        appt[12] = "Completed";
        appt[13] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Update file
        Path p = Paths.get("appointments.txt");
        if (Files.exists(p)) {
            try {
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String[] parts = lines.get(i).split("\\|", -1);
                    if (parts.length > 0 && parts[0].equals(appt[0])) {
                        // Replace this line with the updated appointment data
                        lines.set(i, String.join("|", appt));
                        break;
                    }
                }
                Files.write(p, lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to update appointment status: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }

        applyLockState();

        if (onSaved != null) onSaved.run();
        if (onClose != null) onClose.run();
    }
    
    // Inserts or updates an appointment record with treatments, feedback, and medicines.
    private AppointmentRecord upsertAppointmentRecordNow() {
        List<String> lines = readAllLines(APPT_RECORDS_FILE);
        String apptId = nz(appt, 0);
        int idx = -1;
        AppointmentRecord existing = null;

        for (int i = 0; i < lines.size(); i++) {
            AppointmentRecord r = AppointmentRecord.parse(lines.get(i));
            if (r != null && apptId.equals(r.appointmentId)) {
                idx = i;
                existing = r;
                break;
            }
        }

        AppointmentRecord rec = (existing != null) ? existing : new AppointmentRecord();
        if (existing == null) {
            rec.appointmentId = apptId;
            rec.patientId = nz(appt, 1);
            rec.doctorId = doctorId;
            rec.doctorUsername = doctorUsername;
            rec.date = nz(appt, 4);
            rec.time = nz(appt, 5);
        }

        boolean changed = false;

        // Treatments
        if (!newTreatments.isEmpty()) {
            String encNew = encodeSelectedTreatments(newTreatments);
            rec.treatmentsList = isEmpty(rec.treatmentsList) ? encNew : (rec.treatmentsList + ";" + encNew);
            double base = parseDoubleSafe(rec.treatmentTotal);
            double add = newTreatments.stream().mapToDouble(t -> t.price).sum();
            rec.treatmentTotal = String.format("%.2f", base + add);
            changed = true;
        }

        // Feedback
        String full = feedbackArea.getText();
        int prefixLen = Math.min(protectedFeedbackLen, full.length());
        String appended = full.substring(prefixLen);
        appended = appended.replace("\r", "");
        if (!appended.isEmpty()) {
            rec.feedbackText = isEmpty(rec.feedbackText) ? appended : (rec.feedbackText + "\n\n" + appended);
            changed = true;
        }

        // Medicines
        if (!newMedicineRows.isEmpty()) {
            String enc = encodeMedicinesFromRows(newMedicineRows);
            rec.medicines = isEmpty(rec.medicines) ? enc : (rec.medicines + ";" + enc);
            changed = true;
        }

        if (!changed) return null;

        rec.lastUpdated = LocalDateTime.now().format(TS);

        String out = rec.toLine();
        if (idx >= 0) lines.set(idx, out);
        else lines.add(out);

        boolean wrote = writeAllLinesRobust(APPT_RECORDS_FILE, lines);
        if (!wrote) return null;
        return rec;
    }
    
    // Encodes a list of selected treatments into a semicolon-separated string for storage.
    private String encodeSelectedTreatments(List<Treatment> treats) {
        if (treats == null || treats.isEmpty()) return "";
        List<String> enc = new ArrayList<>();
        for (Treatment t : treats) {
            String name = escapeInnerForComposite(t.name);
            enc.add(name);
        }
        return String.join(";", enc);
    }
    
    // Encodes prescribed medicines from UI rows into a semicolon-separated string for storage.
    private String encodeMedicinesFromRows(List<MedicineRow> rows) {
        if (rows == null || rows.isEmpty()) return "";
        List<String> encoded = new ArrayList<>();
        for (MedicineRow r : rows) {
            String name = escapeInnerForComposite(r.medicineField.getText());
            if (name.isEmpty()) continue;
            String freq = escapeInnerForComposite(selectedOrEmpty(r.frequencyBox));
            String meal = escapeInnerForComposite(selectedOrEmpty(r.mealBox));
            encoded.add(name + "~" + freq + "~" + meal);
        }
        return String.join(";", encoded);
    }
    
    // Returns the selected item from a combo box, or empty string if default.
    private String selectedOrEmpty(JComboBox<String> box) {
        Object o = box.getSelectedItem();
        String s = o == null ? "" : o.toString();
        return s.startsWith("Select") ? "" : s;
    }

    // Restores existing appointment record on panel open: treatments, feedback, and medicines.
    private void restoreRecordOnOpen() {
        String apptId = nz(appt, 0);
        List<String> lines = readAllLines(APPT_RECORDS_FILE);

        for (String ln : lines) {
            AppointmentRecord r = AppointmentRecord.parse(ln);
            if (r != null && apptId.equals(r.appointmentId)) {
                r.feedbackText = unescapeFeedbackFromStorage(r.feedbackText);

                existingTreatments.clear();
                if (!isEmpty(r.treatmentsList)) {
                    String[] names = r.treatmentsList.split(";", -1);
                    for (String n : names) {
                        Treatment t = treatmentsByName.get(n.trim());
                        if (t != null) {
                            existingTreatments.add(t);
                            addTreatmentChip(t, false);
                        }
                    }
                    recalcTotal();
                }

                feedbackArea.setText(r.feedbackText);
                protectedFeedbackLen = feedbackArea.getText().length();
                feedbackFilter.setProtectedLen(protectedFeedbackLen);
                ((AbstractDocument) feedbackArea.getDocument()).setDocumentFilter(feedbackFilter);
                feedbackArea.setCaretPosition(protectedFeedbackLen);

                List<DecodedMedicine> meds = decodeMedicines(r.medicines);
                for (DecodedMedicine dm : meds) addMedicineRow(dm, false);

                return;
            }
        }

        feedbackArea.setText("");
        protectedFeedbackLen = 0;
        feedbackFilter.setProtectedLen(0);
        ((AbstractDocument) feedbackArea.getDocument()).setDocumentFilter(feedbackFilter);
    }

    // Locks the UI to prevent editing when appointment is completed.
    private void applyLockState() {
        // Disable text fields
        treatField.setEditable(false);
        feedbackArea.setEditable(false);

        // Disable buttons
        addTreatmentBtn.setEnabled(false);
        addMedBtn.setEnabled(false);
        btnSaveClose.setEnabled(false);

        // Lock treatment removal
        for (Component comp : selectedTreatmentPanel.getComponents()) {
            if (comp instanceof JPanel jPanel) {
                for (Component inner : jPanel.getComponents()) {
                    if (inner instanceof JButton) {
                        inner.setEnabled(false);
                    }
                }
            }
        }
    }
    
    
    // Decodes an encoded medicine string into a list of DecodedMedicine objects.
    private List<DecodedMedicine> decodeMedicines(String encoded) {
        List<DecodedMedicine> out = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return out;
        String[] items = encoded.split(";", -1);
        for (String it : items) {
            if (it == null || it.isEmpty()) continue;
            String[] p = it.split("~", -1);
            DecodedMedicine dm = new DecodedMedicine();
            dm.name = unescapeInnerForComposite(get(p, 0));
            dm.frequency = unescapeInnerForComposite(get(p, 1));
            dm.meal = unescapeInnerForComposite(get(p, 2));
            out.add(dm);
        }
        return out;
    }

    // Formats a double as currency string in RM format.
    private String formatCurrency(double d) {
        try {
            return currencyFmt.format(d).replace("MYR", "RM").trim();
        } catch (Exception e) {
            return "RM " + String.format("%.2f", d);
        }
    }
    
    // Safely parses a string into double, returning 0 on failure.
    private double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    // Checks if a string is null or empty.
    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    // Escapes reserved characters in strings for storage in composite fields.
    private String escapeInnerForComposite(String s) {
        if (s == null) return "";
        String x = s.replace("\r", "");
        x = x.replace("~", "<TILDE>");
        x = x.replace(";", "<SCOLON>");
        x = x.replace("|", "<PIPE>");
        x = x.replace("\n", "<NL>");
        return x;
    }

    // Unescapes stored composite field strings back to normal.
    private String unescapeInnerForComposite(String s) {
        if (s == null) return "";
        String x = s.replace("<NL>", "\n");
        x = x.replace("<TILDE>", "~");
        x = x.replace("<SCOLON>", ";");
        x = x.replace("<PIPE>", "|");
        return x;
    }
    
    // Unescapes feedback text from storage format.
    private String unescapeFeedbackFromStorage(String s) {
        if (s == null) return "";
        String x = s.replace("<NL>", "\n");
        x = x.replace("<PIPE>", "|");
        return x;
    }
    
    // -------------------- UI Helpers --------------------
    // Protects the first N characters of feedback from being edited.
    private class PrefixProtectedFilter extends DocumentFilter {
        private int protectedLen = 0;

        void setProtectedLen(int len) {
            protectedLen = Math.max(0, len);
        }

        @Override
        public void insertString(DocumentFilter.FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (offset < protectedLen) offset = protectedLen;
            fb.insertString(offset, string, attr);
        }

        @Override
        public void remove(DocumentFilter.FilterBypass fb, int offset, int length)
                throws BadLocationException {
            if (offset < protectedLen) {
                int end = offset + length;
                if (end <= protectedLen) return;
                int newOffset = protectedLen;
                int newLen = end - protectedLen;
                fb.remove(newOffset, newLen);
            } else fb.remove(offset, length);
        }

        @Override
        public void replace(DocumentFilter.FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            int start = offset;
            int end = offset + length;
            if (start < protectedLen) {
                if (end <= protectedLen) {
                    start = protectedLen;
                    length = 0;
                } else {
                    int newLen = end - protectedLen;
                    start = protectedLen;
                    length = newLen;
                }
            }
            fb.replace(start, length, text, attrs);
        }
    }

    // -------------------- Data Classes --------------------
    // Represents a treatment with name and price.
    static class Treatment {
        final String name;
        final double price;

        Treatment(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }
    
    // Represents a UI row for a prescribed medicine.
    static class MedicineRow {
        final JTextField medicineField;
        final JComboBox<String> frequencyBox;
        final JComboBox<String> mealBox;
        final JPanel rowPanel;

        MedicineRow(JTextField m, JComboBox<String> f, JComboBox<String> meal, JPanel panel, JButton remove) {
            this.medicineField = m;
            this.frequencyBox = f;
            this.mealBox = meal;
            this.rowPanel = panel;
        }
    }

    // Holds decoded medicine details.
    static class DecodedMedicine {
        String name = "", frequency = "", meal = "";
    }

    // Represents a saved appointment record with parsing and serialization methods.
    static class AppointmentRecord {
        String appointmentId = "", patientId = "", doctorId = "", doctorUsername = "",
                date = "", time = "", treatmentTotal = "", treatmentsList = "",
                feedbackText = "", medicines = "", lastUpdated = "";

        static AppointmentRecord parse(String line) {
            if (line == null) return null;
            String[] p = line.split(Pattern.quote("|"), -1);
            int N = p.length;
            if (N < 1) return null;
            AppointmentRecord r = new AppointmentRecord();

            r.appointmentId = (N >= 1) ? p[0] : "";
            r.patientId = (N >= 2) ? p[1] : "";
            r.doctorId = (N >= 3) ? p[2] : "";
            r.doctorUsername = (N >= 4) ? p[3] : "";
            r.date = (N >= 5) ? p[4] : "";
            r.time = (N >= 6) ? p[5] : "";
            r.treatmentTotal = (N >= 7) ? p[6] : "";
            r.treatmentsList = (N >= 8) ? p[7] : "";
            r.feedbackText = (N >= 9) ? unescapeFeedbackFromStorageStatic(p[8]) : "";
            r.medicines = (N >= 10) ? p[9] : "";
            r.lastUpdated = (N >= 11) ? p[10] : "";

            return r;
        }

        String toLine() {
            return String.join("|",
                    nz(appointmentId),
                    nz(patientId),
                    nz(doctorId),
                    nz(doctorUsername),
                    nz(date),
                    nz(time),
                    nz(treatmentTotal),
                    nz(treatmentsList),
                    escapeFeedbackForStorageStatic(nz(feedbackText)),
                    nz(medicines),
                    nz(lastUpdated)
            );
        }

        private static String escapeFeedbackForStorageStatic(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\n", "\\n")
                    .replace("|", "<PIPE>");
        }

        private static String unescapeFeedbackFromStorageStatic(String s) {
            if (s == null) return "";
            return s.replace("<PIPE>", "|")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\");
        }
    }
    
    // -------------------- File & Array Helpers --------------------
    // Reads all lines from a file, returns empty list if not found.
    private static List<String> readAllLines(String path) {
        Path p = Paths.get(path);
        File f = p.toFile();

        if (!f.exists()) {
            return new ArrayList<>();
        }

        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
    
    // Writes all lines to a file robustly, using temp files to prevent corruption.
    private static boolean writeAllLinesRobust(String path, List<String> lines) {
        Path target = Paths.get(path);
        Path tmp = Paths.get(path + ".tmp");
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException amnse) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            try {
                Files.write(target, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    // Safely returns element from array or empty string.
    private static String nz(String[] a, int i) {
        return (a != null && i >= 0 && i < a.length && a[i] != null) ? a[i] : "";
    }

    // Returns non-null string or empty string.
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // Gets element from array or empty string (variant).
    private static String get(String[] a, int i) {
        return (a != null && i >= 0 && i < a.length) ? a[i] : "";
    }

    // -------------------- Patient Details --------------------
    // Retrieves formatted patient details from patients.txt by ID.
    private String getPatientDetails(String patientID) {
        if (patientID == null || patientID.isEmpty()) return "Patient details not found.";
        Path p = Paths.get("patients.txt");
        if (!Files.exists(p)) return "Patient details not found.";

        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(Pattern.quote("|"), -1);
                if (parts.length >= 13 && Objects.equals(parts[0], patientID)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Patient Name: ").append(nz(parts, 3)).append(" ").append(nz(parts, 4)).append("\n")
                            .append("Gender: ").append(nz(parts, 5)).append("\n")
                            .append("DOB: ").append(nz(parts, 6)).append(" (Age: ").append(nz(parts, 7)).append(")\n")
                            .append("Contact: ").append(nz(parts, 9)).append("\n")
                            .append("Email: ").append(nz(parts, 8));
                    return sb.toString();
                }
            }
        } catch (IOException e) {
        }
        return "Patient details not found.";
    }
}

package assignment;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReportGenerator extends JPanel {

    private static final String APPOINTMENTS_FILE = "appointments.txt";
    private static final String RECORDS_FILE = "appointments_records.txt";
    private static final String PAYMENTS_FILE = "payments.txt";

    private final JComboBox<String> reportTypeBox;
    private final DefaultTableModel reportModel;
    private final JTable reportTable;
    private final DecimalFormat moneyFmt = new DecimalFormat("#0.00");

    public ReportGenerator() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Comprehensive Reports");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        add(title, BorderLayout.NORTH);

        // Top: report chooser + generate + optional export buttons
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        reportTypeBox = new JComboBox<>(new String[]{
                // Appointments
                "Appointments - Total by Day",
                "Appointments - Status Breakdown",
                "Appointments - By Doctor",
                "Appointments - By Specialization",
                "Appointments - By Shift",
                // Treatments
                "Treatment - Average Bill",
                "Treatment - Top 5 Treatments (by frequency)",
                "Treatment - Top 5 Treatments (by revenue)",
                "Treatment - Common Diagnoses",
                "Treatment - Prescription Trends",
                // Finance
                "Finance - Total Income (collected)",
                "Finance - Expected vs Actual",
                "Finance - By Doctor",
                "Finance - By Specialization",
                "Finance - By Payment Method",
                // Cross
                "Cross - Unpaid Appointments (expected > 0, no payment)",
                "Cross - Revenue Summary by Doctor (expected & collected)",
                "Cross - Revenue Summary by Specialization (expected & collected)",
                "Cross - Top Paying Patients"
        });
        JButton generateBtn = new JButton("Generate");
        JButton refreshBtn = new JButton("Refresh Data");

        top.add(new JLabel("Report:"));
        top.add(reportTypeBox);
        top.add(generateBtn);
        top.add(refreshBtn);

        add(top, BorderLayout.PAGE_START);

        // Table center
        reportModel = new DefaultTableModel();
        reportTable = new JTable(reportModel);
        reportTable.setRowHeight(26);
        add(new JScrollPane(reportTable), BorderLayout.CENTER);

        // Button actions
        generateBtn.addActionListener(e -> generateReport());
        refreshBtn.addActionListener(e -> {
            // simply regenerate same report to refresh caches
            generateReport();
        });
    }

    // ---------- Main dispatcher ----------
    private void generateReport() {
        String type = (String) reportTypeBox.getSelectedItem();
        if (type == null) return;

        // Load files once and pass to reporters
        List<String[]> appointments = loadFile(APPOINTMENTS_FILE);
        List<String[]> records = loadFile(RECORDS_FILE);
        List<String[]> payments = loadFile(PAYMENTS_FILE);

        switch (type) {
            // Appointments
            case "Appointments - Total by Day" -> appointmentsByDay(appointments);
            case "Appointments - Status Breakdown" -> appointmentsStatus(appointments);
            case "Appointments - By Doctor" -> appointmentsByDoctor(appointments);
            case "Appointments - By Specialization" -> appointmentsBySpecialization(appointments);
            case "Appointments - By Shift" -> appointmentsByShift(appointments);

            // Treatments
            case "Treatment - Average Bill" -> treatmentAverageBill(records);
            case "Treatment - Top 5 Treatments (by frequency)" -> treatmentTopNTreatments(records, 5, false);
            case "Treatment - Top 5 Treatments (by revenue)" -> treatmentTopNTreatments(records, 5, true);
            case "Treatment - Common Diagnoses" -> treatmentCommonDiagnoses(records);
            case "Treatment - Prescription Trends" -> treatmentPrescriptions(records);

            // Finance
            case "Finance - Total Income (collected)" -> financeTotalIncome(payments);
            case "Finance - Expected vs Actual" -> financeExpectedVsActual(records, payments);
            case "Finance - By Doctor" -> financeByDoctor(records, payments, appointments);
            case "Finance - By Specialization" -> financeBySpecialization(records, payments, appointments);
            case "Finance - By Payment Method" -> financeByPaymentMethod(payments);

            // Cross
            case "Cross - Unpaid Appointments (expected > 0, no payment)" -> crossUnpaidAppointments(records, payments, appointments);
            case "Cross - Revenue Summary by Doctor (expected & collected)" -> crossRevenueByDoctor(records, payments, appointments);
            case "Cross - Revenue Summary by Specialization (expected & collected)" -> crossRevenueBySpecialization(records, payments, appointments);
            case "Cross - Top Paying Patients" -> crossTopPatients(payments, 10);

            default -> {
                reportModel.setRowCount(0);
                reportModel.setColumnCount(0);
            }
        }
    }

    // ==================== APPOINTMENTS REPORTS ====================
    // Count total appointments grouped by date
    private void appointmentsByDay(List<String[]> appointments) {
        Map<String, Long> counts = appointments.stream()
                .filter(a -> a.length > 4 && !empty(a[4]))
                .collect(Collectors.groupingBy(a -> a[4], Collectors.counting()));

        reportModel.setColumnIdentifiers(new String[]{"Date", "Appointments"});
        reportModel.setRowCount(0);

        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> reportModel.addRow(new Object[]{e.getKey(), e.getValue()}));
    }
    
    // Count appointments by their status (e.g., Completed, Cancelled)
    private void appointmentsStatus(List<String[]> appointments) {
        Map<String, Long> counts = appointments.stream()
                .filter(a -> a.length > 12)
                .map(a -> a[12].isBlank() ? "Unknown" : a[12])
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        reportModel.setColumnIdentifiers(new String[]{"Status", "Count"});
        reportModel.setRowCount(0);
        counts.forEach((k, v) -> reportModel.addRow(new Object[]{k, v}));
    }
    
    // Count appointments per doctor
    private void appointmentsByDoctor(List<String[]> appointments) {
        Map<String, Long> counts = appointments.stream()
                .filter(a -> a.length > 7)
                .map(a -> a[7].isBlank() ? (a.length > 6 ? a[6] : "Unknown") : a[7]) // prefer doctorName else doctorId
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        reportModel.setColumnIdentifiers(new String[]{"Doctor", "Appointments"});
        reportModel.setRowCount(0);
        counts.forEach((k, v) -> reportModel.addRow(new Object[]{k, v}));
    }

    // Count appointments per specialization
    private void appointmentsBySpecialization(List<String[]> appointments) {
        Map<String, Long> counts = appointments.stream()
                .filter(a -> a.length > 8)
                .map(a -> a[8].isBlank() ? "Unknown" : a[8])
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        reportModel.setColumnIdentifiers(new String[]{"Specialization", "Appointments"});
        reportModel.setRowCount(0);
        counts.forEach((k, v) -> reportModel.addRow(new Object[]{k, v}));
    }

    // Count appointments per shift (morning, afternoon, etc.)
    private void appointmentsByShift(List<String[]> appointments) {
        Map<String, Long> counts = appointments.stream()
                .filter(a -> a.length > 9)
                .map(a -> a[9].isBlank() ? "Unknown" : a[9])
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        reportModel.setColumnIdentifiers(new String[]{"Shift", "Appointments"});
        reportModel.setRowCount(0);
        counts.forEach((k, v) -> reportModel.addRow(new Object[]{k, v}));
    }

    // ==================== TREATMENT REPORTS ====================
    // Compute average, total, and record count of treatment bills
    private void treatmentAverageBill(List<String[]> records) {
        double sum = 0.0;
        int count = 0;
        for (String[] r : records) {
            if (r.length > 6 && !empty(r[6])) {
                try {
                    sum += Double.parseDouble(r[6]);
                    count++;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        reportModel.setColumnIdentifiers(new String[]{"Metric", "Value"});
        reportModel.setRowCount(0);
        reportModel.addRow(new Object[]{"Records considered", count});
        reportModel.addRow(new Object[]{"Average Bill", count == 0 ? "0.00" : moneyFmt.format(sum / count)});
        reportModel.addRow(new Object[]{"Total Expected (sum)", moneyFmt.format(sum)});
    }
    
    // List top N treatments, sorted by frequency or revenue
    private void treatmentTopNTreatments(List<String[]> records, int n, boolean byRevenue) {
        // treat 'treatments' column index 7; records may contain semicolon-separated treatments
        Map<String, Integer> freq = new HashMap<>();
        Map<String, Double> rev = new HashMap<>();

        for (String[] r : records) {
            String treatments = r.length > 7 ? r[7] : "";
            double amount = 0.0;
            if (r.length > 6) {
                try { amount = Double.parseDouble(r[6]); } catch (Exception ignored) {}
            }
            if (!empty(treatments)) {
                String[] parts = treatments.split(";");
                // distribute revenue equally among listed treatments for revenue calculation
                double per = parts.length > 0 ? amount / parts.length : 0.0;
                for (String t : parts) {
                    String key = t.trim();
                    if (key.isEmpty()) continue;
                    freq.put(key, freq.getOrDefault(key, 0) + 1);
                    rev.put(key, rev.getOrDefault(key, 0.0) + per);
                }
            }
        }

        // choose sort by frequency or revenue
        List<String> ordered;
        if (byRevenue) {
            ordered = rev.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } else {
            ordered = freq.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        reportModel.setColumnIdentifiers(new String[]{"Treatment", "Frequency", "Estimated Revenue"});
        reportModel.setRowCount(0);

        int limit = Math.min(n, ordered.size());
        for (int i = 0; i < limit; i++) {
            String t = ordered.get(i);
            reportModel.addRow(new Object[]{t, freq.getOrDefault(t, 0), moneyFmt.format(rev.getOrDefault(t, 0.0))});
        }
    }
    
    // Count occurrences of each diagnosis
    private void treatmentCommonDiagnoses(List<String[]> records) {
        Map<String, Integer> count = new HashMap<>();
        for (String[] r : records) {
            String diag = r.length > 8 ? r[8] : "";
            if (empty(diag)) continue;
            String key = diag.trim();
            count.put(key, count.getOrDefault(key, 0) + 1);
        }
        List<Map.Entry<String, Integer>> ordered = count.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        reportModel.setColumnIdentifiers(new String[]{"Diagnosis", "Count"});
        reportModel.setRowCount(0);
        for (Map.Entry<String, Integer> e : ordered) {
            reportModel.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }
    
    // Count how many times each medication is prescribed
    private void treatmentPrescriptions(List<String[]> records) {
        // prescription format example: "Paracetamol~Twice~After Meal"
        Map<String, Integer> meds = new HashMap<>();
        for (String[] r : records) {
            String pres = r.length > 9 ? r[9] : "";
            if (empty(pres)) continue;
            String[] parts = pres.split(";");
            for (String p : parts) {
                String med = p.split("~", 2)[0].trim();
                if (med.isEmpty()) continue;
                meds.put(med, meds.getOrDefault(med, 0) + 1);
            }
        }

        List<Map.Entry<String, Integer>> ordered = meds.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        reportModel.setColumnIdentifiers(new String[]{"Medication", "Prescribed Count"});
        reportModel.setRowCount(0);
        for (Map.Entry<String, Integer> e : ordered) {
            reportModel.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }

    // ==================== FINANCE REPORTS ====================
    // Sum total payments collected
    private void financeTotalIncome(List<String[]> payments) {
        double total = 0.0;
        for (String[] p : payments) {
            if (p.length > 3 && !empty(p[3])) {
                try { total += Double.parseDouble(p[3]); } catch (NumberFormatException ignored) {}
            }
        }
        reportModel.setColumnIdentifiers(new String[]{"Metric", "Value"});
        reportModel.setRowCount(0);
        reportModel.addRow(new Object[]{"Total Collected Income", moneyFmt.format(total)});
        reportModel.addRow(new Object[]{"Payments Count", payments.size()});
    }
    
    // Compare expected revenue vs actual collected
    private void financeExpectedVsActual(List<String[]> records, List<String[]> payments) {
        double expected = 0.0;
        for (String[] r : records) {
            if (r.length > 6 && !empty(r[6])) {
                try { expected += Double.parseDouble(r[6]); } catch (NumberFormatException ignored) {}
            }
        }
        double collected = 0.0;
        for (String[] p : payments) {
            if (p.length > 3 && !empty(p[3])) {
                try { collected += Double.parseDouble(p[3]); } catch (NumberFormatException ignored) {}
            }
        }
        reportModel.setColumnIdentifiers(new String[]{"Metric", "Value"});
        reportModel.setRowCount(0);
        reportModel.addRow(new Object[]{"Total Expected (from records)", moneyFmt.format(expected)});
        reportModel.addRow(new Object[]{"Total Collected (payments)", moneyFmt.format(collected)});
        reportModel.addRow(new Object[]{"Outstanding (expected - collected)", moneyFmt.format(expected - collected)});
    }
    
    // Aggregate collected revenue by doctor
    private void financeByDoctor(List<String[]> records, List<String[]> payments, List<String[]> appointments) {
        // Build appointmentId -> doctorName (prefer appointment doctor name; fallback to record doctorId)
        Map<String, String> apptToDoctorName = buildApptToDoctorNameMap(appointments, records);

        Map<String, Double> collectedByDoctor = new HashMap<>();
        for (String[] p : payments) {
            if (p.length > 3) {
                String apptId = p.length > 1 ? p[1] : "";
                double amount = parseAmountSafe(p, 3);
                String doctor = apptToDoctorName.getOrDefault(apptId, "Unknown Doctor");
                collectedByDoctor.put(doctor, collectedByDoctor.getOrDefault(doctor, 0.0) + amount);
            }
        }

        reportModel.setColumnIdentifiers(new String[]{"Doctor", "Collected Income"});
        reportModel.setRowCount(0);
        collectedByDoctor.forEach((doc, amt) -> reportModel.addRow(new Object[]{doc, moneyFmt.format(amt)}));
    }
    
    // Aggregate collected revenue by specialization
    private void financeBySpecialization(List<String[]> records, List<String[]> payments, List<String[]> appointments) {
        Map<String, String> apptToSpec = buildApptToSpecializationMap(appointments);

        Map<String, Double> collectedBySpec = new HashMap<>();
        for (String[] p : payments) {
            if (p.length > 3) {
                String apptId = p.length > 1 ? p[1] : "";
                double amount = parseAmountSafe(p, 3);
                String spec = apptToSpec.getOrDefault(apptId, "Unknown Specialization");
                collectedBySpec.put(spec, collectedBySpec.getOrDefault(spec, 0.0) + amount);
            }
        }

        reportModel.setColumnIdentifiers(new String[]{"Specialization", "Collected Income"});
        reportModel.setRowCount(0);
        collectedBySpec.forEach((spec, amt) -> reportModel.addRow(new Object[]{spec, moneyFmt.format(amt)}));
    }

    // Summarize revenue by payment method
    private void financeByPaymentMethod(List<String[]> payments) {
        Map<String, Double> byMethod = new HashMap<>();
        Map<String, Integer> countByMethod = new HashMap<>();

        for (String[] p : payments) {
            if (p.length > 4) {
                String method = empty(p[4]) ? "Unknown" : p[4];
                double amount = parseAmountSafe(p, 3);
                byMethod.put(method, byMethod.getOrDefault(method, 0.0) + amount);
                countByMethod.put(method, countByMethod.getOrDefault(method, 0) + 1);
            }
        }

        reportModel.setColumnIdentifiers(new String[]{"Payment Method", "Collected Amount", "Count"});
        reportModel.setRowCount(0);
        for (String method : byMethod.keySet()) {
            reportModel.addRow(new Object[]{method, moneyFmt.format(byMethod.get(method)), countByMethod.getOrDefault(method, 0)});
        }
    }

    // ==================== CROSS REPORTS ====================
    // List unpaid appointments (expected > 0, no payment)
    private void crossUnpaidAppointments(List<String[]> records, List<String[]> payments, List<String[]> appointments) {
        // payments by appointmentId set
        Set<String> paidApptIds = payments.stream().filter(p -> p.length > 1).map(p -> p[1]).collect(Collectors.toSet());

        // Build appointment map for lookup
        Map<String, String[]> apptMap = appointments.stream().filter(a -> a.length > 0).collect(Collectors.toMap(a -> a[0], a -> a, (a, b) -> a));

        reportModel.setColumnIdentifiers(new String[]{"ApptID", "Patient", "Doctor", "Expected Amount"});
        reportModel.setRowCount(0);

        for (String[] r : records) {
            if (r.length > 0) {
                String apptId = r[0];
                if (paidApptIds.contains(apptId)) continue; // already paid

                double amount = 0.0;
                if (r.length > 6) {
                    try { amount = Double.parseDouble(r[6]); } catch (Exception ignored) {}
                }
                if (amount <= 0.0) continue; // ignore zero-amount records

                String[] appt = apptMap.getOrDefault(apptId, new String[]{apptId, "", "", "", "", "", "", "Unknown Doctor", "Unknown Spec"});
                String patient = (appt.length > 2 ? appt[2] : "") + " " + (appt.length > 3 ? appt[3] : "");
                String doctor = appt.length > 7 ? appt[7] : (r.length > 2 ? r[2] : "Unknown");
                reportModel.addRow(new Object[]{apptId, patient.trim(), doctor, moneyFmt.format(amount)});
            }
        }
    }
    
    // Summarize expected vs collected revenue per doctor
    private void crossRevenueByDoctor(List<String[]> records, List<String[]> payments, List<String[]> appointments) {
        // expected per doctor (from records, doctorId in index 2)
        Map<String, Double> expectedByDoctor = new HashMap<>();
        // doctorId -> doctorName (from appointments)
        Map<String, String> doctorNames = buildDoctorNameMap(appointments);

        for (String[] r : records) {
            String dId = r.length > 2 ? r[2] : "Unknown";
            double amt = parseAmountSafe(r, 6);
            expectedByDoctor.put(dId, expectedByDoctor.getOrDefault(dId, 0.0) + amt);
        }

        // collected by doctor via payment -> appointment -> doctor (lookup appointments)
        Map<String, String> apptToDocId = appointments.stream()
                .filter(a -> a.length > 6)
                .collect(Collectors.toMap(a -> a[0], a -> a[6], (a, b) -> a));

        Map<String, Double> collectedByDoctor = new HashMap<>();
        for (String[] p : payments) {
            String apptId = p.length > 1 ? p[1] : "";
            String docId = apptToDocId.getOrDefault(apptId, "Unknown");
            double amt = parseAmountSafe(p, 3);
            collectedByDoctor.put(docId, collectedByDoctor.getOrDefault(docId, 0.0) + amt);
        }

        // Prepare table
        reportModel.setColumnIdentifiers(new String[]{"Doctor ID", "Doctor Name", "Expected", "Collected", "Collection %"});
        reportModel.setRowCount(0);

        // union of doctor ids
        Set<String> allDoctors = new HashSet<>();
        allDoctors.addAll(expectedByDoctor.keySet());
        allDoctors.addAll(collectedByDoctor.keySet());

        for (String dId : allDoctors) {
            double exp = expectedByDoctor.getOrDefault(dId, 0.0);
            double col = collectedByDoctor.getOrDefault(dId, 0.0);
            String name = doctorNames.getOrDefault(dId, dId);
            String pct = exp == 0.0 ? "N/A" : moneyFmt.format((col / exp) * 100) + "%";
            reportModel.addRow(new Object[]{dId, name, moneyFmt.format(exp), moneyFmt.format(col), pct});
        }
    }
    
    // Summarize expected vs collected revenue per specialization
    private void crossRevenueBySpecialization(List<String[]> records, List<String[]> payments, List<String[]> appointments) {
        // apptId -> specialization
        Map<String, String> apptToSpec = buildApptToSpecializationMap(appointments);

        Map<String, Double> expectedBySpec = new HashMap<>();
        for (String[] r : records) {
            String apptId = r.length > 0 ? r[0] : "";
            String spec = apptToSpec.getOrDefault(apptId, "Unknown");
            double amt = parseAmountSafe(r, 6);
            expectedBySpec.put(spec, expectedBySpec.getOrDefault(spec, 0.0) + amt);
        }

        Map<String, Double> collectedBySpec = new HashMap<>();
        for (String[] p : payments) {
            String apptId = p.length > 1 ? p[1] : "";
            String spec = apptToSpec.getOrDefault(apptId, "Unknown");
            double amt = parseAmountSafe(p, 3);
            collectedBySpec.put(spec, collectedBySpec.getOrDefault(spec, 0.0) + amt);
        }

        reportModel.setColumnIdentifiers(new String[]{"Specialization", "Expected", "Collected", "Collection %"});
        reportModel.setRowCount(0);
        Set<String> keys = new HashSet<>();
        keys.addAll(expectedBySpec.keySet());
        keys.addAll(collectedBySpec.keySet());

        for (String k : keys) {
            double exp = expectedBySpec.getOrDefault(k, 0.0);
            double col = collectedBySpec.getOrDefault(k, 0.0);
            String pct = exp == 0.0 ? "N/A" : moneyFmt.format((col / exp) * 100) + "%";
            reportModel.addRow(new Object[]{k, moneyFmt.format(exp), moneyFmt.format(col), pct});
        }
    }

    // List top paying patients
    private void crossTopPatients(List<String[]> payments, int topN) {
        Map<String, Double> byPatient = new HashMap<>();
        for (String[] p : payments) {
            String patientId = p.length > 2 ? p[2] : "Unknown";
            double amt = parseAmountSafe(p, 3);
            byPatient.put(patientId, byPatient.getOrDefault(patientId, 0.0) + amt);
        }
        List<Map.Entry<String, Double>> ordered = byPatient.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .collect(Collectors.toList());

        reportModel.setColumnIdentifiers(new String[]{"Patient ID", "Total Paid"});
        reportModel.setRowCount(0);
        for (Map.Entry<String, Double> e : ordered) {
            reportModel.addRow(new Object[]{e.getKey(), moneyFmt.format(e.getValue())});
        }
    }

    // ==================== UTILITIES / LOADERS ====================
    // Load a file into list of string arrays (split by '|')
    private List<String[]> loadFile(String filename) {
        List<String[]> out = new ArrayList<>();
        Path p = Paths.get(filename);
        if (!Files.exists(p)) return out;

        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                out.add(line.split(Pattern.quote("|"), -1));
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error reading " + filename + ": " + e.getMessage());
        }
        return out;
    }

    // Build map: appointmentId -> specialization
    private Map<String, String> buildApptToSpecializationMap(List<String[]> appointments) {
        Map<String, String> map = new HashMap<>();
        for (String[] a : appointments) {
            if (a.length > 8) {
                String apptId = a[0];
                String spec = empty(a[8]) ? "Unknown" : a[8];
                map.put(apptId, spec);
            }
        }
        return map;
    }
    
    // Build map: appointmentId -> doctor name (prefers appointment info, fallback to record)
    private Map<String, String> buildApptToDoctorNameMap(List<String[]> appointments, List<String[]> records) {
        // prefer doctor name from appointments; if missing, fallback to records' doctorId
        Map<String, String> map = new HashMap<>();
        for (String[] a : appointments) {
            if (a.length > 0) {
                String id = a[0];
                String docName = a.length > 7 && !empty(a[7]) ? a[7] : (a.length > 6 ? a[6] : "Unknown");
                map.put(id, docName);
            }
        }
        // fallback: if appt not present in appointments but present in records
        for (String[] r : records) {
            if (r.length > 0) {
                String apptId = r[0];
                if (!map.containsKey(apptId)) {
                    String dId = r.length > 2 ? r[2] : "Unknown";
                    map.put(apptId, dId);
                }
            }
        }
        return map;
    }

    // Build map: doctorId -> doctor name
    private Map<String, String> buildDoctorNameMap(List<String[]> appointments) {
        Map<String, String> map = new HashMap<>();
        for (String[] a : appointments) {
            if (a.length > 6) {
                String docId = a[6];
                String docName = a.length > 7 && !empty(a[7]) ? a[7] : docId;
                map.put(docId, docName);
            }
        }
        return map;
    }

    // Safely parse double from array at index, return 0.0 if invalid
    private double parseAmountSafe(String[] row, int idx) {
        if (row == null || row.length <= idx) return 0.0;
        String s = row[idx];
        if (empty(s)) return 0.0;
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return 0.0; }
    }

    // Safely parse double from array at index, return 0.0 if invalid
    private boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}

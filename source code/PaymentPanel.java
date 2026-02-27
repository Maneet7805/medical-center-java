package assignment;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.table.DefaultTableCellRenderer;

public class PaymentPanel extends JPanel {
    private final JTextField searchField;
    private final JTable unpaidTable;
    private final DefaultTableModel unpaidModel;
    private final String currentStaffUsername;

    private static final String APPOINTMENTS_FILE = "appointments_records.txt";
    private static final String PAYMENTS_FILE = "payments.txt";

    private final JPopupMenu suggestionsPopup = new JPopupMenu();
    private final Set<String> patientNames = new HashSet<>();
    private DefaultTableModel model;

    // Constructor: Initializes the PaymentPanel UI, including search, table, buttons, predictive suggestions, and event listeners
    public PaymentPanel(String currentStaffUsername) {
        this.currentStaffUsername = currentStaffUsername;

        setLayout(new BorderLayout());

        // ===== Top Panel (Search) =====
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel searchLabel = new JLabel("Enter Patient Name: ");
        searchField = new JTextField(20);
        JButton searchBtn = new JButton("Search");
        JButton viewAllBtn = new JButton("View All");
        topPanel.add(searchLabel);
        topPanel.add(searchField);
        topPanel.add(searchBtn);
        topPanel.add(viewAllBtn);

        // ===== Table for Unpaid Appointments =====
        unpaidModel = new DefaultTableModel(
                new String[]{"AppointmentID", "PatientID", "DoctorID", "Date", "Time", "Treatment", "Amount"}, 0
        );
        unpaidTable = new JTable(unpaidModel);
        JScrollPane tableScroll = new JScrollPane(unpaidTable);

        // ===== Buttons =====
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton generateInvoiceBtn = new JButton("Generate Invoice");
        buttonPanel.add(generateInvoiceBtn);

        // ===== Layout =====
        add(topPanel, BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // ===== Event Listeners =====
        searchBtn.addActionListener(e -> searchUnpaidAppointments());
        generateInvoiceBtn.addActionListener(e -> generateInvoice());
        viewAllBtn.addActionListener(e -> searchUnpaidAppointments(true));

        // ===== Load patient names for predictive suggestions =====
        loadPatientNames();

        // ===== Show all unpaid appointments initially =====
        searchUnpaidAppointments(true);

        // ===== Predictive suggestions =====
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                showSuggestions(searchField, suggestionsPopup, patientNames);
            }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                showSuggestions(searchField, suggestionsPopup, patientNames);
            }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                showSuggestions(searchField, suggestionsPopup, patientNames);
            }
        });
    }
    
    // Loads all patient names from the file for predictive search suggestions
    private void loadPatientNames() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("patients.txt"));
            for (String line : lines) {
                String[] p = line.split("\\|");
                if (p.length >= 5) {
                    String fullName = p[3] + " " + p[4]; // FirstName + LastName
                    patientNames.add(fullName);
                }
            }
        } catch (IOException e) {}
    }

    // Triggers search for unpaid appointments based on the search field
    private void searchUnpaidAppointments() {
        searchUnpaidAppointments(false);
    }

    // Core logic to search unpaid appointments; can view all or filter by keyword
    private void searchUnpaidAppointments(boolean viewAll) {
        unpaidModel.setRowCount(0);
        String keyword = searchField.getText().trim().toLowerCase();

        Set<String> paidAppointments = loadPaidAppointments();

        try {
            List<String> lines = Files.readAllLines(Paths.get(APPOINTMENTS_FILE));
            for (String line : lines) {
                String[] parts = line.split("\\|");
                if (parts.length < 8) continue;

                String apptId = parts[0];
                String patientId = parts[1];
                String doctorId = parts[2];
                String date = parts[4];
                String time = parts[5];
                String amount = parts[6];
                String treatment = parts[7];

                String patientName = getPatientName(patientId).toLowerCase();

                boolean matches = viewAll || patientName.contains(keyword);

                if (matches && !paidAppointments.contains(apptId)) {
                    unpaidModel.addRow(new Object[]{apptId, patientId, doctorId, date, time, treatment, amount});
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error reading appointments file.");
        }
    }

    // Retrieves a patient's full name using their patient ID
    private String getPatientName(String patientId) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("patients.txt"));
            for (String line : lines) {
                String[] p = line.split("\\|");
                if (p[0].equals(patientId)) {
                    return p[3] + " " + p[4]; // FirstName + LastName
                }
            }
        } catch (IOException e) {}
        return "";
    }

    // Loads the set of appointment IDs that have already been paid
    private Set<String> loadPaidAppointments() {
        Set<String> paid = new HashSet<>();
        File file = new File(PAYMENTS_FILE);
        if (!file.exists()) return paid;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    paid.add(parts[1]); // AppointmentID
                }
            }
        } catch (IOException e) {}
        return paid;
    }
    
    // Generates and displays the invoice panel for the selected appointment
    private void generateInvoice() {
        int row = unpaidTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an appointment to generate invoice.");
            return;
        }

        String apptId = unpaidModel.getValueAt(row, 0).toString();
        String patientId = unpaidModel.getValueAt(row, 1).toString();
        String doctorId = unpaidModel.getValueAt(row, 2).toString();
        String date = unpaidModel.getValueAt(row, 3).toString();
        String time = unpaidModel.getValueAt(row, 4).toString();
        String treatment = unpaidModel.getValueAt(row, 5).toString();
        String amount = unpaidModel.getValueAt(row, 6).toString();

        JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        JPanel contentPanel = (JPanel) topFrame.getContentPane().getComponent(1);
        CardLayout cardLayout = (CardLayout) contentPanel.getLayout();

        InvoicePanel invoicePanel = new InvoicePanel(apptId, patientId, doctorId, date, time, treatment, amount, currentStaffUsername, row);
               
        contentPanel.add(invoicePanel, "invoice");
        cardLayout.show(contentPanel, "invoice");
    }

    // Displays predictive search suggestions in a popup based on typed text
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

    // ====================== INVOICE PANEL ======================
    class InvoicePanel extends JPanel {
        private String apptId, patientId, doctorId;
        private String staffUsername, invoiceNo;
        private int rowIndex;

        private static final String PAYMENTS_FILE = "payments.txt";
        private static final String PATIENTS_FILE = "patients.txt";
        private static final String DOCTORS_FILE = "doctors.txt";
        private static final String TREATMENTS_FILE = "treatments.txt";

        // Constructor: Initializes the invoice panel with patient, doctor info, treatment table, totals, and action buttons
        public InvoicePanel(String apptId, String patientId, String doctorId, String date,
                            String time, String treatmentList, String amount, String staffUsername,
                            int rowIndex) {

            this.apptId = apptId;
            this.patientId = patientId;
            this.doctorId = doctorId;
            this.staffUsername = staffUsername;
            this.rowIndex = rowIndex;

            setLayout(new BorderLayout(20, 20));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            // ================= Patient & Doctor Info =================
            JPanel infoPanel = new JPanel(new GridLayout(1, 2, 40, 10));
            infoPanel.setOpaque(false);

            JTextArea patientArea = new JTextArea(getPatientInfo(patientId));
            patientArea.setEditable(false);
            patientArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            patientArea.setMargin(new Insets(10, 10, 10, 10));
            infoPanel.add(wrapWithTitledPanel("Patient Information", patientArea));

            JTextArea doctorArea = new JTextArea(getDoctorInfo(doctorId));
            doctorArea.setEditable(false);
            doctorArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            doctorArea.setMargin(new Insets(10, 10, 10, 10));
            infoPanel.add(wrapWithTitledPanel("Doctor Information", doctorArea));

            // ================= Invoice Header =================
            JPanel headerPanel = new JPanel(new GridLayout(1, 3, 20, 5));
            headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            Random rand = new Random();
            int randomNumber = 10000 + rand.nextInt(90000);
            this.invoiceNo = "I" + randomNumber;

            JLabel lblInvoice = new JLabel("Invoice No: " + invoiceNo, SwingConstants.LEFT);
            JLabel lblDate = new JLabel("Date: " + date, SwingConstants.CENTER);
            JLabel lblAmount = new JLabel("Amount Due: RM " + amount, SwingConstants.RIGHT);
            lblInvoice.setFont(new Font("Segoe UI", Font.BOLD, 18));
            lblDate.setFont(new Font("Segoe UI", Font.BOLD, 18));
            lblAmount.setFont(new Font("Segoe UI", Font.BOLD, 18));

            headerPanel.add(lblInvoice);
            headerPanel.add(lblDate);
            headerPanel.add(lblAmount);

            // ================= Combine Info + Header =================
            JPanel topPanel = new JPanel();
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
            topPanel.add(infoPanel);
            topPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            topPanel.add(headerPanel);

            add(topPanel, BorderLayout.NORTH);

            // ================= Treatments Table =================
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
            centerPanel.setOpaque(false);

            String[] columns = {"Treatment ID", "Treatment Name", "Cost (RM)"};
            model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = new JTable(model);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            table.setRowHeight(30);
            table.setFillsViewportHeight(true);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            // Right-align cost column + alternate row colors
            table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus,
                                                               int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (!isSelected) c.setBackground(row % 2 == 0 ? new Color(245, 245, 245) : Color.WHITE);
                    ((JLabel) c).setHorizontalAlignment(SwingConstants.RIGHT);
                    return c;
                }
            });

            // Other columns: alternate row colors only
            DefaultTableCellRenderer alternateRenderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus,
                                                               int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (!isSelected) c.setBackground(row % 2 == 0 ? new Color(245, 245, 245) : Color.WHITE);
                    ((JLabel) c).setHorizontalAlignment(SwingConstants.LEFT);
                    return c;
                }
            };
            table.getColumnModel().getColumn(0).setCellRenderer(alternateRenderer);
            table.getColumnModel().getColumn(1).setCellRenderer(alternateRenderer);

            table.getTableHeader().setBackground(new Color(0x26A69A));
            table.getTableHeader().setForeground(Color.WHITE);
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 16));

            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(750, 250));
            centerPanel.add(scroll);
            centerPanel.add(Box.createRigidArea(new Dimension(0, 15)));

            // ================= Load Treatments & Totals =================
            double subtotal = 0.0;
            Map<String, String[]> treatments = loadTreatments();

            for (String treatName : treatmentList.split(";")) {
                treatName = treatName.trim();
                for (Map.Entry<String, String[]> entry : treatments.entrySet()) {
                    if (entry.getValue()[0].equalsIgnoreCase(treatName)) {
                        String tid = entry.getKey();
                        String tname = entry.getValue()[0];
                        double cost = Double.parseDouble(entry.getValue()[1]);
                        model.addRow(new Object[]{tid, tname, String.format("%.2f", cost)});
                        subtotal += cost;
                    }
                }
            }

            double taxRate = 0.06;
            double tax = subtotal * taxRate;
            double total = subtotal + tax;

            // ================= Totals Panel =================
            JPanel totalsPanel = new JPanel(new GridLayout(3, 2, 10, 10));

            JLabel lblSubtotal = new JLabel("Subtotal:");
            JLabel lblSubtotalVal = new JLabel("RM " + String.format("%.2f", subtotal));
            lblSubtotalVal.setHorizontalAlignment(SwingConstants.RIGHT);

            JLabel lblTax = new JLabel("Tax (6%):");
            JLabel lblTaxVal = new JLabel("RM " + String.format("%.2f", tax));
            lblTaxVal.setHorizontalAlignment(SwingConstants.RIGHT);

            JLabel lblTotal = new JLabel("Total:");
            JLabel lblTotalVal = new JLabel("RM " + String.format("%.2f", total));
            lblTotalVal.setHorizontalAlignment(SwingConstants.RIGHT);

            totalsPanel.add(lblSubtotal);
            totalsPanel.add(lblSubtotalVal);
            totalsPanel.add(lblTax);
            totalsPanel.add(lblTaxVal);
            totalsPanel.add(lblTotal);
            totalsPanel.add(lblTotalVal);

            totalsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
            totalsPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            for (Component comp : totalsPanel.getComponents()) {
                if (comp instanceof JLabel jLabel) jLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            }

            centerPanel.add(totalsPanel);
            add(centerPanel, BorderLayout.CENTER);

            // ================= Buttons =================
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton payBtn = new JButton("Proceed to Payment");
            JButton backBtn = new JButton("Back");
            bottomPanel.add(payBtn);
            bottomPanel.add(backBtn);
            add(bottomPanel, BorderLayout.PAGE_END);

            // ===== Button Actions =====
            payBtn.addActionListener(e -> processPayment(total));
            backBtn.addActionListener(e -> {
                JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
                JPanel contentPanel = (JPanel) topFrame.getContentPane().getComponent(1);
                CardLayout cardLayout = (CardLayout) contentPanel.getLayout();
                cardLayout.show(contentPanel, "payments");
            });
        }

        // ================= Helper Methods =================
        // Wraps a component inside a titled border panel
        private JPanel wrapWithTitledPanel(String title, JComponent component) {
            JPanel panel = new JPanel(new BorderLayout());
            javax.swing.border.TitledBorder border = BorderFactory.createTitledBorder(title);
            border.setTitleFont(new Font("Segoe UI", Font.BOLD, 18));
            panel.setBorder(border);
            panel.add(component, BorderLayout.CENTER);
            return panel;
        }

        // Retrieves detailed patient information from file using patient ID
        private String getPatientInfo(String patientId) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(PATIENTS_FILE));
                for (String line : lines) {
                    String[] p = line.split("\\|");
                    if (p[0].equals(patientId)) {
                        return p[3] + " " + p[4] + "\n" +
                               "Phone: " + p[9] + "\n" +
                               "Email: " + p[8] + "\n" +
                               "Address: " + p[10] + ", " + p[11] + ", " + p[12];
                    }
                }
            } catch (IOException e) {}
            return "Patient not found.";
        }
        
        // Retrieves detailed doctor information from file using doctor ID
        private String getDoctorInfo(String doctorId) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(DOCTORS_FILE));
                for (String line : lines) {
                    String[] d = line.split("\\|");
                    if (d[0].equals(doctorId)) {
                        return d[3] + " " + d[4] + "\n" +
                               "Specialization: " + d[13] + "\n" +
                               "Phone: " + d[9] + "\n" +
                               "Email: " + d[8];
                    }
                }
            } catch (IOException e) {}
            return "Doctor not found.";
        }

        // Loads all treatment records from file into a map
        private Map<String, String[]> loadTreatments() {
            Map<String, String[]> map = new HashMap<>();
            try {
                List<String> lines = Files.readAllLines(Paths.get(TREATMENTS_FILE));
                for (String line : lines) {
                    String[] t = line.split("\\|");
                    if (t.length >= 3) map.put(t[0].trim(), new String[]{t[1].trim(), t[2].trim()});
                }
            } catch (IOException e) {}
            return map;
        }

        // Processes the payment, saves it to file, updates table, and shows receipt
        private void processPayment(double total) {
            String[] methods = {"Cash", "Card", "Online"};
            String method = (String) JOptionPane.showInputDialog(
                    this, "Select Payment Method:", "Payment",
                    JOptionPane.PLAIN_MESSAGE, null, methods, methods[0]
            );
            if (method == null) return;

            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            String record = String.join("|",
                    invoiceNo, apptId, patientId,
                    String.format("%.2f", total), method, dateTime, staffUsername
            );

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(PAYMENTS_FILE, true))) {
                bw.write(record);
                bw.newLine();
                JOptionPane.showMessageDialog(this, "Payment successful!");
                unpaidModel.removeRow(rowIndex);
                showReceipt(invoiceNo, total, method, dateTime);
                
                // Go back to payments panel
                JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
                JPanel contentPanel = (JPanel) topFrame.getContentPane().getComponent(1);
                CardLayout cardLayout = (CardLayout) contentPanel.getLayout();
                cardLayout.show(contentPanel, "payments");
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving payment.");
            }
        }
        
        // Displays the formatted receipt for the completed payment
        private void showReceipt(String invoiceNo, double total, String method, String dateTime) {
            StringBuilder sb = new StringBuilder();
            sb.append("========== Dental Clinic ==========\n");
            sb.append("Invoice No: ").append(invoiceNo).append("\n");
            sb.append("Date: ").append(dateTime).append("\n\n");

            sb.append("Patient Information:\n");
            sb.append(getPatientInfo(patientId)).append("\n\n");

            sb.append("Doctor Information:\n");
            sb.append(getDoctorInfo(doctorId)).append("\n\n");

            sb.append("Treatments:\n");
            for (int i = 0; i < model.getRowCount(); i++) {
                String tid = model.getValueAt(i, 0).toString();
                String tname = model.getValueAt(i, 1).toString();
                String cost = model.getValueAt(i, 2).toString();
                sb.append("  ").append(tid).append(" - ").append(tname)
                  .append(" (RM ").append(cost).append(")\n");
            }

            sb.append("\n-----------------------------------\n");
            sb.append("Total Amount: RM ").append(String.format("%.2f", total)).append("\n");
            sb.append("Payment Method: ").append(method).append("\n");
            sb.append("Staff: ").append(staffUsername).append("\n");
            sb.append("===================================\n");

            JTextArea area = new JTextArea(sb.toString());
            area.setFont(new Font("Monospaced", Font.PLAIN, 14));
            area.setEditable(false);

            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(500, 400));

            JOptionPane.showMessageDialog(this, scroll, "Receipt", JOptionPane.INFORMATION_MESSAGE);
        }
    }   
}

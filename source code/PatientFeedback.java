package assignment;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

public class PatientFeedback extends JPanel {
    private static final String FEEDBACK_FILE = "feedback.txt";
    private final String patientId;
    private final JTable awaitingTable, pastTable;
    private final DefaultTableModel awaitingModel, pastModel;

    // Constructor: Initializes the feedback panel with tabs for awaiting and past feedback
    public PatientFeedback(String patientId) {
        this.patientId = patientId;

        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(900, 600)); // wider & taller

        // --- Tabs for Awaiting Feedback & Past Feedback ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));

        // --- Awaiting Feedback Table ---
        awaitingModel = new DefaultTableModel(
                new String[]{"Appointment ID", "Date", "Doctor", "Give Feedback"}, 0
        ) {
            @Override public boolean isCellEditable(int r, int c) { return c == 3; }
        };

        awaitingTable = new JTable(awaitingModel);
        awaitingTable.setRowHeight(40);
        awaitingTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        awaitingTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 15));
        awaitingTable.setFillsViewportHeight(true);
        awaitingTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        awaitingTable.getColumn("Give Feedback").setCellRenderer(
                new TableButtonRenderer("Give", "No appointments awaiting feedback")
        );
        awaitingTable.getColumn("Give Feedback").setCellEditor(
                new TableButtonEditor(new JCheckBox(), "Give", "No appointments awaiting feedback", row -> {
                    String apptId = String.valueOf(awaitingModel.getValueAt(row, 0));
                    openFeedbackForm(apptId);
                })
        );

        JScrollPane scrollAwaiting = new JScrollPane(awaitingTable);
        scrollAwaiting.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tabs.add("Awaiting Feedback", scrollAwaiting);

        // --- Past Feedback Table ---
        pastModel = new DefaultTableModel(
                new String[]{"Appointment ID", "Rating", "Comments"}, 0
        ) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        pastTable = new JTable(pastModel);
        pastTable.setRowHeight(40);
        pastTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        pastTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 15));
        pastTable.setFillsViewportHeight(true);
        pastTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JScrollPane scrollPast = new JScrollPane(pastTable);
        scrollPast.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tabs.add("Past Feedback", scrollPast);

        add(tabs, BorderLayout.CENTER);

        // Load data
        loadAwaitingAppointments();
        loadPastFeedback();
    }

    // Prompts the user to give feedback for a specific appointment
    public void promptFeedbackForAppointment(String apptId) {
        openFeedbackForm(apptId);
    }

    // Loads appointments that are awaiting feedback for this patient
    private void loadAwaitingAppointments() {
        awaitingModel.setRowCount(0);
        Path p = Paths.get("appointments.txt");
        if (!Files.exists(p)) {
            addPlaceholderRow(awaitingModel, "No appointments awaiting feedback");
            return;
        }
        boolean found = false;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] a = ln.split(Pattern.quote("|"), -1);
                if (a.length >= 8 && a[1].equals(patientId)) {
                    String apptId = a[0];
                    if (!feedbackExists(apptId)) {
                        awaitingModel.addRow(new Object[]{apptId, a[4], a[7], "Give"});
                        found = true;
                    }
                }
            }
        } catch (IOException ignored) {}
        if (!found) addPlaceholderRow(awaitingModel, "No appointments awaiting feedback");
    }

    // Checks if feedback already exists for a given appointment
    private boolean feedbackExists(String apptId) {
        Path p = Paths.get(FEEDBACK_FILE);
        if (!Files.exists(p)) return false;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] f = ln.split(Pattern.quote("|"), -1);
                if (f.length >= 5 && f[0].equals(apptId)) return true; // format updated
            }
        } catch (IOException ignored) {}
        return false;
    }

    // Loads past feedback submitted by this patient
    private void loadPastFeedback() {
        pastModel.setRowCount(0);
        Path p = Paths.get(FEEDBACK_FILE);
        if (!Files.exists(p)) {
            pastModel.addRow(new Object[]{"No feedback given", "", ""});
            return;
        }
        boolean found = false;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] f = ln.split(Pattern.quote("|"), -1);
                if (f.length >= 5 && f[2].equals(patientId)) {
                    pastModel.addRow(new Object[]{f[0], f[3], f[4]});
                    found = true;
                }
            }
        } catch (IOException ignored) {}
        if (!found) pastModel.addRow(new Object[]{"No feedback given", "", ""});
    }

    // Saves feedback for a given appointment to the feedback file
    private synchronized void saveFeedback(String apptId, String stars, String comments) {
        int rating = 0;
        for (char c : stars.toCharArray()) if (c == '★') rating++;
        if (rating == 0) rating = 5;

        String doctorId = "";
        Path apptFile = Paths.get("appointments.txt");
        if (Files.exists(apptFile)) {
            try (BufferedReader br = Files.newBufferedReader(apptFile, StandardCharsets.UTF_8)) {
                String ln;
                while ((ln = br.readLine()) != null) {
                    String[] a = ln.split(Pattern.quote("|"), -1);
                    if (a.length >= 7 && a[0].equals(apptId)) {
                        doctorId = a[6];
                        break;
                    }
                }
            } catch (IOException ignored) {}
        }

        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(FEEDBACK_FILE),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(apptId + "|" + doctorId + "|" + patientId + "|" + rating + "|" + comments);
            bw.newLine();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save feedback: " + ex.getMessage());
        }
    }

    // Adds a placeholder row to a table model when there is no data
    private void addPlaceholderRow(DefaultTableModel model, String message) {
        Object[] row = new Object[model.getColumnCount()];
        row[0] = message;
        for (int i = 1; i < row.length; i++) row[i] = "";
        model.addRow(row);
    }

    
    // Opens a feedback dialog/form for the patient to submit feedback
    private void openFeedbackForm(String apptId) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel ratingPanel = new JPanel(new FlowLayout());
        ratingPanel.setBorder(BorderFactory.createTitledBorder("Select Your Rating"));
        String[] emojis = {"😃", "🙂", "😐", "😟", "😡"};
        String[] stars = {"★★★★★", "★★★★", "★★★", "★★", "★"};
        final String[] selectedRating = {stars[0]};
        for (int i = 0; i < emojis.length; i++) {
            JButton btn = new JButton(emojis[i]);
            final int idx = i;
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
            btn.addActionListener(e -> selectedRating[0] = stars[idx]);
            ratingPanel.add(btn);
        }

        JTextArea commentArea = new JTextArea(5, 30);
        commentArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        commentArea.setBorder(BorderFactory.createTitledBorder("Comments"));

        panel.add(ratingPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(commentArea), BorderLayout.CENTER);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    this, panel, "Give Feedback", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) return;
            if (commentArea.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a comment before submitting.");
                continue;
            }
            saveFeedback(apptId, selectedRating[0], commentArea.getText().trim());
            loadAwaitingAppointments();
            loadPastFeedback();
            break;
        }
    }

    // Renders a button inside a table cell, disabling it if it's a placeholder
    public static class TableButtonRenderer extends JButton implements TableCellRenderer {
        private final String placeholderText;

        public TableButtonRenderer(String text, String placeholderText) {
            setText(text);
            this.placeholderText = placeholderText;
            setFocusable(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            boolean isPlaceholder = placeholderText.equals(String.valueOf(table.getValueAt(row, 0)));
            setEnabled(!isPlaceholder);
            setForeground(isPlaceholder ? Color.GRAY : Color.BLACK);
            return this;
        }
    }

    // Handles button clicks in table cells and invokes a row-specific action
    public static class TableButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private final RowAction action;
        private final String placeholderText;
        private int row;

        public interface RowAction { void onClick(int row); }

        public TableButtonEditor(JCheckBox checkBox, String text, String placeholderText, RowAction act) {
            super(checkBox);
            this.action = act;
            this.placeholderText = placeholderText;
            this.button = new JButton(text);
            button.setFocusable(false);
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = row;
            boolean isPlaceholder = placeholderText.equals(String.valueOf(table.getValueAt(row, 0)));
            button.setEnabled(!isPlaceholder);
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (button.isEnabled()) action.onClick(row);
            return null;
        }
    }
}

package assignment;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.time.format.DateTimeFormatter;
import javax.swing.event.*;
import javax.swing.table.*;
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE;
import org.jdatepicker.impl.*;

public class UserManagementPanel extends JPanel {

    // --- UI components ---
    private final JTable userTable;
    private final JComboBox<String> roleSelector;
    private final DefaultTableModel model;
    private final JTextField searchField;
    private final TableRowSorter<DefaultTableModel> rowSorter;


    // --- Constructor ---
    public UserManagementPanel(String[] allowedRoles) {

        setLayout(new BorderLayout());

        roleSelector = new JComboBox<>(allowedRoles);
        JButton createBtn = new JButton("Register");
        JButton viewBtn = new JButton("View");
        JButton updateBtn = new JButton("Update Selected");
        JButton deleteBtn = new JButton("Delete Selected");
        searchField = new JTextField(15);

        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Select Role:"));
        topPanel.add(roleSelector);
        topPanel.add(new JLabel("Search:"));
        topPanel.add(searchField);
        topPanel.add(createBtn);
        topPanel.add(viewBtn);
        topPanel.add(updateBtn);
        topPanel.add(deleteBtn);

        model = new DefaultTableModel();
        userTable = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(userTable);
        
        rowSorter = new TableRowSorter<>(model);
        userTable.setRowSorter(rowSorter);
        
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        createBtn.addActionListener(e -> openRegistration());
        viewBtn.addActionListener(e -> loadUsers());
        updateBtn.addActionListener(e -> updateSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        
        // Filter logic
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterTable();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterTable();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                filterTable();
            }
        });
        
        // Auto-load users for the first role by default
        if (roleSelector.getItemCount() > 0) {
            roleSelector.setSelectedIndex(0);
            loadUsers();
        }

        // Refresh data when role changes
        roleSelector.addActionListener(e -> loadUsers());

        setVisible(true);
    }
    
    // --- Filtering users in table ---
    private void filterTable() {
        String text = searchField.getText().trim();
        if (text.length() == 0) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text)); // case-insensitive
        }
    }
    
    // --- Open registration frame ---
    private void openRegistration() {
        String role = roleSelector.getSelectedItem().toString();
        new RegistrationFrame(role);
    }

    // --- Load users from file into table ---
    private void loadUsers() {
        String role = roleSelector.getSelectedItem().toString();
        List<String[]> users = UserFileHandler.readUsersFromFile(role);

        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No users found for role: " + role);
            return;
        }

        model.setRowCount(0);
        model.setColumnCount(0);

        // Set headers based on role (doctor has specialization & shift)
        String[] headers = role.equalsIgnoreCase("doctor") ?
                new String[]{"ID", "Username", "Password", "First Name", "Last Name", "Gender",
                        "DOB", "Age", "Email", "Contact", "Address", "Postcode", "State", "Specialization", "Shift"} :
                new String[]{"ID", "Username", "Password", "First Name", "Last Name", "Gender",
                        "DOB", "Age", "Email", "Contact", "Address", "Postcode", "State"};

        for (String header : headers) {
            model.addColumn(header);
        }

        for (String[] user : users) {
            model.addRow(user);
        }
    }

    // --- Update selected user ---
    private void updateSelected() {
        int row = userTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user to update.");
            return;
        }

        String role = roleSelector.getSelectedItem().toString().toLowerCase();
        int columnCount = model.getColumnCount();
        String[] data = new String[columnCount];

        for (int i = 0; i < columnCount; i++) {
            data[i] = model.getValueAt(row, i).toString();
        }

        String[] fieldNames;
        if (role.equals("doctor")) {
            fieldNames = new String[]{
                "ID", "Username", "Password", "First Name", "Last Name", "Gender",
                "DOB", "Age", "Email", "Contact", "Address", "Postcode", "State", "Specialization", "Shift"
            };
        } else {
            fieldNames = new String[]{
                "ID", "Username", "Password", "First Name", "Last Name", "Gender",
                "DOB", "Age", "Email", "Contact", "Address", "Postcode", "State"
            };
        }

        UpdateUserDialog updateUserDialog = new UpdateUserDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Update User - ID: " + data[0],
            fieldNames,
            data,
            updatedData -> {
                if (updatedData != null) {
                    // Optional: add validation here too, if desired
                    // if (!validateFields(updatedData, role)) return;

                    UserFileHandler.updateUserById(role, updatedData[0], updatedData);
                    JOptionPane.showMessageDialog(this, "User updated successfully.");

                    for (int i = 0; i < updatedData.length; i++) {
                        model.setValueAt(updatedData[i], row, i);
                    }
                }
            }
        );

        updateUserDialog.setVisible(true);
    }

    // --- Delete selected user ---
    private void deleteSelected() {
        int row = userTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user to delete.");
            return;
        }

        String role = roleSelector.getSelectedItem().toString();
        String id = userTable.getValueAt(row, 0).toString();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete user ID: " + id + "?",
                "Confirm Deletion", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            UserFileHandler.deleteUserById(role, id);
            model.removeRow(row);
            JOptionPane.showMessageDialog(this, "User deleted successfully.");
        }
    }
}

// ---------------- UpdateUserDialog ----------------
class UpdateUserDialog extends JDialog {

    // Constructor sets up form fields and buttons
    public UpdateUserDialog(Frame parent, String title, String[] fieldNames, String[] data, Consumer<String[]> onUpdate) {
        super(parent, title, true);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Form panel
        JPanel formPanel = new JPanel(new GridLayout(fieldNames.length, 2, 10, 10));
        JComponent[] fields = new JComponent[fieldNames.length];
        
        // Loop over fields to create appropriate input components (text, combo, date picker)
        UtilDateModel model = new UtilDateModel();
        Properties p = new Properties();
        p.put("text.today", "Today");
        p.put("text.month", "Month");
        p.put("text.year", "Year");
        JDatePanelImpl datePanel = new JDatePanelImpl(model, p);
        JDatePickerImpl dobPicker = new JDatePickerImpl(datePanel, new DateComponentFormatter());
        JTextField ageField = new JTextField();
        ageField.setEditable(false);

        for (int i = 0; i < fieldNames.length; i++) {
            JLabel label = new JLabel(fieldNames[i] + ":");
            JComponent field;

            switch (fieldNames[i]) {
                case "ID" -> {
                    field = new JTextField(data[i]);
                    ((JTextField) field).setEditable(false);
                }
                case "Gender" -> {
                    JComboBox<String> genderBox = new JComboBox<>(new String[]{"Male", "Female"});
                    genderBox.setSelectedItem(data[i]);
                    field = genderBox;
                }
                case "DOB" -> {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                        LocalDate dob = LocalDate.parse(data[i], formatter);
                        model.setDate(dob.getYear(), dob.getMonthValue() - 1, dob.getDayOfMonth());
                        model.setSelected(true);
                    } catch (Exception e) {}
                    field = dobPicker;
                }
                case "Age" -> {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                        LocalDate dob = LocalDate.parse(data[6], formatter);
                        ageField.setText(String.valueOf(Period.between(dob, LocalDate.now()).getYears()));
                    } catch (Exception e) {
                        ageField.setText("Invalid DOB");
                    }
                    field = ageField;
                }
                case "Specialization" -> {
                    JComboBox<String> specBox = new JComboBox<>(new String[]{
                        "General Practice", "Dental", "Pediatrics", "Dermatology", "ENT"
                    });
                    specBox.setSelectedItem(data[i]);
                    field = specBox;
                }
                case "Shift" -> {
                    JComboBox<String> shiftBox = new JComboBox<>(new String[]{
                        "Shift A", "Shift B", "Shift C"
                    });
                    shiftBox.setSelectedItem(data[i]);
                    field = shiftBox;
                }
                default -> field = new JTextField(data[i]);
            }

            fields[i] = field;
            formPanel.add(label);
            formPanel.add(field);
        }
        
         model.addChangeListener(e -> {
            if (model.getValue() != null) {
                LocalDate selected = model.getValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                int age = Period.between(selected, LocalDate.now()).getYears();
                ageField.setText(String.valueOf(age));
            }
        });
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton updateBtn = new JButton("Update");
        JButton cancelBtn = new JButton("Cancel");

        // Update button logic
        updateBtn.addActionListener(e -> {
            String[] updatedData = new String[fields.length];

            for (int i = 0; i < fields.length; i++) {
                switch (fields[i]) {
                    case JTextField textField -> updatedData[i] = textField.getText().trim();
                    case JComboBox comboBox -> updatedData[i] = comboBox.getSelectedItem().toString().trim();
                    case JDatePickerImpl dp -> {
                        Date selected = (Date) dp.getModel().getValue();
                        if (selected != null) {
                            LocalDate date = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            updatedData[i] = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        } else {
                            updatedData[i] = "";
                        }
                    }
                    default -> {
                    }
                }
            }
            
            String role = title.toLowerCase().contains("doctor") ? "doctor" :
                          title.toLowerCase().contains("staff") ? "staff" :
                          title.toLowerCase().contains("manager") ? "manager" :
                          title.toLowerCase().contains("patient") ? "patient" : "";

            if (!validateFields(updatedData, role)) return;

            onUpdate.accept(updatedData);
            dispose();
        });

        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(updateBtn);
        buttonPanel.add(cancelBtn);

        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    // --- Field validation ---    
    private boolean validateFields(String[] data, String role) {
        String id = data[0];
        String username = data[1];
        String password = data[2];
        String gender = data[5];
        String dob = data[6];
        String email = data[8];
        String contact = data[9];
        String postcode = data[11];

        // Reserved admin name
        if (username.equalsIgnoreCase("admin")) {
            JOptionPane.showMessageDialog(this, "Username 'admin' is reserved.");
            return false;
        }

        // Username uniqueness
        String[] roles = {"manager", "staff", "doctor", "patient"};
        for (String r : roles) {
            File file = new File(r + "s.txt");
            if (!file.exists()) continue;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length > 2 && parts[1].equalsIgnoreCase(username) && !parts[0].equals(id)) {
                        JOptionPane.showMessageDialog(this, "Username already exists in " + r + "s.");
                        return false;
                    }
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error reading " + r + "s.txt");
                return false;
            }
        }

        // Gender must be Male or Female
        if (!gender.equals("Male") && !gender.equals("Female")) {
            JOptionPane.showMessageDialog(this, "Gender must be Male or Female.");
            return false;
        }

        // Email validation
        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            JOptionPane.showMessageDialog(this, "Invalid email format.");
            return false;
        }

        // Contact validation (01xxxxxxxx or 01xxxxxxxxx)
        if (!contact.matches("^01\\d{8,9}$")) {
            JOptionPane.showMessageDialog(this, "Invalid contact. Must start with 01 and have 10–11 digits.");
            return false;
        }

        // Postcode validation
        if (!postcode.matches("^\\d{5}$")) {
            JOptionPane.showMessageDialog(this, "Postcode must be exactly 5 digits.");
            return false;
        }

        // Password length
        if (password.length() < 6) {
            JOptionPane.showMessageDialog(this, "Password must be at least 6 characters.");
            return false;
        }

        // Age recalculation (lock user edit)
        try {
            LocalDate birthDate = LocalDate.parse(dob, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            int actualAge = Period.between(birthDate, LocalDate.now()).getYears();
            data[7] = String.valueOf(actualAge); // overwrite age
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid DOB format. Use dd/MM/yyyy");
            return false;
        }
        
        // Doctor-specific: specialization and shift validation
        if (role.equalsIgnoreCase("doctor")) {
            if (data.length < 15) {
                JOptionPane.showMessageDialog(this, "Missing specialization or shift information for doctor.");
                return false;
            }

            String specialization = data[13];
            String shift = data[14];

            // Acceptable options
            String[] validSpecializations = {
                "General Practice", "Dental", "Pediatrics", "Dermatology", "ENT"
            };
            String[] validShifts = {"Shift A", "Shift B", "Shift C"};

            boolean specValid = Arrays.asList(validSpecializations).contains(specialization);
            boolean shiftValid = Arrays.asList(validShifts).contains(shift);

            if (!specValid) {
                JOptionPane.showMessageDialog(this, "Invalid specialization: " + specialization);
                return false;
            }

            if (!shiftValid) {
                JOptionPane.showMessageDialog(this, "Invalid shift: " + shift);
                return false;
            }
        }
        return true;
    }
}




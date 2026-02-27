package assignment;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.time.format.DateTimeFormatter;
import javax.swing.Timer;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;
import org.jdatepicker.impl.*;

 // Launches the HomePage GUI on the Event Dispatch Thread.
public class LoginRegistrationApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HomePage());
    }
}

class HomePage extends JFrame {
    // Initializes the home page with a background image and a login button.
    public HomePage() {
        setTitle("Welcome to APU Medical Center");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        ImageIcon original = new ImageIcon(getClass().getResource("home_page.jpg"));
        Image originalImage = original.getImage();

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;

        double imgWidth = originalImage.getWidth(null);
        double imgHeight = originalImage.getHeight(null);
        double widthRatio = screenWidth / imgWidth;
        double heightRatio = screenHeight / imgHeight;
        double scaleFactor = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (imgWidth * scaleFactor);
        int newHeight = (int) (imgHeight * scaleFactor);

        Image scaled = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        ImageIcon backgroundIcon = new ImageIcon(scaled);
        JLabel backgroundLabel = new JLabel(backgroundIcon);
        backgroundLabel.setLayout(new BorderLayout());

        JButton loginBtn = new JButton("Login");
        customizeButton(loginBtn);
        loginBtn.addActionListener(e -> {
            dispose();
            new LoginFrame();
        });

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 30, 100)); 
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 60, 50, 0)); 
        bottomPanel.add(loginBtn);

        backgroundLabel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(backgroundLabel);
        setVisible(true);
    }

    // Applies custom styling to a button (font, color, border, size).
    private void customizeButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 24));
        button.setBackground(new Color(0, 153, 76));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(0, 102, 51), 2));
        button.setPreferredSize(new Dimension(200, 55));
    }
}

class LoginFrame extends JFrame {
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private int loginAttempts = 0;
    private Timer lockoutTimer;
    private int countdown = 30;
    private final JButton loginBtn;
    
    // Initializes the login frame with background image, login form, and navigation buttons.
    public LoginFrame() {
        setTitle("Login - APU Medical Center");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        ImageIcon original = new ImageIcon(getClass().getResource("login_page.jpg"));
        Image originalImage = original.getImage(); 

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;

        double imgWidth = originalImage.getWidth(null);
        double imgHeight = originalImage.getHeight(null);
        double widthRatio = screenWidth / imgWidth;
        double heightRatio = screenHeight / imgHeight;
        double scaleFactor = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (imgWidth * scaleFactor);
        int newHeight = (int) (imgHeight * scaleFactor);

        Image scaled = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        ImageIcon backgroundIcon = new ImageIcon(scaled);
        JLabel backgroundLabel = new JLabel(backgroundIcon);
        backgroundLabel.setLayout(new BorderLayout());

        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 204), 3),
                "Login",
                0, 0,
                new Font("Segoe UI", Font.BOLD, 24),
                new Color(0, 102, 204)
        ));
        loginPanel.setBackground(new Color(255, 255, 255, 230));
        loginPanel.setPreferredSize(new Dimension(500, 400));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        gbc.gridx = 0; gbc.gridy = 1;
        loginPanel.add(userLabel, gbc);

        usernameField = new JTextField();
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        usernameField.setPreferredSize(new Dimension(100, 30));
        gbc.gridx = 1;
        loginPanel.add(usernameField, gbc);

        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        gbc.gridx = 0; gbc.gridy = 2;
        loginPanel.add(passLabel, gbc);

        passwordField = new JPasswordField();
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        passwordField.setPreferredSize(new Dimension(100, 30));
        gbc.gridx = 1;
        loginPanel.add(passwordField, gbc);

        loginBtn = new JButton("Login");
        JButton backBtn = new JButton("Back");
        customizeButton(loginBtn);
        customizeButton(backBtn);
        
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> login());
        
        getRootPane().setDefaultButton(loginBtn);

        gbc.gridx = 0; gbc.gridy = 3;
        loginPanel.add(loginBtn, gbc);
        gbc.gridx = 1;
        loginPanel.add(backBtn, gbc);

        JPanel outerWrapper = new JPanel(new BorderLayout());
        outerWrapper.setOpaque(false);

        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);

        GridBagConstraints wrapperGbc = new GridBagConstraints();
        wrapperGbc.gridy = 0;
        wrapperGbc.anchor = GridBagConstraints.CENTER;
        wrapperGbc.insets = new Insets(100, 100, 0, 0); // left margin

        JPanel fixedPanel = new JPanel(new BorderLayout());
        fixedPanel.setOpaque(false);
        fixedPanel.setPreferredSize(new Dimension(500, 400));
        fixedPanel.add(loginPanel, BorderLayout.CENTER);

        leftPanel.add(fixedPanel, wrapperGbc);
        outerWrapper.add(leftPanel, BorderLayout.WEST);

        backgroundLabel.add(outerWrapper, BorderLayout.CENTER);

        setContentPane(backgroundLabel);

        loginBtn.addActionListener(e -> login());
        backBtn.addActionListener(e -> {
            dispose();
            new HomePage();
        });

        setVisible(true);
    }

    // Applies custom styling to a button specifically for the login frame.
    private void customizeButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 20));
        button.setBackground(new Color(0, 102, 204));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(new Color(0, 51, 102), 2));
        button.setFocusPainted(false);
    }
    
    // Handles login logic for all roles, validates credentials, and redirects to the respective dashboard.
    private void login() {
        if (isLocked()) {
            JOptionPane.showMessageDialog(this, "Too many attempts! Please wait " + countdown + " seconds.");
            return;
        }

        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and password.");
            return;
        }

        // Hardcoded admin check
        if (username.equals("admin") && password.equals("123456")) {
            loginAttempts = 0; // reset
            JOptionPane.showMessageDialog(this, "Welcome Admin!");
            dispose();
            new AdminDashboard(username);
            return;
        }

        String[] roles = {"manager", "staff", "doctor", "patient"};
        boolean userFound = false;
        boolean loginSuccess = false;

        for (String role : roles) {
            String fileName = role + "s.txt";
            try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length > 2 && parts[1].equals(username)) {
                        userFound = true;
                        if (parts[2].equals(password)) {
                            loginAttempts = 0; // reset
                            loginSuccess = true;

                            JOptionPane.showMessageDialog(this, "Login Successful as " + role);
                            dispose();
                            switch (role) {
                                case "manager" -> new ManagerDashboard(username);
                                case "staff" -> new StaffDashboard(username);
                                case "doctor" -> new DoctorDashboard(username);
                                case "patient" -> new PatientDashboard(username);
                            }
                            return;
                        }
                    }
                }
            } catch (IOException e){}
        }

        if (!userFound) {
            loginAttempts++;
            passwordField.setText("");
            usernameField.requestFocusInWindow();
            usernameField.selectAll();
            handleFailedLogin("Username not found. Attempt " + loginAttempts + " of 3.");
        } else if (!loginSuccess) {
            loginAttempts++;
            passwordField.setText("");
            passwordField.requestFocusInWindow();
            handleFailedLogin("Incorrect password. Attempt " + loginAttempts + " of 3.");
        }
    }

    // Displays a failure message and initiates lockout after 3 failed attempts.
    private void handleFailedLogin(String message) {
        JOptionPane.showMessageDialog(this, message);
        if (loginAttempts >= 3) {
            startLockout();
        }
    }

    // Checks if login is currently locked due to too many failed attempts.
    private boolean isLocked() {
        return lockoutTimer != null && lockoutTimer.isRunning();
    }

    // Starts a 30-second lockout timer, disables login fields, and shows countdown.
    private void startLockout() {
        countdown = 30;
        loginBtn.setEnabled(false);
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);

        lockoutTimer = new Timer(1000, e -> {
            countdown--;
            loginBtn.setText("Wait " + countdown + "s");
            if (countdown <= 0) {
                lockoutTimer.stop();
                loginAttempts = 0;
                loginBtn.setText("Login");
                loginBtn.setEnabled(true);
                usernameField.setEnabled(true);
                passwordField.setEnabled(true);
                usernameField.requestFocusInWindow();
                usernameField.selectAll();
            }
        });
        lockoutTimer.start();

        JOptionPane.showMessageDialog(this, "Too many failed attempts! Locked for 30 seconds.");
    }
}

class RegistrationFrame extends JFrame {
    private final JTextField firstName, lastName, email, contact, address1, postcode, state, username, ageField;
    private final JPasswordField password;
    private final JRadioButton maleRadio, femaleRadio;
    private final ButtonGroup genderGroup;
    private JComboBox<String> specializationBox, shiftBox;
    private final String role;
    private final JDatePickerImpl dobPicker;

    // Initializes registration frame with fields, validation, and optional doctor-specific fields (specialization and shift).
    public RegistrationFrame(String role) {
        this.role = role;
        setTitle("Register New " + capitalize(role));
        setSize(600, 750);
        setLayout(new GridLayout(18, 2, 5, 5));
        setLocationRelativeTo(null);

        // Input fields
        firstName = new JTextField();
        lastName = new JTextField();
        email = new JTextField();
        contact = new JTextField();
        address1 = new JTextField();
        postcode = new JTextField();
        state = new JTextField();
        username = new JTextField();
        password = new JPasswordField();
        ageField = new JTextField();
        ageField.setEditable(false);
        ageField.setBackground(Color.LIGHT_GRAY);

        // Gender
        maleRadio = new JRadioButton("Male");
        femaleRadio = new JRadioButton("Female");
        genderGroup = new ButtonGroup();
        genderGroup.add(maleRadio);
        genderGroup.add(femaleRadio);
        JPanel genderPanel = new JPanel();
        genderPanel.add(maleRadio);
        genderPanel.add(femaleRadio);

        // JDatePicker
        UtilDateModel model = new UtilDateModel();
        Properties p = new Properties();
        p.put("text.today", "Today");
        p.put("text.month", "Month");
        p.put("text.year", "Year");
        JDatePanelImpl datePanel = new JDatePanelImpl(model, p);
        dobPicker = new JDatePickerImpl(datePanel, new DateComponentFormatter());
        JPanel dobPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dobPanel.add(new JLabel("Date of Birth:"));
        dobPanel.add(dobPicker);
        model.addChangeListener(e -> updateAge());

        // Buttons
        JButton registerBtn = new JButton("Submit Registration");
        JButton closeBtn = new JButton("Close");

        // Add fields
        add(new JLabel("First Name:")); add(firstName);
        add(new JLabel("Last Name:")); add(lastName);
        add(new JLabel("Gender:")); add(genderPanel);
        add(new JLabel("Date of Birth:")); add(dobPanel);
        add(new JLabel("Email:")); add(email);
        add(new JLabel("Contact:")); add(contact);
        add(new JLabel("Address Line 1:")); add(address1);
        add(new JLabel("Post Code:")); add(postcode);
        add(new JLabel("State:")); add(state);
        add(new JLabel("Username:")); add(username);
        add(new JLabel("Password:")); add(password);

        // Specialization and Shift for Doctors
        if (role.equalsIgnoreCase("doctor")) {
            specializationBox = new JComboBox<>(new String[]{"General Practice", "Dental", "Pediatrics", "Dermatology", "ENT"});
            shiftBox = new JComboBox<>(new String[]{"Shift A", "Shift B", "Shift C"});

            add(new JLabel("Specialization:")); add(specializationBox);
            add(new JLabel("Shift:")); add(shiftBox);
        }

        // Final buttons
        add(registerBtn); add(closeBtn);
        registerBtn.addActionListener(e -> register());
        closeBtn.addActionListener(e -> dispose());

        setVisible(true);
    }
    
    // Validates registration fields, checks username uniqueness, confirms details, generates ID, and writes user data to file.
    private void register() {
        String usernameInput = username.getText().trim();
        String passwordInput = new String(password.getPassword()).trim();

        // Block reserved admin username
        if (usernameInput.equalsIgnoreCase("admin")) {
            JOptionPane.showMessageDialog(this, "Username 'admin' is reserved. Please choose another username.");
            return;
        }

        // Check if username exists in any role file
        String[] rolesToCheck = {"manager", "staff", "doctor", "patient"};
        for (String r : rolesToCheck) {
            File file = new File(r + "s.txt");
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\\|");
                        if (parts.length > 2 && parts[1].equalsIgnoreCase(usernameInput)) {
                            JOptionPane.showMessageDialog(this,
                                    "Username already exists. Please choose a different username.");
                            return;
                        }
                    }
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Error reading " + r + "s.txt");
                    return;
                }
            }
        }

        // Get field values
        String first = firstName.getText().trim();
        String last = lastName.getText().trim();
        String em = email.getText().trim();
        String phone = contact.getText().trim();
        String addr = address1.getText().trim();
        String pc = postcode.getText().trim();
        String st = state.getText().trim();
        String gender = maleRadio.isSelected() ? "Male" : femaleRadio.isSelected() ? "Female" : "";
        
        if (dobPicker.getModel().getValue() == null) {
            JOptionPane.showMessageDialog(this, "Please select a date of birth.");
            return;
        }

        Date selectedDate = (Date) dobPicker.getModel().getValue();
        if (selectedDate == null) {
            JOptionPane.showMessageDialog(this, "Please select a valid date of birth.");
            return;
        }
        LocalDate birthDate = selectedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String dob = birthDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        
        // Specialization and shift (for doctor)
        boolean doctorNeedsSpec = role.equalsIgnoreCase("doctor") &&
                (specializationBox.getSelectedItem() == null || shiftBox.getSelectedItem() == null);

        // Basic empty field check
        if (first.isEmpty() || last.isEmpty() || gender.isEmpty() || em.isEmpty() || phone.isEmpty()
                || addr.isEmpty() || pc.isEmpty() || st.isEmpty() || usernameInput.isEmpty() || passwordInput.isEmpty()
                || doctorNeedsSpec) {
            JOptionPane.showMessageDialog(this, "All fields are required including gender, specialization, shift, and DOB.");
            return;
        }

        // Phone validation
        if (!phone.matches("^01\\d{8,9}$")) {
            JOptionPane.showMessageDialog(this, "Contact must start with '01' and be 10 or 11 digits long.");
            return;
        }

        // Postcode validation
        if (!pc.matches("^\\d{5}$")) {
            JOptionPane.showMessageDialog(this, "Postcode must be exactly 5 digits.");
            return;
        }

        // Email validation
        if (!em.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            JOptionPane.showMessageDialog(this, "Invalid email format. Example: user@example.com");
            return;
        }

        // Password strength
        if (passwordInput.length() < 6) {
            JOptionPane.showMessageDialog(this, "Password must be at least 6 characters long.");
            return;
        }

        // Confirm dialog
        StringBuilder confirmMessage = new StringBuilder("Confirm?\n\n");
        confirmMessage.append("Name: ").append(first).append(" ").append(last).append("\n");
        confirmMessage.append("Gender: ").append(gender).append("\n");
        confirmMessage.append("DOB: ").append(dob).append(" (Age: ").append(age).append(")\n");
        confirmMessage.append("Username: ").append(usernameInput).append("\n");
        if (role.equalsIgnoreCase("doctor")) {
            confirmMessage.append("Specialization: ").append(specializationBox.getSelectedItem()).append("\n");
            confirmMessage.append("Shift: ").append(shiftBox.getSelectedItem()).append("\n");
        }

        int choice = JOptionPane.showConfirmDialog(this, confirmMessage.toString(), "Confirm Registration", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;

        // Generate ID
        String idPrefix = role.substring(0, 1).toUpperCase();
        String id = idPrefix + (new Random().nextInt(90000) + 10000);

        // Build output line
        StringBuilder sb = new StringBuilder();
        sb.append(id).append("|")
          .append(usernameInput).append("|")
          .append(passwordInput).append("|")
          .append(first).append("|")
          .append(last).append("|")
          .append(gender).append("|")
          .append(dob).append("|")
          .append(age).append("|")
          .append(em).append("|")
          .append(phone).append("|")
          .append(addr).append("|")
          .append(pc).append("|")
          .append(st);

        if (role.equalsIgnoreCase("doctor")) {
            sb.append("|").append(specializationBox.getSelectedItem());
            sb.append("|").append(shiftBox.getSelectedItem());
        }

        // Write to file
        try (FileWriter writer = new FileWriter(role + "s.txt", true)) {
            writer.write(sb.toString() + "\n");
            JOptionPane.showMessageDialog(this, capitalize(role)
                    + " Registered Successfully. ID: " + id);
            dispose();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error saving to file: " + role + "s.txt");
        }
    }
    
    // Updates the age field automatically when a date of birth is selected in the date picker.
    private void updateAge() {
        try {
            Date selected = (Date) dobPicker.getModel().getValue();
            if (selected != null) {
                LocalDate birthDate = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                int age = Period.between(birthDate, LocalDate.now()).getYears();
                ageField.setText(String.valueOf(age));
            } else {
                ageField.setText(""); // Clear if nothing is selected
            }
        } catch (Exception ex) {
            ageField.setText("Invalid DOB");
        }
    }

     // Returns the input string with the first letter capitalized.
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}


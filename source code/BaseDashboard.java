package assignment;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

// Abstract base class for dashboards: initializes main frame, sidebar, content panel with CardLayout, and applies theme colors.
public abstract class BaseDashboard extends JFrame {
    protected final String currentUsername;
    protected final CardLayout cardLayout = new CardLayout();
    protected final JPanel contentPanel = new JPanel(cardLayout);
    protected final JPanel sidebar = new JPanel();
    protected final Color themeColor;
    protected final Color buttonColor;

    public BaseDashboard(String username, String titleText, Color sidebarColor, Color buttonColor) {
        this.currentUsername = username;
        this.themeColor = sidebarColor;
        this.buttonColor = buttonColor;

        setTitle(titleText);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        setupSidebar(sidebarColor, titleText);
        setupContentPanel();

        add(sidebar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
        
        cardLayout.show(contentPanel, "home");
        setVisible(true);
    }

    // Sets up the sidebar panel with title, default buttons (Home, Logout), layout, and calls for custom buttons.
    private void setupSidebar(Color bgColor, String titleText) {
        sidebar.setBackground(bgColor);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(250, getHeight()));

        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(titleLabel);

        JButton homeBtn = createSidebarButton("Home");
        JButton logoutBtn = createSidebarButton("Logout");

        homeBtn.addActionListener(e -> cardLayout.show(contentPanel, "home"));
        logoutBtn.addActionListener(e -> {
            dispose();
            new HomePage(); // Make sure HomePage exists
        });

        sidebar.add(homeBtn);
        addCustomSidebarButtons();
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(logoutBtn);
    }

    // Initializes the main content panel with a default home panel containing a welcome message.
    private void setupContentPanel() {
        JPanel homePanel = new JPanel(new BorderLayout());
        JLabel welcome = new JLabel("Welcome to the Dashboard", SwingConstants.CENTER);
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 24));
        homePanel.add(welcome, BorderLayout.CENTER);
        contentPanel.add(homePanel, "home");
    }

    // Creates a styled JButton for the sidebar with consistent colors, size, font, and cursor.
    protected JButton createSidebarButton(String text) {
        JButton button = new JButton(text);
        int height = 60;
        Dimension size = new Dimension(Integer.MAX_VALUE, height);
        button.setMinimumSize(size);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setFocusPainted(false);
        button.setBackground(buttonColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 16));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    // Displays predictive suggestions for a text field based on a given data collection (autocomplete functionality).
    protected void showSuggestions(JTextField field, JPopupMenu popup, Collection<String> data) {
        popup.removeAll();
        String txt = field.getText().trim().toLowerCase();
        if (txt.isEmpty()) {
            popup.setVisible(false);
            return;
        }
        int count = 0;
        for (String s : data) {
            if (s.toLowerCase().startsWith(txt)) { // Predictive match
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

    // Abstract method to allow subclasses to add their own custom buttons to the sidebar.
    protected abstract void addCustomSidebarButtons();
}

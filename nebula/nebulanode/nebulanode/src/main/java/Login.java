import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.sun.jndi.toolkit.url.Uri;
import org.json.JSONException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;

public class Login {
    private JPanel loginPanel;
    private JLabel userLabel;
    private JTextField userName;
    private String user = null;
    private JLabel passLabel;
    private JPasswordField password;
    private String pass = null;
    private JButton loginButton;
    private JLabel intro;
    private JLabel copyrightLabel;
    private JLabel loginError;
    private boolean logged = false;
    private boolean validAccount = false;
    private int trial = 0;
    JFrame frame = new JFrame("Login");
    JDialog loginDialog = new JDialog(frame, "Login", Dialog.ModalityType.APPLICATION_MODAL);
    private JButton signUpButton;
    private JLabel visitLink;

    public boolean run() {
        if (logged == false) {
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.pack();
            frame.setVisible(false);

            loginDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            loginDialog.add(loginPanel);
            loginDialog.pack();
            loginDialog.setLocationByPlatform(true);
            loginDialog.setVisible(true);
        }
        return logged;
    }

    public Login(final String[] args) {
        loginDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (logged) {
                    loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                } else {
                    int confirmed = JOptionPane.showConfirmDialog(loginDialog, "Are you sure you want to stop making money?", "Quitter, quitter!",
                            JOptionPane.YES_NO_OPTION);
                    if (confirmed == JOptionPane.YES_OPTION) {
                        System.exit(0);
                    }
                }
            }
        });
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (trial < 3) {
                    try {
                        if (args.length <= 0) {
                            user = userName.getText();              //TODO - EDITS MADE HERE FOR BATCH ARGUMENTS
                            pass = password.getText();
                            logged = login(user, pass);
                        } else {
                            logged = login(args[0], args[1]);
                            user = args[0];
                            pass = args[1];
                        }
                        if (logged) {
                            System.out.println("You have successfully logged in.");
                            loginError.setText("You have successfully logged in.");
                            loginDialog.dispose();
                        } else {
                            System.out.println("Username or Password is incorrect.");
                            loginError.setText("Username or Password is incorrect.");
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                } else {
                    loginError.setText("<html>Incorrect password limit reached (" + trial + "). <br/>Please wait 5 minutes to try again</html>");
                    Timer timer = new Timer();
                    TimerTask trialReset = new TimerTask() {
                        @Override
                        public void run() {
                            trial = 0;
                            loginError.setText("");
                        }
                    };
                    timer.schedule(trialReset, 5 * 1000);
                }
            }
        });
        signUpButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    final URI uri = new URI("https://www.nebula.my");
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(uri);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
    }

    public boolean login(String email, String password) throws IOException {
        boolean validAccount = false;
        String userData = "https://www.nebula.my/_functions/user/" + email + "/" + password;
        String dataVerify = readFromURL(userData);

        if (dataVerify.contains("TRUE") && dataVerify.contains("FALSE")) {
            loginError.setText("Username or Password is incorrect.");

        } else if (dataVerify.contains("TRUE") && !dataVerify.contains("FALSE")) {
            validAccount = true;
            loginError.setText("");

        } else if (dataVerify.contains("FALSE") && !dataVerify.contains("TRUE")) {
            loginError.setText("Username doesn't exist.");
        }
        return validAccount;
    }

    public boolean login(String[] args) throws IOException {   // TODO - EDITS MADE HERE FOR BATCH ARGUMENTS
        boolean validAccount = login(args[0], args[1]);

        return validAccount;
    }

    private String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public String readFromURL(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            return jsonText;
        } finally {
            is.close();
        }
    }

    public boolean isLogged() {
        return logged;
    }

    public String getUsername() {
        return user;
    }

    public String getPass() {
        return pass;
    }

    public JButton getLoginButton() {
        return loginButton;
    }

    public boolean getLoggedStatus() {
        return logged;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        loginPanel = new JPanel();
        loginPanel.setLayout(new GridLayoutManager(6, 3, new Insets(20, 20, 20, 20), 50, 20));
        loginPanel.setBackground(new Color(-15528407));
        loginPanel.setForeground(new Color(-1));
        loginPanel.setMinimumSize(new Dimension(675, 275));
        loginPanel.setPreferredSize(new Dimension(675, 275));
        intro = new JLabel();
        Font introFont = this.$$$getFont$$$("Segoe UI Light", -1, 14, intro.getFont());
        if (introFont != null) intro.setFont(introFont);
        intro.setForeground(new Color(-1));
        intro.setText("Please sign in to use Node.");
        loginPanel.add(intro, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        password = new JPasswordField();
        password.setBackground(new Color(-1));
        loginPanel.add(password, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(275, -1), new Dimension(275, -1), 0, false));
        userLabel = new JLabel();
        userLabel.setBackground(new Color(-1));
        Font userLabelFont = this.$$$getFont$$$("Segoe UI Light", -1, -1, userLabel.getFont());
        if (userLabelFont != null) userLabel.setFont(userLabelFont);
        userLabel.setForeground(new Color(-1));
        userLabel.setText("Username (Email)");
        loginPanel.add(userLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        passLabel = new JLabel();
        Font passLabelFont = this.$$$getFont$$$("Segoe UI Light", -1, -1, passLabel.getFont());
        if (passLabelFont != null) passLabel.setFont(passLabelFont);
        passLabel.setForeground(new Color(-1));
        passLabel.setText("Password");
        loginPanel.add(passLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        loginButton = new JButton();
        loginButton.setBackground(new Color(-1));
        loginButton.setForeground(new Color(-15124111));
        loginButton.setText("Login");
        loginPanel.add(loginButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        signUpButton = new JButton();
        signUpButton.setBackground(new Color(-1));
        signUpButton.setForeground(new Color(-15124111));
        signUpButton.setText("Sign Up");
        loginPanel.add(signUpButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        loginError = new JLabel();
        loginError.setForeground(new Color(-4487636));
        loginError.setText("");
        loginPanel.add(loginError, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        copyrightLabel = new JLabel();
        Font copyrightLabelFont = this.$$$getFont$$$("Segoe UI Light", Font.BOLD, 14, copyrightLabel.getFont());
        if (copyrightLabelFont != null) copyrightLabel.setFont(copyrightLabelFont);
        copyrightLabel.setForeground(new Color(-1));
        copyrightLabel.setText("by Nebula Technologies.");
        loginPanel.add(copyrightLabel, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        visitLink = new JLabel();
        Font visitLinkFont = this.$$$getFont$$$("Segoe UI Light", Font.BOLD, 15, visitLink.getFont());
        if (visitLinkFont != null) visitLink.setFont(visitLinkFont);
        visitLink.setForeground(new Color(-1));
        visitLink.setText("Visit us at https://www.nebula.my");
        loginPanel.add(visitLink, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        userName = new JTextField();
        userName.setBackground(new Color(-1));
        userName.setText("");
        loginPanel.add(userName, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(275, -1), new Dimension(275, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        return new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return loginPanel;
    }


    public class User {
        public String email;
        public String password;

        public User(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }
}

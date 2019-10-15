import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.json.JSONException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

    public boolean run() {
        if (logged == false) {
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.pack();
            frame.setVisible(false);

            loginDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            loginDialog.add(loginPanel);
            loginDialog.pack();
            loginDialog.setLocationByPlatform(true);
            loginDialog.setVisible(true);
        }
        return logged;
    }

    public Login() {
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
                    user = userName.getText();
                    pass = password.getText();
                    try {
                        logged = login(user, pass);
                        if (logged) {
                            loginError.setText("You have successfully logged in.");
                        } else {
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
        String userdata = readFromURL(userData);

        if (userdata.contains("TRUE") && userdata.contains("FALSE")) {
            loginError.setText("Username or Password is incorrect.");

        } else if (userdata.contains("TRUE") && !userdata.contains("FALSE")) {
            validAccount = true;
            loginError.setText("");

        } else if (userdata.contains("FALSE") && !userdata.contains("TRUE")) {
            loginError.setText("Username doesn't exist.");
        }
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
        loginPanel.setLayout(new GridLayoutManager(5, 3, new Insets(20, 20, 20, 20), 50, 20));
        loginPanel.setBackground(new Color(-15528407));
        loginPanel.setForeground(new Color(-1));
        loginPanel.setMinimumSize(new Dimension(675, 275));
        loginPanel.setPreferredSize(new Dimension(675, 275));
        intro = new JLabel();
        Font introFont = this.$$$getFont$$$("Avenir", -1, 14, intro.getFont());
        if (introFont != null) intro.setFont(introFont);
        intro.setForeground(new Color(-1));
        intro.setText("Please sign in to use Node.");
        loginPanel.add(intro, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        userName = new JTextField();
        userName.setBackground(new Color(-1));
        userName.setText("");
        loginPanel.add(userName, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(250, -1), null, 0, false));
        password = new JPasswordField();
        password.setBackground(new Color(-1));
        loginPanel.add(password, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(250, -1), null, 0, false));
        userLabel = new JLabel();
        userLabel.setBackground(new Color(-1));
        Font userLabelFont = this.$$$getFont$$$("Avenir", -1, -1, userLabel.getFont());
        if (userLabelFont != null) userLabel.setFont(userLabelFont);
        userLabel.setForeground(new Color(-1));
        userLabel.setText("Username (Email)");
        loginPanel.add(userLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        passLabel = new JLabel();
        Font passLabelFont = this.$$$getFont$$$("Avenir", -1, -1, passLabel.getFont());
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
        copyrightLabel = new JLabel();
        Font copyrightLabelFont = this.$$$getFont$$$("Avenir", Font.BOLD | Font.ITALIC, 12, copyrightLabel.getFont());
        if (copyrightLabelFont != null) copyrightLabel.setFont(copyrightLabelFont);
        copyrightLabel.setForeground(new Color(-1));
        copyrightLabel.setText("by Nebula Inc.");
        loginPanel.add(copyrightLabel, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        loginError = new JLabel();
        loginError.setForeground(new Color(-4487636));
        loginError.setText("");
        loginPanel.add(loginError, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
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

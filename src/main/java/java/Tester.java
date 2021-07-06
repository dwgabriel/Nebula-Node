public class Tester {

    public String getSource() {
        String nebulaDirPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("%20", "");

        return nebulaDirPath;
    }
}

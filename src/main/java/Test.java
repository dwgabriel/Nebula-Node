
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PruneType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.tomcat.jni.OS;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;

import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Test {

//    static File userHome = new File(System.getProperty("user.home"));
    //    static File userHome = new File(System.getProperty("user.home"), "/Library/Application Support");
    private static String userHome = System.getProperty("user.home");
    private static String OS = (System.getProperty("os.name")).toUpperCase();


    static File appData = new File(System.getenv("APPDATA"));
    static File temp = new File("E:\\temp");
    static File nebulaData = new File(temp, "Nebula");
    static File taskCache = new File(nebulaData, "taskcache");
    static File results = new File(taskCache, "results");
    private static File updates;

    static DockerClientConfig config;
    static DockerHttpClient dockerHttpClient;
    static DockerClient dockerClient;
    static File programFiles = new File(System.getenv("ProgramFiles"));
    private static String resultsServer = "https://nebula-server.herokuapp.com/complete";
    static BigDecimal cpuEarningPower = null;
    static BigDecimal totalEarningPower = null;
    static LinkedHashMap<String, String> taskParamsMap = new LinkedHashMap<>();
    static File blenderDir = new File("C:\\Program Files\\Blender Foundation\\Blender 2.92");



    private static DecimalFormat timeFormat = new DecimalFormat("#.##");
    private static boolean updated = false;
    static BigDecimal totalEarn = BigDecimal.valueOf(0.011);

    public static void main(String[] args) {
        try {
//            File vrscene = new File("C:\\Users\\Daryl\\Dropbox\\Test Files\\sketchUp\\cynthia", "6674_test.vrscene");
//            File stripped = stripPathVRSCENE(vrscene, "tester");

            File file = new File ("C:\\Users\\Daryl\\Dropbox\\Test Files\\sketchUp\\cynthia\\test", "test.zip");
            File destDir = new File("C:\\Users\\Daryl\\Dropbox\\Test Files\\sketchUp\\cynthia\\test");
            System.out.println("UNZIPPED : " + unzip(file, destDir));



        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static File stripPathVRSCENE(File vrscene, String taskID) throws IOException {
        FileInputStream fis = new FileInputStream(vrscene);
        InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);

        File newVRSCENE = new File(vrscene.getParentFile().getAbsolutePath(), taskID + ".vrscene");
        FileOutputStream fos = new FileOutputStream(newVRSCENE);
        OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        BufferedWriter wr = new BufferedWriter(osw);

        String imgWidth = null;
        String imgHeight = null;

        try {
            for(String line; (line = br.readLine()) != null; ) {
                if (line.contains("file=") && line.contains("\\")) {
                    String[] lineStart = line.split("=");
                    String[] split = line.split("\\\\");
                    String newline = lineStart[0] + "=\"" + split[split.length - 1];
                    line = line.replaceAll("\\\\", "");
                    line = line.replaceFirst(line, newline);
                } else if (line.contains("img_width")) {
                    String[] split = line.split("=");
                    imgWidth = split[1].replaceAll(";", "");
                } else if (line.contains("img_height")) {
                    String[] split = line.split("=");
                    imgHeight = split[1].replaceAll(";", "");
                }
                wr.write(line + System.lineSeparator());
            }
            wr.flush();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            wr.close();
        }

        return newVRSCENE;
    }

    public static File createNewFile(File destinationDir, String entryFileName) throws IOException {
        String newFileName = entryFileName;
        System.out.println("CHECK - entryFileName : " + entryFileName);
        if (entryFileName.contains("\\")) {
            String[] split = entryFileName.split("\\\\");
            newFileName = split[split.length];
        } else if (entryFileName.contains("/")) {
            String[] split = entryFileName.split("/");
            System.out.println("CHECK - Split : " + split.length);
            newFileName = split[split.length - 1];
            System.out.println("CHECK - newFileName : " + newFileName);
        }

        File destFile = new File(destinationDir, newFileName);

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + entryFileName);
        }

        return destFile;
    }

    private static boolean unzip(File file, File destDir) {
        boolean unzipped = false;
        Charset utf8 = Charset.forName("UTF-8");
        Charset csLatin1 = Charset.forName("IBM437");

        try {

            if (file.getName().contains(".zip")) {

                byte[] buffer = new byte[(int) file.length()];
                ZipFile zipFile = new ZipFile(file, csLatin1);

                Enumeration<ZipEntry> entry = (Enumeration<ZipEntry>) zipFile.entries();
                while (entry.hasMoreElements()) {

                    ZipEntry ze = entry.nextElement();
                    BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(ze));
                    System.out.println("FILE TO CREATE : " + ze.getName());
                    File newFile = createNewFile(destDir, ze.getName());

                    if (ze.isDirectory()) {
                        if (!newFile.isDirectory() && !newFile.mkdirs()) {
                            throw new IOException("Failed to create directory " + newFile);
                        }
                    } else {
                        File parent = newFile.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) {
                            throw new IOException("Failed to create directory " + parent);
                        }

                        FileOutputStream fos = new FileOutputStream(newFile);
                        int length;
                        while ((length = bis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        fos.flush();
                        fos.close();
                    }
                }

                unzipped = true;

            } else if (file.getName().contains(".7z")) {

                SevenZFile sevenZFile = new SevenZFile(file);
                SevenZArchiveEntry entry = sevenZFile.getNextEntry();

                while (entry != null) {
                    File newFile = createNewFile(destDir, entry.getName());

                    if (entry.isDirectory()) {

                        if (!newFile.isDirectory() && !newFile.mkdirs()) {
                            throw new IOException("Failed to create directory " + newFile);
                        }
                    } else {

                        File parent = newFile.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) {
                            throw new IOException("Failed to create directory " + parent);
                        }

                        FileOutputStream fos = new FileOutputStream(newFile);
                        byte[] buffer = new byte[(int) entry.getSize()];

                        sevenZFile.read(buffer, 0, buffer.length);
                        fos.write(buffer);
                        fos.flush();
                        fos.close();
                    }
                    entry = sevenZFile.getNextEntry();
                }
                sevenZFile.close();

                unzipped = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return unzipped;
    }

    public static LinkedHashMap<String, Integer> getTileBorders(File vrayTileScript) throws IOException, NumberFormatException {

        LinkedHashMap<String, Integer> tileBorderMap = new LinkedHashMap<>();

        BufferedReader bufferedReader = new BufferedReader(new FileReader(vrayTileScript));
        String line = bufferedReader.readLine();
        String [] tileBorders = line.split(",");

        LinkedHashMap<String, Float> dimensions = getDimensions("123456");
        float imgWidth = dimensions.get("width");
        float imgHeight = dimensions.get("height");

        float topF = Float.parseFloat(tileBorders[0]);
        float leftF = Float.parseFloat(tileBorders[1]);
        float rightF = Float.parseFloat(tileBorders[2]);
        float bottomF = Float.parseFloat(tileBorders[3]);

        int top = (int) round(topF * imgHeight, 0);
        int left = (int) round(leftF * imgWidth, 0);
        int right = (int) round(rightF * imgWidth, 0);
        int bottom = (int) round(bottomF * imgHeight, 0);

        tileBorderMap.put("top", top);
        tileBorderMap.put("left", left);
        tileBorderMap.put("right", right);
        tileBorderMap.put("bottom", bottom);

        return tileBorderMap;
    }

    public static LinkedHashMap<String, Float> getDimensions(String taskID) throws FileNotFoundException {
        File dimensionFile = new File("C:\\Users\\Daryl\\Dropbox\\Test Files\\test_ts", taskID + ".txt");
        LinkedHashMap<String, Float> dimensions = new LinkedHashMap<>();

        try {
            FileInputStream fis = new FileInputStream(dimensionFile);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);

            for (String line; (line = br.readLine()) != null; ) {
                String[] dimensionStrings = line.split(",");

                float imgWidth = Float.parseFloat(dimensionStrings[0]);
                float imgHeight = Float.parseFloat(dimensionStrings[1]);

                dimensions.put("width", imgWidth);
                dimensions.put("height", imgHeight);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dimensions;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}

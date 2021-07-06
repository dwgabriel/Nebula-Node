import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.google.common.io.FileBackedOutputStream;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Node {

    SystemInfo systemInfo = new SystemInfo();
    CentralProcessor processor = systemInfo.getHardware().getProcessor();
    private String schedulerServer = "https://nebula-server.herokuapp.com/scheduler";
    private String resultsServer = "https://nebula-server.herokuapp.com/complete";
    private String rescheduleServer = "https://nebula-server.herokuapp.com/reschedule";
    private String updateServer = "https://nebula-server.herokuapp.com/update";

    File programFiles = new File(System.getenv("ProgramFiles"));
    private static String OS = (System.getProperty("os.name")).toUpperCase();
    private static File appData;
    private static File nebulaData;
    private static File taskCache;
    private static File updates;
    private static File results;
    private static String nodeEmail;
    private static String deviceID;
    private static String ipAddress;
    private static String score = "95";
    public static String gpu = null;
    private static boolean setup = false;
    private static boolean logged = false;
    public static boolean pingStatus = false;
    //    private static final double perGhzHourMYR = 0.016;
    private static final double perGhzHourUSD = 0.004;
    private static final double perGpu = 0.175;
    private static BigDecimal cpuEarningPower = null;
    private static BigDecimal totalEarningPower = null;
    private static double totalGhz;
    //    private static double earningPower;
    private static BigDecimal totalEarn = BigDecimal.valueOf(0);
    private static String productVersion = "1.3.15";                                      // TODO - ALWAYS UPDATE THIS BEFORE PUSHING UPDATES
    private static boolean updated = false;
    private static double totalMinutes = 0;
    private static int currentCpu;
    private static int totalPhysCores;
    private static int totalLogicCores;
    private static DecimalFormat timeFormat = new DecimalFormat("#.##");
    private static DecimalFormat costFormat = new DecimalFormat("##.##");
    private static LinkedHashMap<String, String> taskParamsMap = new LinkedHashMap<>();
    Deque<String> queue = new LinkedList<>();
    Deque<File> resultQueue = new LinkedList<>();

    private Logger logger = LoggerFactory.getLogger(Node.class);

    JFrame frame;
    private JPanel panel1;
    private JLabel status;
    private JButton start;
    private JButton stop;
    private JButton quit;
    private JTextArea logArea;
    private JLabel earningsToday;
    private JLabel totalComputeTime;
    private JLabel earningPowerLabel;
    private JLabel productVersionLabel;
    private JSlider cpuConfig;
    private JLabel core2;
    private JLabel core1;
    private JLabel core3;
    private JLabel core4;
    private JButton apply;
    private JLabel cpuPercentage;

//    public static Docker docker;

    public static void main(String[] args) throws Exception {

        Login login = new Login(args); // TODO - EDITS MADE HERE FOR BATCH ARGUMENTS

        while (!logged) {
            System.out.println("[LOG] Running . . .");
            if (args.length > 0 && args[2].equals("login")) {
                System.out.println("[LOG] Batch");
                login.getLoginButton().doClick();
                logged = login.getLoggedStatus();
            } else {
                logged = login.run();
            }

            if (logged) {
                nodeEmail = login.getUsername();
                System.out.println("[LOG] Node Email : " + nodeEmail);
                final Node node = new Node();
                setup = node.startNode();

                if (args.length > 0 && args[3].equals("start")) {
                    int index = 1;
                    while (setup) {
                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                node.start.doClick();
                            }
                        }, 3000);
                        System.out.println("[LOG] Clicked. No. : " + index);
                        index++;
                        break;
                    }
                }
                break;
            } else {
                System.out.println("[LOG] Login unsuccessful.");
            }
            break;
        }
    }

    public Node() throws IOException {
        // UI/UX SET UP
        frame = new JFrame("Node");
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        // UI INFORMATION SETUP
        status.setText("Welcome to Nebula Node ");
        earningsToday.setText("Today's Earnings : USD " + totalEarn);
        totalComputeTime.setText(String.format("Total Compute Time : " + totalMinutes + " min(s) "));

        // INITIATE DOCKER
//        docker = new Docker(taskParamsMap, logArea);
        start.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (setup) {

                        apply.setEnabled(false);
                        if (!isRendering()) {

                            absoluteClearCache();
                            pingStatus = true;
                            pingServer();
                        } else if (isRendering()) {
                            pingStatus = false;
                        }
                    } else {
                        status.setText("Node is not ready to do work. 5 more minutes . . .");
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
        stop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (pingStatus) {
                        apply.setEnabled(false);
                        pingStatus = false;
                        if (isRendering()) {
                            status.setText("Node is stopping. Please wait . . . ");
                            selectiveClearCache();
                            stopRenders();
                        }
                        postReschedule("Node stopped.");
                        status.setText("Node has been stopped.");
                    } else {
                        status.setText("Node is not running.");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        quit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    int confirmed = JOptionPane.showConfirmDialog(null,
                            "Are you sure you want to stop making money? \n Your earnings this session : USD " + totalEarn +
                                    "\n Total Compute Time : " + totalMinutes + " min(s)", "Quitter, quitter!",
                            JOptionPane.YES_NO_OPTION);

                    if (confirmed == JOptionPane.YES_OPTION) {
                        if (isRendering()) {
//                            docker.dockerStop();
                            absoluteClearCache();
                            stopRenders();
                        }
                        postReschedule("Node stopped.");
                        System.exit(0);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        apply.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                double percentage = 0;
                if (cpuConfig.getValue() == 1) {
                    percentage = 0.25;
                } else if (cpuConfig.getValue() == 2) {
                    percentage = 0.5;
                } else if (cpuConfig.getValue() == 3) {
                    percentage = 0.75;
                } else if (cpuConfig.getValue() == 4) {
                    percentage = 1;
                }
                int cpu = (int) Math.ceil(percentage * (processor.getLogicalProcessorCount() - 1));

                try {
                    if (cpu != currentCpu) {
//                        status.setText("Setting CPU Usage to " + percentage * 100 + " %. Please wait.");
//                        modifyNodeCPUandMemory(cpu, 1024 * cpu);
//                        status.setText("CPU Settings applied. Click 'Start'");
                    } else {
//                        status.setText("CPU already set to " + percentage * 100 + " %");
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int confirmed = JOptionPane.showConfirmDialog(frame, "Are you sure you want to stop making money? " +
                                "\n Your earnings this session : USD" + totalEarn +
                                "\n Total Compute Time : " + totalMinutes + " min(s)", "Quitter, quitter!",
                        JOptionPane.YES_NO_OPTION);

                try {
                    if (confirmed == JOptionPane.YES_OPTION) {
                        if (isRendering()) {
                            absoluteClearCache();
                            stopRenders();
                        }
                        postReschedule("Node stopped.");
                        System.exit(0);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    public boolean startNode() throws Exception {
        boolean nodeStarted = false;

        log("--- YOUR WEEKLY PROFITS WILL BE PAID TO YOU IN FULL BY THE FOLLOWING MONDAY. HAPPY COMPUTING! ---");
        productVersionLabel.setText("Version : " + productVersion);
        createAltDatabase();
        checkForUpdates();
        absoluteClearCache();

        totalGhz = getCPU();
        gpu = checkForGPU();
//        currentCpu = getCurrentCPU();
//        cpuConfig.setValue(currentCpu);
        totalEarningPower = calculateEarningPower();
//        earningPower = round((totalGhz * perGhzHourUSD), 2);
        earningPowerLabel.setText("Your Earning Power (100%) : USD " + totalEarningPower + " / hr");

        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            ipAddress = socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (setupRenderApplication()) {
            nodeStarted = true;
        }

        log("[LOG] CPU is running fresh at " + totalGhz);
        status.setText("Node is ready. Punch 'Start' for the magic to begin.");     // TODO - EDITS MADE HERE FOR BATCH ARGUMENTS
        return nodeStarted;
    }

    public boolean setupRenderApplication() throws IOException, InterruptedException {
        boolean setup = false;
        boolean vraySetup;
        boolean blenderSetup;

        if (checkRenderApplication("vray")) {
            vraySetup = true;
        } else {
            vraySetup = installRenderApp("vray");
        }

        if (checkRenderApplication("blender")) {
            blenderSetup = true;
        } else {
            blenderSetup = installRenderApp("blender");
        }

        if (vraySetup && blenderSetup) {
            setup = true;
        } else {
            log("[ERROR] Failed to setup all required applications. Please contact support.");
        }

        return setup;
    }

    public boolean checkRenderApplication(String application) throws IOException, InterruptedException {
        boolean setup = false;

        if (application.contains("vray")) {
            File vrayDir = new File("C:\\Program Files\\Chaos Group\\V-Ray\\V-Ray for SketchUp\\extension\\vrayappsdk\\bin");

            if (Task.checkFile("vray.exe", vrayDir, "contain") != null) {
                setup = true;
            } else {
                vrayDir = new File(programFiles, "Chaos Group");
                if (Task.checkFile("vray.exe", vrayDir, "contain") != null) {
                    setup = true;
                }
            }
        } else if (application.contains("blender")) {
            File blenderDir = new File("C:\\Program Files\\Blender Foundation\\Blender 2.92");

            if (Task.checkFile("blender.exe", blenderDir, "contain") != null) {
                setup = true;
            } else {
                blenderDir = new File(programFiles, "Blender Foundation");
                if (Task.checkFile("blender.exe", blenderDir, "contain") != null) {
                    setup = true;
                }
            }
        }

        return setup;
    }

    public boolean installRenderApp(String application) throws IOException, InterruptedException {
        boolean installed = false;
        status.setText("Installing required applications . . . ");
        log("[LOG] Installing required applications . . . ");

        String blenderDbxURL = "https://www.dropbox.com/s/r4pg31xs8ap3ia0/blender-installer.msi?dl=1";
        if (application.contains("blender")) {

            File blenderInstaller = new File(taskCache, "blender-installer.msi");
            if (downloadFile(blenderDbxURL, blenderInstaller) && blenderInstaller != null) {

                ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/K", "msiexec", "/i", blenderInstaller.getAbsolutePath(), "/quiet", "/qb", "/norestart", "&&", "exit");
                Process process = processBuilder.start();
                while (process.isAlive()) {
                    if (checkRenderApplication(application)) {
                        installed = true;
                        break;
                    }
                }

            } else {
                log("[ERROR] Failed to download and install Blender. Please contact support.");
            }
        }
//        else if (application.contains("vray")) {
//            // nothing for now
//        }

        return installed;
    }

    public boolean isRendering() {
        boolean isRendering = false;

        if (Task.renderProcess != null && Task.renderProcess.isAlive()) {
            isRendering = true;
//        } else if (Task.renderProcess != null && !Task.renderProcess.isAlive()) {
//            log("[LOG] Vray Render Process exists, but is not Alive.");
////        } else if (docker.computeStatus()) {
////            isRendering = true;
        } else {
            log("[LOG] Node is not rendering at the moment.");
        }

        return isRendering;
    }

    public void stopRenders() throws IOException {
        Runtime.getRuntime().exec("taskkill /F /IM vray.exe");
        Runtime.getRuntime().exec("taskkill /F /IM blender.exe");

    }

    public void pingServer() throws IOException {
        final Timer pingTimer = new Timer();
        pingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    antiScreensaver();
                    if (pingStatus) {
                        if (!isRendering() && queue.size() == 0) {
                            status.setText("Requesting tasks from server...");
                            Task task = postGetTask();

                            if (task != null) {
                                log("[LOG] Working on " + taskParamsMap.get("subtaskID"));
                                status.setText("Working on " + taskParamsMap.get("subtaskID") + ". Please DO NOT turn off your PC.");

                                if (task.isTasksReady()) {

                                    boolean resultRendered = task.startRendering();
                                    if (resultRendered) {
                                        log("[LOG] " + taskParamsMap.get("subtaskID") + " completed. Returning result back to server.");
                                        status.setText(taskParamsMap.get("subtaskID") + " completed. Good work!");

                                        if (!postResults(task)) {
                                            if (!postResults(task)) {
                                                log("[LOG] Failed to return results to server.");
                                                postReschedule("Failed to return results to server.");
                                            }
                                        }
                                    } else {
                                        log("[LOG] Failed to render. ");
                                        postReschedule("Failed to render.");
                                    }
                                } else {
                                    log("[LOG] Tasks failed to setup. ");
                                    postReschedule("Failed task setup.");
                                }
                            } else {
                                status.setText("No tasks available. Waiting for tasks ...");
                                log("[LOG] No tasks available. Waiting for tasks ...");
                            }
                        } else {
                            status.setText("Still working on " + taskParamsMap.get("subtaskID") + ". Please DO NOT turn off your PC.");
                            log("[LOG] Still working on " + taskParamsMap.get("subtaskID"));
                        }
                    } else {
                        status.setText("Node stopped communicating with server.");

                        selectiveClearCache();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1 * 1 * 1000, 1 * 15 * 1000);

        Timer updateTimer = new Timer();
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    checkForUpdates();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1 * 1 * 1000, 1 * 900 * 1000); // in Milliseconds. 30 Mins = 900,000 Millis.
    }

    public Task postGetTask() throws IOException { // Sends nebulanode.Node Status to Server as request for Tasks. Should receive 2 Items - (a) Docker Blender . (b) Task data
        int httpStatus = 0;
        boolean tasks = false;
        Task task = null;

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            // Build entity for passing nebulanode.Node status and parameters.
            HttpEntity data = EntityBuilder.create()
                    .setContentEncoding("UTF-8")
                    .setContentType(ContentType.APPLICATION_FORM_URLENCODED)
                    .setParameters(new BasicNameValuePair("Node-Email", nodeEmail)
                            , new BasicNameValuePair("Device-Identity", deviceID)
                            , new BasicNameValuePair("IP-Address", ipAddress)
                            , new BasicNameValuePair("Queue", Integer.toString(queue.size()))
                            , new BasicNameValuePair("Version", productVersion))
                    .build();

            HttpUriRequest request = new HttpPost(schedulerServer);
            ((HttpPost) request).setEntity(data);
            CloseableHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            String subtaskParams = response.getFirstHeader("Subtask-Params").getValue();

            // Checks if a task has been received from nebula_server in the form of an HTTP Entity.
            if (entity != null && subtaskParams != null && !subtaskParams.equals("null")) {

                entity.getContent();
                taskParamsMap = extractParams(subtaskParams);

                // Adds the newly received task to queue to begin rendering.
                // However, if queue is full for some reason, it wastes no time and calls nebula_server for a Re-scheduling event.
                if (addToQueue(taskParamsMap.get("subtaskID"))) {
                    task = new Task(taskParamsMap, entity, taskCache, logArea, status);

                } else {
                    status.setText("Queue full.");
                    log("[LOG] Queue full. Returning task to server.");
                    postReschedule("Node queue full.");
                }
            } else {
                return null;
            }

            httpStatus = response.getStatusLine().getStatusCode();
            httpClient.close();
            response.close();
            log("[LOG] Executing request " + request.getRequestLine() + " | Status : " + httpStatus);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return task;
    }

    public void postReschedule(String failReason) throws IOException {

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpEntity data = EntityBuilder.create()                                                                    // Build entity to inform Server that this Node called STOP, and needs to re-schedule its subtask.
                    .setContentEncoding("UTF-8")                                                                        // Entity includes original Task Identity, Subtask Identity and TileScript
                    .setContentType(ContentType.APPLICATION_FORM_URLENCODED)
                    .setParameters(new BasicNameValuePair("Node-Email", nodeEmail)
                            , new BasicNameValuePair("Device-Identity", deviceID)
                            , new BasicNameValuePair("Task-Identity", taskParamsMap.get("taskID"))
                            , new BasicNameValuePair("Subtask-Identity", taskParamsMap.get("subtaskID"))
                            , new BasicNameValuePair("User-Email", taskParamsMap.get("userEmail"))
                            , new BasicNameValuePair("IP-Address", ipAddress)
                            , new BasicNameValuePair("Reason ", failReason))
                    .build();

            HttpUriRequest request = new HttpPost(rescheduleServer);
            ((HttpPost) request).setEntity(data);
            CloseableHttpResponse response = httpClient.execute(request);

            int status = response.getStatusLine().getStatusCode();
            log("[LOG] Executing request " + request.getRequestLine() + " | Status : " + status);
            httpClient.close();
            response.close();
            selectiveClearCache();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //  The extractJsonTaskParams is paramount to ensuring the right task information is passed to create the right tasks.
    //  The method will extract and define the 'application' parameter to decide what Key and Value are to be added into the taskParamsMap to be passed to the Task Class.
    private static LinkedHashMap<String, String> extractParams(String subtaskParams) {
        ArrayList<String> params = new ArrayList<>();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        String s = subtaskParams.replaceAll("[\"{}]", "");
        Scanner scanner = new Scanner(s);
        scanner.useDelimiter(",");
        int counter = 0;
        while (scanner.hasNext()) {
            params.add(counter, scanner.next());
            counter++;
        }

        // Scan the jsonParam String to identify what application and task is uploaded. Then add Key and Value of parameters to the map respectively.
        // New Application Task Types are to be added here.

        if (subtaskParams.contains("vray")) {

            map.put("renderfileName", params.get(0));
            map.put("tileScriptName", params.get(1));
            map.put("packedSkpName", params.get(2));
            map.put("taskID", params.get(3));
            map.put("application", params.get(4));
            map.put("userEmail", params.get(5));
            map.put("frameCategory", params.get(6));
            map.put("startFrame", params.get(7));
            map.put("endFrame", params.get(8));
            map.put("renderOutputType", params.get(9));
            map.put("subtaskID", params.get(10));
            map.put("renderFrame", params.get(11));
            map.put("frameCount", params.get(12));
            map.put("subtaskCount", params.get(13));
            map.put("renderfileURL", params.get(14));
            map.put("packedSkpURL", params.get(15));
            map.put("uploadfileName", params.get(16));
            map.put("userSubscription", params.get(17));
            map.put("userAllowance", params.get(18));
            map.put("computeRate", params.get(19));

        } else if (subtaskParams.contains("blender")) {

            map.put("renderfileName", params.get(0));
            map.put("tileScriptName", params.get(1));
            map.put("taskID", params.get(2));
            map.put("application", params.get(3));
            map.put("userEmail", params.get(4));
            map.put("frameCategory", params.get(5));
            map.put("startFrame", params.get(6));
            map.put("endFrame", params.get(7));
            map.put("renderOutputType", params.get(8));
            map.put("subtaskID", params.get(9));
            map.put("renderFrame", params.get(10));
            map.put("frameCount", params.get(11));
            map.put("subtaskCount", params.get(12));
            map.put("renderfileURL", params.get(13));
            map.put("uploadfileName", params.get(14));
            map.put("userSubscription", params.get(15));
            map.put("userAllowance", params.get(16));
            map.put("computeRate", params.get(17));
        }
        scanner.close();

        return map;
    }

//    public String getUpdateURL() throws IOException { // Retrieves information from server. (Works)
//        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
//            String updaterServlet = "https://nebula-server.herokuapp.com/update";
//
//            HttpUriRequest request = RequestBuilder
//                    .get().setUri(updaterServlet)
//                    .setHeader("IP-ADDRESS", ipAddress)
//                    .setHeader("NODE-EMAIL", nodeEmail)
//                    .setHeader("NODE-VERSION", productVersion)
//                    .build();
//
//            String url;
//            CloseableHttpResponse response = httpClient.execute(request);
//
//            url = response.getFirstHeader("Update-URL").getValue();
//            if (!url.equals("null") || url != null) {
//                return url;
//            }
//
//            httpClient.close();
//            response.close();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

    public void checkForUpdates() throws IOException {
        log("Checking for updates . . .");
        String updaterServlet = "https://nebula-server.herokuapp.com/update";

        File nodeUpdateBatch = new File(updates, "node-updater.bat");
        File nodeUpdaterExe = new File(updates, "node-updater.exe");
        File nodeUpdateConfig = new File(updates, "node-update-config.txt");

        try {
                CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                HttpUriRequest request = RequestBuilder
                        .get().setUri(updaterServlet)
                        .setHeader("IP-ADDRESS", ipAddress)
                        .setHeader("NODE-EMAIL", nodeEmail)
                        .setHeader("NODE-VERSION", productVersion)
                        .build();

                CloseableHttpResponse response = httpClient.execute(request);

                String latestVersion = response.getFirstHeader("Latest-Version").getValue();
                String nodeUpdateConfigURL = response.getFirstHeader("Update-URL").getValue();
                log("CHECK - latest : " + latestVersion + " | current : " + productVersion);


             httpClient.close();
                response.close();

                if (latestVersion != null) {
                    log("CHECK 2");
                    updated = latestVersion.equals(productVersion);

                    if (!updated && nodeUpdateConfigURL != null) {

                        downloadFile(nodeUpdateConfigURL, nodeUpdateConfig);
                        List<String> nodeConfig = readLinesFromFile(nodeUpdateConfig);
                        HashMap<String, String> updateDetails = extractUpdateDetails(nodeConfig);
                        updated = updateDetails.get("productVersion").equals(productVersion);

                        if (!updated) {
                            status.setText("New updates available. Downloading now . . . ");                    // TODO - Where to show updates being downloaded and does app need to be closed.

                    // CODE BELOW DISABLES AUTO-UPDATE DOWNLOAD & INSTALL
                    ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/K", "start", nodeUpdaterExe.getAbsolutePath());
                    Process process = processBuilder.start();

                    // CODE BELOW ENABLES AUTO-UPDATE DOWNLOAD & INSTALL
//                            File updateFile = new File(updates, "update-installer.exe");
//                            if (downloadFile(updateDetails.get("url"), updateFile)) {
//
//                                ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/K", "start", nodeUpdateBatch.getAbsolutePath(), updateDetails.get("productVersion"), updateFile.getAbsolutePath(), "/quiet", "/passive");
//                                Process process = processBuilder.start();
//                                System.exit(0);
//                            }
                        }
                    } else if (updated) {
                        log("[LOG] All updated. Product Version : " + productVersion);
                    } else if (nodeUpdateConfig == null) {
                        log("[LOG] Unable to retrieve node-update-config URL");
                    }
                } else {
                    log("[LOG] Server under maintenance. Can't update at this time.");
                }


//            if (nodeUpdateConfigURL != null) {
//
//                downloadFile(nodeUpdateConfigURL, nodeUpdateConfig);
//                List<String> nodeConfig = readLinesFromFile(nodeUpdateConfig);
//                HashMap<String, String> updateDetails = extractUpdateDetails(nodeConfig);
//                updated = updateDetails.get("productVersion").equals(productVersion);
//
//                if (!updated) {
//                    status.setText("New updates available. Downloading now . . . ");                    // TODO - Where to show updates being downloaded and does app need to be closed.
//
////                    ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/K", "start", nodeUpdaterExe.getAbsolutePath());
////                    Process process = processBuilder.start();
//
//                    File updateFile = new File(updates, "update-installer.exe");
//                    if (downloadFile(updateDetails.get("url"), updateFile)) {
//
//                        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/K", "start", nodeUpdateBatch.getAbsolutePath(), updateDetails.get("productVersion"), updateFile.getAbsolutePath(), "/quiet", "/passive");
//                        Process process = processBuilder.start();
//                        System.exit(0);
//                    }
//                } else {
//                    log("[LOG] All updated. Product Version : " + productVersion);
//                }
//            } else {
//                log("[LOG] Server under maintenance. Can't update at this time.");
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean downloadFile(String downloadURL, File outputFile) {
        boolean downloaded = false;
        try {
            URL download = new URL(downloadURL);
            ReadableByteChannel rbc = Channels.newChannel(download.openStream());
            FileOutputStream fileOut = new FileOutputStream(outputFile);
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);                  // TODO - Should replace all existing nodeUpdateConfig
            fileOut.flush();
            fileOut.close();
            rbc.close();

            downloaded = true;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return downloaded;
    }

    public HashMap<String, String> extractUpdateDetails(List<String> nodeConfig) {

        Iterator<String> iterator = nodeConfig.iterator();
        HashMap<String, String> updateDetails = new HashMap<>();

        while (iterator.hasNext()) {
            String line = iterator.next();

            if (line.contains("ProductVersion")) {
                String[] split = line.split(" = ");
                updateDetails.put("productVersion", split[1]);
            } else if (line.contains("URL")) {
                String[] split = line.split(" = ");
                updateDetails.put("url", split[1]);
            }
        }

        return updateDetails;
    }

    public static List<String> readLinesFromFile(File updateConfig) {
        List<String> lines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(updateConfig))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lines;
    }

//    public File writeCpuConfigFile(int cpuCores, int memory) throws IOException {
//        File vBoxManage = new File("C:\\Program Files\\Oracle\\VirtualBox\\VBoxManage.exe");
//
//        String cpuProc = String.format("\"" + vBoxManage.getAbsolutePath() + "\"" + " modifyvm default --cpus " + cpuCores);
//        String memProc = String.format("\"" + vBoxManage.getAbsolutePath() + "\"" + " modifyvm default --memory " + memory);
//
//        File cpuConfigFile = new File(nebulaData, "cpuconfig.bat");
//
//        PrintWriter fout = new PrintWriter(new FileWriter(cpuConfigFile));
//        fout.println("docker-machine stop");
//        fout.println(cpuProc);
//        fout.println(memProc);
//        fout.println("docker-machine start");
//        fout.flush();
//        fout.close();
//
//        return cpuConfigFile;
//    }

//    public void modifyNodeCPUandMemory(int cpuCores, int memory) throws IOException {
//        File cpuconfig = writeCpuConfigFile(cpuCores, memory);

//        log("[LOG] THIS FEATURE IS CURRENTLY DISABLED.");
//        System.out.println("[LOG] Reading lines from : " + cpuconfig.getName());
//        Process modifyCPU = Runtime.getRuntime().exec("\"" + cpuconfig.getAbsolutePath() + "\"");
//
//        String line;
//        InputStream inputStream = modifyCPU.getInputStream();
//        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
//        while ((line = bufferedReader.readLine()) != null) {
//            System.out.println("[VBOX] " + line);
//        }
//        getCurrentCPU();
//    }

    public void log(String message) {
        logArea.append(message + "\n");
        System.out.println(message);
    }

    public static void createAltDatabase() {
        if (OS.contains("WIN")) {

            appData = new File(System.getenv("APPDATA"));

            File temp = new File("E:\\temp");
            if (temp.exists() && temp.isDirectory()) {
                nebulaData = new File(appData, "Nebula");
                try {
                    File altNebulaData = new File(temp, "Nebula");
                    if (!altNebulaData.exists()) {
                        altNebulaData.mkdir();
                        FileUtils.copyDirectory(nebulaData, altNebulaData);
                    }
                    nebulaData = altNebulaData;

                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                appData = new File(System.getenv("APPDATA"));
                nebulaData = new File(appData, "Nebula");
            }
        } else {
            appData = new File(System.getProperty("user.home"));
            nebulaData = new File(appData, "Nebula");
        }

        taskCache = new File(nebulaData, "taskcache");
        updates = new File(nebulaData, "updates");
        results = new File(taskCache, "results");
    }

    public String getSource() {
        String nebulaDirPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("%20", "");

        return nebulaDirPath;
    }

    public boolean addToQueue(String subtaskID) throws IOException {
        boolean addedToQueue = false;

        if (queue.size() == 0) {
            addedToQueue = true;
            queue.add(subtaskID);
        }

        return addedToQueue;
    }

    public void printFilesInDir(File dir) throws UnsupportedEncodingException {
        File[] files = dir.listFiles();
        System.out.println("[LOG] Listing files in " + dir.getName());
        if (files.length > 0) {
            for (int i = 0; i < files.length; i++) {
                String filename = new String(files[i].getName().getBytes("UTF-8"));
                System.out.println("[LOG] " + dir.getName() + " | " + i + ". " + filename);
            }
        } else {
            System.out.println("[LOG] " + dir.getName() + " is empty.");
        }
    }

    private boolean unzip(File zipFile, File destDir) {
        boolean unzipped = false;
        FileInputStream fis;
        byte[] buffer = new byte[(int) zipFile.length()];
        try {
            fis = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(fis, StandardCharsets.UTF_8);
            ZipEntry ze = zis.getNextEntry();
            int idx = 1;
            while (ze != null) {

                String fileName = ze.getName();
                idx++;
                File newFile = new File(destDir, fileName);
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int length;
                while ((length = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
            fis.close();

            unzipped = true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return unzipped;
    }

    public boolean postResults(Task task) throws IOException, InterruptedException {                 // TODO - Currently scans the TaskCache folder to retrieve userEmail (and other metaData) -- THIS IS DEPENDENT ON THERE BEING ONLY ONE JOB IN QUEUE AT A TIME.
        boolean resultsReceived = false;

        File renderResult = Task.getResult(results,
                taskParamsMap.get("subtaskID"));

        if (renderResult == null) {
            printFilesInDir(results);
        } else {

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                String cost = String.valueOf(calculateCost(task));
                String resultParams = task.buildResultParamsString(cost, nodeEmail, deviceID, ipAddress);

                FileBody fileBody = new FileBody(renderResult);
                HttpEntity data = MultipartEntityBuilder.create()
                        .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                        .addPart("Render-Result", fileBody)
                        .addTextBody("Result-Params", resultParams)
                        .build();

                HttpUriRequest request = new HttpPost(resultsServer);                                       // TODO - EDITS MADE HERE
                ((HttpPost) request).setEntity(data);
                try (CloseableHttpResponse response = httpClient.execute(request)) {

                    resultsReceived = Boolean.parseBoolean(response.getFirstHeader("Result-Received").getValue());

                    int status = response.getStatusLine().getStatusCode();
                    log("[LOG] Executing request " + request.getRequestLine() + " | STATUS : " + status);

                    httpClient.close();
                    response.close();

                    if (resultsReceived) {
                        renderResult.delete();
//                        printFilesInDir(results);
                        selectiveClearCache();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return resultsReceived;
    }

    public static void clearDir(File file, String filter1, String filter2, String filter3, String filter4) {

        System.out.println("CHECKPOINT - CLEAR_DIR | filter1 : " + filter1 + " | filter2 : " + filter2 + " | filter3 : " + filter3 + " | filter4 : " + filter4);

        if (!file.isDirectory()
                && !file.getName().contains(filter1)
                && !file.getName().contains(filter2)
                && !file.getName().contains(filter3)
                && !file.getName().contains(filter4)) {

            file.delete();
        } else if (file.isDirectory()) {
            File[] subfiles = file.listFiles();

            if (subfiles.length > 0) {
                for (int j = 0; j < subfiles.length; j++) {
                    File subfile = subfiles[j].getAbsoluteFile();
                    clearDir(subfile, filter1, filter2, filter3, filter4);
                }
            }
            if (!file.getName().contains("results")) {
                file.delete();
            }
        }
    }

    public void selectiveClearCache() {
        log("[LOG] (SELECTIVE) Clearing cache . . .");
        queue.clear();

        if (taskCache.exists()) {

            if (taskParamsMap.get("application").contains("vray")) {

                File files[] = taskCache.listFiles();
                for (int i = 0; i < files.length; i++) {
                    File file = files[i].getAbsoluteFile();
                    clearDir(file,
                            ".exr",
                            taskParamsMap.get("renderfileName"),
                            taskParamsMap.get("packedSkpName"),
                            taskParamsMap.get("taskID") + ".txt");
                }
            } else if (taskParamsMap.get("application").contains("blender")) {

                File files[] = taskCache.listFiles();
                for (int i = 0; i < files.length; i++) {
                    File file = files[i].getAbsoluteFile();

                    clearDir(file,
                            ".exr",
                            taskParamsMap.get("renderfileName"),
                            "null",
                            "null");
                }
            }
            log("[LOG] Cache cleared.");
        }
    }

    public void absoluteClearCache() {
        log("[LOG] (ABSOLUTE) Clearing cache . . .");
        queue.clear();

        if (taskCache.exists()) {

            File files[] = taskCache.listFiles();
            for (int i = 0; i < files.length; i++) {
                File file = files[i].getAbsoluteFile();

                clearDir(file,
                        ".exr",
                        "null",
                        "null",
                        "null");
            }

            log("[LOG] Cache cleared.");
        }
    }

    public String checkForGPU() {
        String gpu = null;

        try {
            File dxdiagLog = new File(taskCache, "dxdiag_log.txt");
            // Use "dxdiag /t" variant to redirect output to a given file
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "dxdiag", "/t", dxdiagLog.getAbsolutePath());
            log("[LOG] Checking for GPU...");
            Process p = pb.start();
            p.waitFor();

            BufferedReader br = new BufferedReader(new FileReader(dxdiagLog.getAbsolutePath()));
            String line;
            System.out.println(String.format("-- Printing GPU Info --", dxdiagLog.getAbsolutePath()));
            while ((line = br.readLine()) != null) {
                log("[LOG] DX-DIAG - ");

                if (line.trim().startsWith("Card name:")) {

                    String trimmedLine = line.trim();
                    if (trimmedLine.toLowerCase().contains("nvidia")) {
                        gpu = "nvidia";
                        log("DETECTED GPU : " + trimmedLine);

                    } else if (trimmedLine.toLowerCase().contains("amd")) {
                        gpu = "amd";
                        log("DETECTED GPU : " + trimmedLine);

                    }
                }
            }
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }

        return gpu;
    }

    public static BigDecimal calculateEarningPower() {
        BigDecimal totalEarningPower = bdRound(BigDecimal.valueOf(perGpu), 2);
        cpuEarningPower = bdRound(BigDecimal.valueOf(totalGhz * perGhzHourUSD), 2);

        if (gpu != null) {
            totalEarningPower = BigDecimal.valueOf(perGpu);
//            totalEarningPower = bdRound(cpuEarningPower.add(BigDecimal.valueOf(perGpu)), 2);
        } else {
            totalEarningPower = cpuEarningPower;
        }

        return totalEarningPower;
    }

    public String calculateCost(Task task) {

        double computeSeconds = task.getComputeSeconds();
        refreshTotalComputeTime(totalMinutes, round(computeSeconds / 60, 2));
        earningsToday.setText("Today's Earnings : USD " + totalEarn);

        BigDecimal computeHours = bdRound(BigDecimal.valueOf((computeSeconds / 60) / 60), 4); // Should capture compute times of as low as 1 second. 1 second = 0.000278 Hour(s).
//        BigDecimal computeMinutes = bdRound(BigDecimal.valueOf(computeSeconds / 60), 6);
//        BigDecimal perGhzMinute = bdRound(totalEarningPower.divide(BigDecimal.valueOf(60)), 6);
        BigDecimal cost;
        String totalCost = null;

        try {
            cost = bdRound(totalEarningPower.multiply(computeHours), 6); // Should capture cost of compute times of as low as 1 second.
            totalCost = String.valueOf(cost);

            if (totalEarn.compareTo(BigDecimal.valueOf(0.01)) == 0 || totalEarn.compareTo(BigDecimal.valueOf(0.01)) == 1) {
                totalEarn = bdRound(totalEarn.add(cost), 2);
            } else {
                totalEarn = bdRound(totalEarn.add(cost), 6);
            }

            log("[LOG] NEW EARNINGS : " + cost);
            log("[LOG] Total Earn : " + totalEarn);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return totalCost;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static BigDecimal bdRound(BigDecimal value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = value;
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd;
    }

    public double getCPU() {
        status.setText("Getting CPU Info . . .");
        DecimalFormat ghzFormat = new DecimalFormat("#.#");
        deviceID = systemInfo.getHardware().getComputerSystem().getSerialNumber();
        totalLogicCores = processor.getLogicalProcessorCount();
        totalPhysCores = processor.getPhysicalProcessorCount();
        long cpuHertz = processor.getMaxFreq();
        double cpuGhz = Double.parseDouble(ghzFormat.format(cpuHertz / 1000000000.0));
        double totalGhz = cpuGhz * totalLogicCores;
        log("[LOG] Total CPU GHZ : " + totalGhz);

        return totalGhz;
    }

    public static File getNebulaData() {
        return nebulaData;
    }

//    public int getCurrentCPU() throws IOException {
//        int cpu = 0;
//        Process dockerInfo = Runtime.getRuntime().exec("docker info");
//
//        String line;
//        InputStream inputStream = dockerInfo.getInputStream();
//        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
//        while ((line = bufferedReader.readLine()) != null) {
//            System.out.println("[VBOX] " + line);
//
//            for (int i = 1; i < totalLogicCores; i++) {
//                if (line.contains("CPUs : " + i)) {
//                    cpu = i;
//                }
//            }
//        }
//        int cpuPercentage = calculatePercentage(cpu);
////        double cpuPercentage = Math.ceil((cpu / (totalLogicCores - 1)) * 100);
//        System.out.println("[LOG] Logic Cores : " + totalLogicCores + " | CPU : " + cpu + " | CPU % : " + cpuPercentage);
//        this.cpuPercentage.setText("CPU : " + cpuPercentage + " %");
//        return cpu;
//    }

//    public int calculatePercentage(int cpu) {
//
//        float percentageF = (((float) cpu) / (totalLogicCores - 1)) * 100;
//        int percentage = (int) percentageF;
//
//        return percentage;
//    }

    public void antiScreensaver() throws AWTException {
        Robot robot = new Robot();
        Point pObj = MouseInfo.getPointerInfo().getLocation();
        robot.mouseMove(pObj.x - 1, pObj.y - 1);
        robot.mouseMove(pObj.x + 1, pObj.y + 1);
    }

    public double refreshTotalComputeTime(double totalMinutes, double minutes) {
        totalMinutes += minutes;
        totalMinutes = round(totalMinutes, 2);
        totalComputeTime.setText(String.format("Total Compute Time : " + timeFormat.format(totalMinutes) + " s "));

        return totalMinutes;
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
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(6, 6, new Insets(10, 10, 10, 10), 50, 20));
        panel1.setBackground(new Color(-15528407));
        panel1.setEnabled(false);
        panel1.setMinimumSize(new Dimension(1000, 400));
        panel1.setPreferredSize(new Dimension(1000, 400));
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setVerticalScrollBarPolicy(22);
        panel1.add(scrollPane1, new GridConstraints(5, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        logArea = new JTextArea();
        scrollPane1.setViewportView(logArea);
        start = new JButton();
        start.setBackground(new Color(-1));
        start.setForeground(new Color(-15124111));
        start.setText("Start");
        panel1.add(start, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, 30), null, 1, false));
        stop = new JButton();
        stop.setBackground(new Color(-1));
        stop.setForeground(new Color(-15124111));
        stop.setHorizontalTextPosition(0);
        stop.setText("Stop");
        panel1.add(stop, new GridConstraints(2, 1, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, 30), null, 0, false));
        quit = new JButton();
        quit.setBackground(new Color(-1));
        quit.setForeground(new Color(-15124111));
        quit.setText("Quit");
        panel1.add(quit, new GridConstraints(2, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, 30), null, 0, false));
        status = new JLabel();
        status.setForeground(new Color(-1));
        status.setText("");
        panel1.add(status, new GridConstraints(0, 1, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, -1), null, 0, false));
        earningsToday = new JLabel();
        earningsToday.setForeground(new Color(-4483784));
        earningsToday.setText("");
        panel1.add(earningsToday, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        totalComputeTime = new JLabel();
        totalComputeTime.setForeground(new Color(-7865601));
        totalComputeTime.setText("");
        panel1.add(totalComputeTime, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        earningPowerLabel = new JLabel();
        earningPowerLabel.setForeground(new Color(-1));
        earningPowerLabel.setText("");
        panel1.add(earningPowerLabel, new GridConstraints(1, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        productVersionLabel = new JLabel();
        productVersionLabel.setForeground(new Color(-1));
        productVersionLabel.setText("");
        panel1.add(productVersionLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        core1 = new JLabel();
        core1.setForeground(new Color(-1));
        core1.setText("25%");
        panel1.add(core1, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        core4 = new JLabel();
        core4.setForeground(new Color(-1));
        core4.setText("100%");
        panel1.add(core4, new GridConstraints(4, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        core2 = new JLabel();
        core2.setForeground(new Color(-1));
        core2.setText("50%");
        panel1.add(core2, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        core3 = new JLabel();
        core3.setForeground(new Color(-1));
        core3.setText("75%");
        panel1.add(core3, new GridConstraints(4, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        apply = new JButton();
        apply.setBackground(new Color(-1));
        apply.setForeground(new Color(-15124111));
        apply.setText("Apply CPU");
        panel1.add(apply, new GridConstraints(3, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, 30), null, 0, false));
        cpuPercentage = new JLabel();
        cpuPercentage.setForeground(new Color(-1));
        cpuPercentage.setText("CPU % :");
        panel1.add(cpuPercentage, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cpuConfig = new JSlider();
        cpuConfig.setBackground(new Color(-15528403));
        cpuConfig.setForeground(new Color(-1));
        cpuConfig.setMaximum(4);
        cpuConfig.setMinimum(1);
        cpuConfig.setMinorTickSpacing(1);
        cpuConfig.setPaintLabels(false);
        cpuConfig.setPaintTicks(true);
        cpuConfig.setPaintTrack(true);
        cpuConfig.setSnapToTicks(true);
        cpuConfig.setToolTipText("");
        cpuConfig.setValue(1);
        cpuConfig.setValueIsAdjusting(true);
        panel1.add(cpuConfig, new GridConstraints(3, 1, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}

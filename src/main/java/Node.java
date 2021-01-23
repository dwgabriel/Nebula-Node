import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PruneType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.HTTP;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import sun.misc.IOUtils;

import javax.swing.*;
import javax.swing.text.html.parser.Entity;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Node {

    SystemInfo systemInfo = new SystemInfo();
    CentralProcessor processor = systemInfo.getHardware().getProcessor();
    private String schedulerServer = "https://nebula-server.herokuapp.com/scheduler";
    private String resultsServer = "https://nebula-server.herokuapp.com/complete";
    private String rescheduleServer = "https://nebula-server.herokuapp.com/reschedule";
    private String updateServer = "https://nebula-server.herokuapp.com/update";
    private static String userHome = System.getProperty("user.home");
    private static String nodeDir = System.getProperty("user.dir");
    private static String OS = (System.getProperty("os.name")).toUpperCase();
    private static DefaultDockerClientConfig config;
    private static DockerClient dockerClient;
    private static File appData;
    private static File nebulaData;
    private static File taskCache;
    private static File updates;
    private static File results;
    private static String subtaskParams;
    private static String nodeEmail;
    private static String deviceID;
    //    private static String userEmail;                                                                                           // NOT THE SAME AS nodeEmail. This is Demand User's identity, not Supply User's.
//    private static String subtaskID;
//    private static String taskID;
//    private static String application;
//    private static String startFrame;
//    private static String endFrame;
//    private static String frameCount;
//    private static String renderFrame;
//    private static String renderOutputType;
//    private static String tileScriptName;
//    private static String originalTaskFileName;
//    private static String frameCategory;
//    private static String subtaskCount;
//    private static String blendfileURL;
    private static String ipAddress;
    //    private static String nodeUpdateConfigURL;
    private static String score = "95";
    private static boolean setup = false;
    private static boolean logged = false;
    private static boolean pingStatus = false;
    //    private static final double perGhzHourMYR = 0.016;
    private static final double perGhzHourUSD = 0.004;
    private static double totalGhz;
    private static double earningPower;
    private static BigDecimal totalEarn = BigDecimal.valueOf(0);
    private static String productVersion = "Version = 1.0.23";                                      // TODO - ALWAYS UPDATE THIS BEFORE PUSHING UPDATES
    private static boolean updated = false;
    private static boolean newTasks = false;
    //    private static int totalHours = 0;
    private static double totalMinutes = 0;
    private static int failureCounter = 0;
    private static int currentCpu;
    private static int totalPhysCores;
    private static int totalLogicCores;
    private static DecimalFormat timeFormat = new DecimalFormat("#.##");
    Deque<String> queue = new LinkedList<>();
    private static DecimalFormat costFormat = new DecimalFormat("##.##");
    private static LinkedHashMap<String, String> taskParamsMap = new LinkedHashMap<>();

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

    public static void main(String[] args) throws Exception {

        Login login = new Login(args); // TODO - EDITS MADE HERE FOR BATCH ARGUMENTS

        while (!logged) {
            System.out.println("Running . . .");
            if (args.length > 0 && args[2].equals("login")) {
                System.out.println("Batch");
                login.getLoginButton().doClick();
                logged = login.getLoggedStatus();
            } else {
                logged = login.run();
            }
            if (logged) {
                nodeEmail = login.getUsername();
                System.out.println("Node Email : " + nodeEmail);
                final Node node = new Node();
                setup = node.startNode(node);             // TODO - EDITS MADE HERE FOR BATCH ARGUMENTS

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
                        System.out.println("Clicked. No. : " + index);
                        index++;
                        break;
                    }
                }
                break;
            } else {
                System.out.println("Login unsuccessful.");
            }
            break;
        }
    }

    public Node() {
        frame = new JFrame("Node");
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        status.setText("Welcome to Nebula Node ");
        earningsToday.setText("Today's Earnings : USD " + totalEarn);
//        totalComputeTime.setText("Total Compute Time : " + totalHours + " hr " + totalMinutes + " min");
        totalComputeTime.setText(String.format("Total Compute Time : " + totalMinutes + " min(s) "));
        start.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                status.setText("Node is running . . . ");
                try {
                    if (setup) {
                        apply.setEnabled(false);
                        if (!computeStatus().equals("up")) {
                            pingStatus = true;
                            pingServer();
                        } else if (computeStatus().equals("up")) {
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
                if (pingStatus) {
                    try {
                        apply.setEnabled(true);
                        pingStatus = false;
                        if (computeStatus().equals("up")) {
                            status.setText("Node is stopping. Please wait . . . ");
                            postReschedule();
                            dockerStop();
                        }
                        status.setText("Node has been stopped.");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    status.setText("Node is not running.");
                }
            }
        });
        quit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirmed = JOptionPane.showConfirmDialog(null,
                        "Are you sure you want to stop making money? \n Your earnings this session : USD " + totalEarn +
                                "\n Total Compute Time : " + totalMinutes + " min(s)", "Quitter, quitter!",
                        JOptionPane.YES_NO_OPTION);

                if (confirmed == JOptionPane.YES_OPTION) {
                    if (computeStatus().equals("up")) {
                        try {
                            postReschedule();
                            dockerStop();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    System.exit(0);

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
                        status.setText("Setting CPU Usage to " + percentage * 100 + " %. Please wait.");
                        modifyNodeCPUandMemory(cpu, 1024 * cpu);
                        status.setText("CPU Settings applied. Click 'Start'");
                    } else {
                        status.setText("CPU already set to " + percentage * 100 + " %");
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

                if (confirmed == JOptionPane.YES_OPTION) {
                    if (computeStatus().equals("up")) {
                        try {
                            postReschedule();
                            dockerStop();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                    System.exit(0);
                }
            }
        });
    }

    public void pingServer() throws IOException {
        status.setText("Node is Running . . . ");

        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (pingStatus) {
                    try {
                        status.setText("Ping-ing server . . . ");
                        antiScreensaver();
                        postResults();

                        if (computeStatus().equals("up")) {
                            status.setText("Node is now rendering for " + taskParamsMap.get("subtaskID") + ". Please DO NOT turn off your computer");
                            log("Node is rendering for task : " + taskParamsMap.get("subtaskID"));
//
//                        } else if (!computeStatus().equals("up")) {
//
//                            if (queue.size() < 1 && !posting) {                                                                 // TODO - BOOLEAN POSTING ******************************
//                                log("Running. Fetching jobs from Server...");
//                                log("Queue size : " + queue.size());
//                                if (postStatus()) {
//                                    if (checkForApplication(taskParamsMap.get("application"))) {
//                                        if (compute()) {
//                                            status.setText("Your PC is now working on " + taskParamsMap.get("subtaskID") + ". Please DO NOT turn off your computer");
//                                        } else {
//                                            log("[ERROR] Node failed to compute tasks with application.");
//                                            status.setText("Computing failed. Returning task to server . . . ");
//                                            postReschedule();
//                                        }
//
//                                    } else {
//                                        log("[ERROR] Node failed to pull application.");
//                                        status.setText("Computing failed. Returning task to server . . . ");
//                                        postReschedule();
//                                    }
//
//                                } else {
//                                    status.setText("No tasks for now. Waiting for tasks . . .");
//
//                                    if (queue.size() > 0) {
//                                        status.setText("Returning task to server . . .");
//                                        postReschedule();
//                                        log("RE_SCHEDULED. | Task retrieval failed. Returned task back to server.");
//                                    } else {
//                                        status.setText("No tasks for now. Waiting for tasks . . .");
//                                        log("TASK QUEUE CLEAR. | There are no tasks to compute at this time.");
//                                    }
//                                }
//
//                            } else if (queue.size() >= 1) {
//                                log("CHECK | Queue is full. Queue size : " + queue.size());
//
//                                if (computeStatus().equals("exited")) {
//                                    postReschedule();
//                                }
//                            }


                        } else if (!computeStatus().equals("up") && queue.size() < 1) {
                            log("Running. Fetching jobs from Server...");
                            log("Queue size : " + queue.size());

                            if (postStatus()) {
                                if (checkForApplication(taskParamsMap.get("application"))) {
                                    if (compute()) {
                                        status.setText("Your PC is now working on " + taskParamsMap.get("subtaskID") + ". Please DO NOT turn off your computer");
                                    } else {
                                        log("[ERROR] Node failed to compute tasks with application.");
                                        status.setText("Computing failed. Returning task to server . . . ");
                                        postReschedule();
                                    }

                                } else {
                                    log("[ERROR] Node failed to pull application.");
                                    status.setText("Computing failed. Returning task to server . . . ");
                                    postReschedule();
                                }

                            } else {
                                status.setText("No tasks for now. Waiting for tasks . . .");
                                postReschedule();
                            }
                        } else {
                            log("NOTHING IS HAPPENING. CHECK AGAIN.");
                            log("Compute Status : " + computeStatus());
                            log("Queue Size : " + queue.size());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    log("Ping to server stopped.");
                    clearTaskCache();
                    timer.cancel();
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
        }, 1 * 1 * 1000, 1 * 300 * 1000);
    }

    public boolean postStatus() throws IOException { // Sends nebulanode.Node Status to Server as request for Tasks. Should receive 2 Items - (a) Docker Blender . (b) Task data
        CloseableHttpClient httpClient = HttpClients.createDefault();
        int httpStatus = 0;
        boolean tasks = false;

        try {
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

//            log("POST STATUS CHECK | ENTITY LENGTH : " + entity.getContentLength());
            log("CHECK | Response Headers Size : " + response.getAllHeaders().length);
            String taskExist = response.getFirstHeader("Task-Exist").getValue();
            log("TASK_EXIST : " + taskExist);

            if (entity != null && taskExist.equals("1")) { // todo - When there are no tasks, headers with errors are still sent and this isn't a good way to catch requests with no tasks.
                entity.getContent();
                subtaskParams = response.getFirstHeader("Subtask-Params").getValue();
                taskParamsMap = extractParams(subtaskParams);

                if (checkQueue(taskParamsMap.get("subtaskID"))) {
                    tasks = setupTaskFiles(entity);
                } else {
                    System.out.println("[ERROR] " + taskParamsMap.get("subtaskID") + " already exists in queue.");
                }
            } else {
                log("[SERVER] There are no tasks to compute at this time.");
                tasks = false;
            }

            httpStatus = response.getStatusLine().getStatusCode();
            log("Executing request " + request.getRequestLine());
            log("Status Code for POST : " + httpStatus);

        } catch (Exception e) {
            e.printStackTrace();
            log("Exception : " + e.getMessage());
        } finally {
            log("---------- End of Request ----------");
            httpClient.close();
            return tasks;
        }
    }

    public String buildResultParamsString(double computeSeconds, double computeMinutes, String cost) {
        StringBuilder resultParams = new StringBuilder(nodeEmail +
                "," + deviceID +
                "," + ipAddress +
                "," + taskParamsMap.get("taskID") +
                "," + taskParamsMap.get("subtaskID") +
                "," + taskParamsMap.get("userEmail") +
                "," + computeSeconds +
                "," + computeMinutes +
                "," + cost +
                "," + taskParamsMap.get("subtaskCount") +
                "," + taskParamsMap.get("frameCount") +
                "," + taskParamsMap.get("frameCategory") +
                "," + taskParamsMap.get("application") +
                "," + taskParamsMap.get("taskFileName") +
                "," + taskParamsMap.get("renderOutputType") +
                "," + taskParamsMap.get("renderFrame"));

        return resultParams.toString();
    }

    public void postReschedule() throws IOException {

        CloseableHttpClient httpClient = HttpClients.createDefault();

        if (queue.size() > 0) {

            log("RE-SCHEDULING | RETURNING TASK " + taskParamsMap.get("subtaskID") + " TO SERVER . . . ");

            try {
                HttpEntity data = EntityBuilder.create()                                                                    // Build entity to inform Server that this Node called STOP, and needs to re-schedule its subtask.
                        .setContentEncoding("UTF-8")                                                                        // Entity includes original Task Identity, Subtask Identity and TileScript
                        .setContentType(ContentType.APPLICATION_FORM_URLENCODED)
                        .setParameters(new BasicNameValuePair("Node-Email", nodeEmail)
                                , new BasicNameValuePair("Device-Identity", deviceID)
                                , new BasicNameValuePair("IP-Address", ipAddress)
                                , new BasicNameValuePair("Task-Identity", taskParamsMap.get("taskID"))
                                , new BasicNameValuePair("Subtask-Identity", taskParamsMap.get("subtaskID")))
//                                , new BasicNameValuePair("Tile-Script", taskParamsMap.get("tileScriptName"))) // todo - Re-scheduling should not be reliant on Application specific details. Only SubtaskID.
                        .build();                       // todo - CHECK IF EDGE CASE OF RE-SCHEDULING AFTER FINAL SUBTASK IS SCHEDULED IS POSSIBLE - (DO TASK FILES STILL EXIST OR ARE THEY ALREADY DELETED)

                HttpUriRequest request = new HttpPost(rescheduleServer);
                ((HttpPost) request).setEntity(data);
                CloseableHttpResponse response = httpClient.execute(request);

                int status = response.getStatusLine().getStatusCode();
                log("Executing request " + request.getRequestLine());
                log("Status Code for POST : " + status);
            } catch (Exception e) {
                e.printStackTrace();
                log("Exception : " + e.getMessage());
            } finally {
                log("---------- End of Request ----------");
                httpClient.close();
            }
        } else {
            log("Nothing to re-schedule.");

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
        if (subtaskParams.contains("blender")) {
            map.put("taskFileName", params.get(0));
            map.put("tileScriptName", params.get(1));
            map.put("taskID", params.get(2));
            map.put("application", params.get(3));
            map.put("userEmail", params.get(4));
            map.put("shareLink", params.get(5));
            map.put("frameCategory", params.get(6));
            map.put("startFrame", params.get(7));
            map.put("endFrame", params.get(8));
            map.put("renderOutputType", params.get(9));
            map.put("subtaskID", params.get(10));
            map.put("renderFrame", params.get(11));
            map.put("frameCount", params.get(12));
            map.put("subtaskCount", params.get(13));
        }
        // else if (subtaskParams.contains("insert application")) {
        // map.put("param", params.get(n));

        System.out.println("UPLOAD PARAMS : ");
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry pair = (Map.Entry) iterator.next();
            System.out.println(pair.getKey() + " : " + pair.getValue());
        }

        scanner.close();

        return map;
    }


    public String getUpdateURL() throws IOException { // Retrieves information from server. (Works)
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String updaterServlet = "https://nebula-server.herokuapp.com/update";

        HttpUriRequest request = RequestBuilder
                .get().setUri(updaterServlet)
                .build();

        String url;
        CloseableHttpResponse response = httpClient.execute(request);
        try {
            url = response.getFirstHeader("Update-URL").getValue();
            System.out.println("UPDATE URL : " + url);
            if (url.equals("null")) {
                return null;
            } else {
                return url;
            }

        } finally {
            response.close();
        }
    }

    public boolean checkForApplication(String application) throws InterruptedException {
        boolean applicationReady = false;

        List<Image> images = dockerClient.listImagesCmd().exec();
        if (!images.toString().contains(taskParamsMap.get("application"))) {    // todo - hardcoded for Blender, must be changed to be adaptive for all applications
            log("Required App not found. Node is downloading app . . .");
            applicationReady = pullImage(application);
        }

        return applicationReady;
    }

    public boolean pullImage(String application) throws InterruptedException {
        boolean imagePulled = false;

        if (application.equals("blenderCycles")) {
            imagePulled = pullBlender();
        }
        // else if (*insert other application*) {
        // pullImage of respective applications
        //}

        return imagePulled;
    }

    public void checkForUpdates() throws IOException {
        File nodeUpdater = new File(updates, "node-updater.exe");
        File nodeUpdateConfig = new File(updates.getAbsolutePath(), "node-update-config.txt");
        String nodeUpdateConfigURL = getUpdateURL();

        try {
//            log("NODE DIR CHECK | Node Dir : " + nodeDir);
            URL download = new URL(nodeUpdateConfigURL);
            System.out.println("DOWNLOADING CONFIG FILE : " + download.openStream());
            ReadableByteChannel rbc = Channels.newChannel(download.openStream());
            FileOutputStream fileOut = new FileOutputStream(nodeUpdateConfig);
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);                  // TODO - Should replace all existing nodeUpdateConfig
            fileOut.flush();
            fileOut.close();
            rbc.close();


            List<String> nodeConfig = readLinesFromFile(nodeUpdateConfig);
            for (int i = 0; i < nodeConfig.size(); i++) {
                if (nodeConfig.get(i).equals(productVersion)) {
                    updated = true;
                }
            }
            if (!updated) {
                status.setText("New updates available. Downloading now . . . ");                    // TODO - Where to show updates being downloaded and does app need to be closed.
                log("New updates available. Downloading now . . . ");
                Runtime run = Runtime.getRuntime();
                run.exec(nodeUpdater.getAbsolutePath());
                System.exit(0);
            } else {
                log(productVersion);
                log("All updated.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> readLinesFromFile(File updateConfig) {
        System.out.println("CHECK | Reading Lines from " + updateConfig + " | Path : " + updateConfig.getAbsolutePath());
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

    public boolean startNode(Node node) throws Exception {
        log("--- YOUR WEEKLY PROFITS WILL BE PAID TO YOU IN FULL BY THE FOLLOWING MONDAY. HAPPY COMPUTING! ---");
        productVersionLabel.setText(productVersion);
        status.setText("Checking for updates . . .");
        createDatabase();
        checkForUpdates();
        clearTaskCache();

        log("Getting CPU Information . . .");
        totalGhz = getCPU();
        currentCpu = getCurrentCPU();
        cpuConfig.setValue(currentCpu);
        earningPower = round((totalGhz * perGhzHourUSD), 2);
        earningPowerLabel.setText("Your Earning Power (100%) : USD " + earningPower + " / hr");

        log("Getting IP Address . . . ");
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            ipAddress = socket.getLocalAddress().getHostAddress();
            log("IP Address : " + ipAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log("CPU is running fresh at " + totalGhz);
        if (setupDocker()) {
//            modifyNodeCPUandMemory(totalLogicCores - 1, 4096);
            status.setText("Node is ready. Punch 'Start' for the magic to begin.");     // TODO - EDITS MADE HERE FOR BATCH ARGUMENTS
            return true;
        }
        return false;
    }

    public boolean setupTaskFiles(HttpEntity entity) throws IOException {
        boolean tasks = false;

        // Download TaskFiles through GDrive/DBox share links.
        File taskFile = downloadBlendFileFromURL(taskParamsMap.get("shareLink"), taskParamsMap.get("taskID"), taskCache);
        System.out.println("DOWNLOAD CHECK | Renderfile : " + taskFile.getName() + " | Size : " + taskFile.length() + " | Location : " + taskFile.getAbsolutePath());

        // Download TaskFiles from Server to Node
        File taskPackage = new File(taskCache, taskParamsMap.get("taskID").concat(".zip"));
        ReadableByteChannel rbc = Channels.newChannel(entity.getContent());
        FileOutputStream fos = new FileOutputStream(taskPackage);
        log("Downloading Task File from Server . . . ");
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fos.flush();
        fos.close();

        // Unzip downloaded taskFiles to Node taskCache Dir.
        if (unzip(taskPackage, taskCache) && taskFile.length() > 0) {
            List<File> taskCacheFileList = listFilesInDir(taskCache);

            if (!taskCacheFileList.isEmpty()) { // TODO - Hard-coded for blender. Needs more adaptive method to ensure correct files as per application
                tasks = checkTaskFiles(listFilesInDir(taskCache), taskParamsMap.get("taskFileName"), taskParamsMap.get("tileScriptName"));

                log("POST_STATUS_CHECK | tasks boolean : " + tasks);
            } else {
                log("ERROR | Task Files inconsistent. Returning task to server.");
                postReschedule();
            }
        } else {
            log("ERROR | Task failed to unzip or Renderfile not downloaded correctly. Renderfile Name : " + taskFile.getName() + " | Size : " + taskFile.length());
        }

        return tasks;
    }

    public boolean setupDockerMachine() throws IOException {
        String line;
        System.out.println("Setting Node CPUs . . . ");

        Process dockerMachineProcess = Runtime.getRuntime().exec("C:\\Program Files\\Docker Toolbox\\docker-start.cmd");
        InputStream stdin = dockerMachineProcess.getInputStream();
        BufferedReader brCleanUp = new BufferedReader(new InputStreamReader(stdin));
        while ((line = brCleanUp.readLine()) != null) {
            log("[Stdout] " + line);
        }
        if (dockerMachineProcess.exitValue() == 0) {
            log("Docker Machine. Check");
            return true;
        } else {
            log("ERROR : Docker Machine failed to set up properly.");
        }
        return false;
    }

    public boolean setupDocker() throws IOException {

        if (setupDockerMachine()) {

            config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("tcp://192.168.99.100:2376")
                    .withDockerTlsVerify(true)
                    .withDockerCertPath(userHome + "/.docker/machine/certs")
                    .withDockerConfig(userHome + "/.docker")
                    .withRegistryEmail("darylgabrielwong@gmail.com")
                    .withRegistryUsername("darylgabrielwong")
                    .withRegistryPassword("DWGabriel4")
                    .build();

            dockerClient = DockerClientBuilder.getInstance(config)
                    .build();

            if (dockerClient != null) {
                log("Docker Client. Check.");
                return true;
            }
        }
        return false;
    }

    public File writeCpuConfigFile(int cpuCores, int memory) throws IOException {
        File vBoxManage = new File("C:\\Program Files\\Oracle\\VirtualBox\\VBoxManage.exe");

        String cpuProc = String.format("\"" + vBoxManage.getAbsolutePath() + "\"" + " modifyvm default --cpus " + cpuCores);
        String memProc = String.format("\"" + vBoxManage.getAbsolutePath() + "\"" + " modifyvm default --memory " + memory);

        File cpuConfigFile = new File(nebulaData, "cpuconfig.bat");

        PrintWriter fout = new PrintWriter(new FileWriter(cpuConfigFile));
        fout.println("docker-machine stop");
        fout.println(cpuProc);
        fout.println(memProc);
        fout.println("docker-machine start");
        fout.flush();
        fout.close();

        return cpuConfigFile;
    }

    public void modifyNodeCPUandMemory(int cpuCores, int memory) throws IOException {
        File cpuconfig = writeCpuConfigFile(cpuCores, memory);

        System.out.println("FILE : " + cpuconfig.getAbsolutePath());
        Process modifyCPU = Runtime.getRuntime().exec("\"" + cpuconfig.getAbsolutePath() + "\"");

        String line;
        InputStream inputStream = modifyCPU.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        while ((line = bufferedReader.readLine()) != null) {
            System.out.println("[VBOX] " + line);
        }

        getCurrentCPU();
    }

    public void log(String message) {
        logger.info(message);
        logArea.append(message + "\n");
        System.out.println(message);
    }

    public void createDatabase() {
        if (OS.contains("WIN")) {
            appData = new File(System.getenv("APPDATA"));
            nebulaData = new File(appData, "Nebula");

        } else {
            appData = new File(System.getProperty("user.home"));
            nebulaData = new File(appData, "Nebula");
        }
        nebulaData.mkdir();
        taskCache = new File(nebulaData, "taskcache");
        updates = new File(nebulaData, "updates");
        results = new File(taskCache, "results");

        if (!taskCache.getAbsoluteFile().exists()) {
            taskCache.mkdir();
            updates.mkdir();
            results.mkdir();
            log("Database created at : " + taskCache.getAbsolutePath());
        } else {
            log("Database already exists. Location : " + taskCache.getAbsolutePath());
        }
    }

    public File downloadBlendFileFromURL(String url, String taskID, File taskCache) throws MalformedURLException {
        String filename = String.format(taskID + ".blend");

        File renderFile = new File(taskCache.getAbsolutePath(), filename);
        int renderfileLength = getFileSizeInKB(new URL(url));
//        int renderFileLimit = 20000;

        if (url.contains("google")) {
            System.out.println("Google Drive share link detected. Downloading from GDrive URL . . . ");
            downloadBlendfileFromGDrive(url, taskID, taskCache, renderfileLength);
        } else if (url.contains("dropbox")) {
            System.out.println("DropBox share link detected. Downloading from DropBox URL . . .");
            downloadBlendfileFromDbox(url, taskID, taskCache, renderfileLength);
        } else {
            System.out.println("ERROR : INVALID / UNKNOWN URL");
        }

        return renderFile;
    }

    public File downloadBlendfileFromDbox(String url, String taskID, File originalTaskDir, int renderfileLength) {
//        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
        String downloadURL = url.replace("?dl=0", "?dl=1");
        String filename = String.format(taskID + ".blend");
        File renderFile = new File(originalTaskDir.getAbsolutePath(), filename);
        try {
            URL download = new URL(downloadURL);
            System.out.println("DOWNLOAD (DBOX) : " + renderFile.getName());
            ReadableByteChannel rbc = Channels.newChannel(download.openStream());
            InputStream in = new BufferedInputStream((download.openStream()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int n = 0;
            int updateFileSize = out.size();
            int totalFileSize = renderfileLength;
            FileOutputStream fileOut = new FileOutputStream(renderFile);
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fileOut.flush();
            fileOut.close();
            rbc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderFile;
    }

    public File downloadBlendfileFromGDrive(String url, String taskID, File originalTaskDir, int renderfileLength) {
        String gdriveURL = url.replace("file/d/", "uc?export=download&id=");
        gdriveURL = gdriveURL.replace("/view?usp=sharing", "");
        String filename = String.format(taskID + ".blend");
        File renderFile = new File(originalTaskDir.getAbsolutePath(), filename);
        try {
            URL download = new URL(gdriveURL);
            System.out.println("DOWNLOAD (GDRIVE) : " + download.openStream());
            ReadableByteChannel rbc = Channels.newChannel(download.openStream());
            FileOutputStream fileOut = new FileOutputStream(renderFile);
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fileOut.flush();
            fileOut.close();
            rbc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderFile;
    }

    private static int getFileSizeInKB(URL url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.getInputStream();
            return conn.getContentLength() / 1024;
        } catch (IOException e) {
            return -1;
        } finally {
            conn.disconnect();
        }
    }


    public List<File> listFilesInDir(File taskCache) {

        File[] files = taskCache.getAbsoluteFile().listFiles();
        Arrays.sort(files);//ensuring order 001, 002, ..., 010, ...
        log("TASK CHECK | " + taskCache.getName() + " File Size: " + files.length);
        return Arrays.asList(files);
    }

    public boolean checkTaskFiles(List<File> taskCacheFiles, String originalTaskFileName, String tileScriptName) {
        boolean originalTaskFileReady = false;
        boolean tileScriptReady = false;
        boolean taskFilesReady = false;

        log("CHECK_TASK_FILES | originalTaskFileName : " + originalTaskFileName);
        log("CHECK_TASK_FILES | tileScriptName : " + tileScriptName);

        for (int i = 0; i < taskCacheFiles.size(); i++) {
            File file = taskCacheFiles.get(i);
            log(i + ". TASK CACHE FILE : " + file.getName());


            if (file.getName().equals(originalTaskFileName)) {
                originalTaskFileReady = true;
                log("TASK CHECK | " + originalTaskFileName + " checked and ready.");

            } else if (file.getName().equals(tileScriptName)) {
                tileScriptReady = true;
                log("TASK CHECK | " + tileScriptName + " checked and ready.");
            }
        }

        if (originalTaskFileReady && tileScriptReady) {
            taskFilesReady = true;
            System.out.println("Task Files checked and ready.");
            log("Task File download complete.");
        } else {
            System.out.println("[ERROR] Task Files inconsistent.");
        }

        return taskFilesReady;
    }

    public boolean checkQueue(String subtaskID) throws IOException {
        boolean queueOpen = false;

        if (queue.size() < 1) {
            queueOpen = true;
            queue.add(subtaskID);
            log(subtaskID + " added to the Queue. [Size : " + queue.size() + "]");
        } else {
            postReschedule();
            log("Queue size limit reached. Returning " + subtaskID + " to server. [Size : " + queue.size() + "]");
        }

        return queueOpen;
    }

    private boolean unzip(File zipFile, File destDir) {
        boolean unzipped = false;
        FileInputStream fis;
        byte[] buffer = new byte[(int) zipFile.length()];
        try {
            fis = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
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

    public void postResults() throws IOException {                 // TODO - Currently scans the TaskCache folder to retrieve userEmail (and other metaData) -- THIS IS DEPENDENT ON THERE BEING ONLY ONE JOB IN QUEUE AT A TIME.


        File[] resultsArray = results.listFiles();
        Queue<File> resultQueue = new LinkedList<>();

        log("CHECK | RESULTS");
        for (int i = 0; i < resultsArray.length; i++) {
            log("RESULTS : " + resultsArray[i].getName());
            if (taskParamsMap.get("taskID") != null && resultsArray[i].getName().contains(taskParamsMap.get("taskID"))) {
                log("TASK ID : " + taskParamsMap.get("taskID"));
                log(resultsArray[i].getName() + " has been added to the Result Queue.");
                resultQueue.add(resultsArray[i].getAbsoluteFile());
            } else {
                resultsArray[i].getAbsoluteFile().delete();
            }
        }

        if (resultsArray.length <= 0) {
            log("No tasks to return.");
        } else if (resultsArray.length > 0) {

            try {

                log("RESULT | Returning result " + resultQueue.peek().getName() + " to Server now . . .");
//                double computeMinutes = checkContainers("nebula_" + subtaskID);
                double computeSeconds = checkContainers("nebula_" + taskParamsMap.get("subtaskID"));
                String cost = String.valueOf(calculateCost(computeSeconds, totalGhz));
                log(" =====================================");
                log("NEW EARNINGS : " + cost);
                log(" =====================================");
                earningsToday.setText("Today's Earnings : USD " + totalEarn);

                File renderResult = resultQueue.peek().getAbsoluteFile();
                String resultParams = buildResultParamsString(computeSeconds, (computeSeconds / 60), cost);

                CloseableHttpClient httpClient = HttpClients.createDefault();
                HttpEntity data = MultipartEntityBuilder.create()
                        .addPart("Render-Result", new FileBody(renderResult))
                        .addTextBody("Result-Params", resultParams)
                        .build();

                HttpUriRequest request = new HttpPost(resultsServer);                                       // TODO - EDITS MADE HERE
                ((HttpPost) request).setEntity(data);
                CloseableHttpResponse response = httpClient.execute(request);
                int status = response.getStatusLine().getStatusCode();
                log("Executing request " + request.getRequestLine());
                log("Status Code for POST : " + status);

                httpClient.close();
                renderResult.delete();
                clearTaskCache();
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getStackTrace().toString());
            }
        }
    }

    public boolean compute() {
        boolean computing = false;

        try {
//            log("CHECK | App Data : " + appData.getAbsolutePath());
//            log("CHECK | Database located at : " + nebulaData.getAbsolutePath());

//            docker run -it -v taskCache:taskcache ikester/blender taskcache/blendfile.blend --python taskcache/thescript.py -o taskcache/frame_### -f 1
            String bindVolume = createVolume(taskCache.getAbsolutePath());

            CreateContainerResponse container = null;

            if (taskParamsMap.get("application").contains("blender")) {
                container = createBlenderContainer(taskParamsMap.get("taskFileName"),
                        taskParamsMap.get("tileScriptName"),
                        taskParamsMap.get("subtaskID"),
                        bindVolume,
                        taskParamsMap.get("startFrame"),
                        taskParamsMap.get("endFrame"),
                        taskParamsMap.get("renderFrame"),
                        taskParamsMap.get("renderOutputType"));
            }
            // else if (application.equals("insert application")) {
            // container = createApplicationContainer(param1, param2, param3, param-n, etc.)
            // }

            dockerClient.startContainerCmd(container.getId()).exec();
            if (computeStatus().equals("up")) {
                computing = true;
                log("Docker container started.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return computing;
    }

    public CreateContainerResponse createBlenderContainer(String originalTaskFileName,
                                                          String tileScriptName,
                                                          String subtaskID,
                                                          String bindVolume,
                                                          String startFrame,
                                                          String endFrame,
                                                          String renderFrame,
                                                          String renderOutputType) {
        CreateContainerResponse container = null;
        ArrayList<String> blenderCL;
//        int frameCount = Integer.valueOf(endFrame) - Integer.valueOf(startFrame) + 1;

        blenderCL = generateBlenderCL(originalTaskFileName,
                tileScriptName,
                subtaskID,
                renderFrame,
                renderOutputType);

        container = dockerClient.createContainerCmd("ikester/blender")
                .withCmd("bin/bash")
                .withName("nebula_" + subtaskID)
                .withBinds(Bind.parse(bindVolume))
                .withCmd(blenderCL.get(0)
                        , blenderCL.get(1)
                        , blenderCL.get(2)
                        , blenderCL.get(3)
                        , blenderCL.get(4)
                        , blenderCL.get(5)
                        , blenderCL.get(6)
                        , blenderCL.get(7)
                        , blenderCL.get(8)
                        , blenderCL.get(9)
                        , blenderCL.get(10)
                        , blenderCL.get(11)
                        , blenderCL.get(12)
                        , blenderCL.get(13))
                .exec();
        return container;
    }

    // todo - to be moved and generated from Server.
    public ArrayList<String> generateBlenderCL(String originalTaskFileName, String tileScriptName, String subtaskID, String renderFrame, String renderOutputType) {
        ArrayList<String> blenderCL = new ArrayList<>();

        blenderCL.add("-b");
        blenderCL.add("taskcache/" + originalTaskFileName);
        blenderCL.add("-E");
        blenderCL.add("CYCLES");
        blenderCL.add("--python");
        blenderCL.add("taskcache/" + tileScriptName);
        blenderCL.add("-o");
        blenderCL.add("/taskcache/results/" + subtaskID);
        blenderCL.add("-F");
        blenderCL.add(renderOutputType.toUpperCase());
        blenderCL.add("-f");
        blenderCL.add(renderFrame);
        blenderCL.add("-t");
        blenderCL.add("8");

        // TODO - ADD RENDER OUTPUT TYPE (.tga, etc.)

        return blenderCL;
    }

    public String createVolume(String taskCachePath) {
        String bindVolume;

        if (OS.contains("MAC")) {
            bindVolume = String.format(taskCachePath + ":/taskcache");
            return bindVolume;

        } else {
            taskCachePath = String.format("/" + taskCachePath);
            taskCachePath = taskCachePath.replaceAll(":", "");
            taskCachePath = taskCachePath.replace("\\", "/");
            taskCachePath = taskCachePath.replace("/C/", "/c/");

            bindVolume = String.format(taskCachePath + ":/taskcache");

            return bindVolume;
        }
    }

    public String computeStatus() {
        String status = "null";

        if (dockerClient != null) {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            if (containers.size() > 0) {
                String containerStatus = containers.get(0).getStatus().toLowerCase();

                if (containerStatus.contains("up")) {
                    status = "up";

                } else if (containerStatus.contains("exited")) {
                    status = "exited";
                }
            } else {
//                log("Number of Containers : " + containers.size());
                clearTaskCache();
            }
        } else {
            log("ERROR | Docker Client doesn't exist. Please restart the app.");
        }
        return status;
    }

    public void dockerStop() {
        if (dockerClient != null) {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            if (containers.size() > 0) {
                for (int i = 0; i < containers.size(); i++) {
                    dockerClient.stopContainerCmd(containers.get(i).getId()).exec();
                }
            }
            dockerClient.pruneCmd(PruneType.CONTAINERS).exec();
            clearTaskCache();
        }
    }

    public void clearTaskCache() {
        if (dockerClient != null) {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            if (containers.size() > 0) {
                log("Containers : " + containers.size());
            } else {
                queue.clear();
                File[] files = taskCache.listFiles();
                for (int i = 0; i < files.length; i++) {
                    String fileName = files[i].getName();
                    if (!fileName.contains(results.getName())) {
                        files[i].getAbsoluteFile().delete();
                    }
                }
            }
        } else {
            log("Docker not running.");
            queue.clear();
            File[] files = taskCache.listFiles();
            for (int i = 0; i < files.length; i++) {
                String fileName = files[i].getName();
                if (!fileName.contains(results.getName())) {
                    files[i].getAbsoluteFile().delete();
                }
            }
        }

    }

    public boolean pullBlender() throws InterruptedException {
        boolean blenderImagePulled = false;

        dockerClient.pullImageCmd("ikester/blender:latest")
                .exec(new PullImageResultCallback())
                .awaitCompletion(300, TimeUnit.SECONDS); // TODO - HARDCODED, SHOULD NOT RELY ON TIME.

        List<Image> images = dockerClient.listImagesCmd().exec();
        if (images.toString().contains("blender")) {    // todo - hardcoded for Blender, must be changed to be adaptive for all applications
            blenderImagePulled = true;
        }

        return blenderImagePulled;
    }

//    public String calculateCost(double computeSeconds, double totalGhz) {
////        double computeHours = round((computeMinutes / 60), 2);          // Converts to hours in 2 decimal format and parsed to Double.
//        double computeMinutes = (float) round((computeSeconds / 60), 2);
//        double perGhzMinute = (float) perGhzHourUSD / 60;
//        double cost;
//        String totalCost = null;
//        try {
//            cost = (float) round((totalGhz * computeMinutes * perGhzMinute), 2);
//            System.out.println("COST CHECK | perGhzMinute" + perGhzMinute);
//            System.out.println("COST CHECK | Cost : " + cost);
//            totalCost = String.valueOf(cost);
//
//            totalEarn += cost;
//            log("Total Earn : " + costFormat.format(totalEarn));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return totalCost;
//    }

    public String calculateCost(double computeSeconds, double totalGhz) {
        BigDecimal computeMinutes = bdRound(BigDecimal.valueOf(computeSeconds / 60), 6);
        BigDecimal perGhzMinute = bdRound(BigDecimal.valueOf(perGhzHourUSD / 60), 6);
        BigDecimal cost;
        String totalCost = null;

        try {
            cost = bdRound(computeMinutes.multiply(perGhzMinute).multiply(BigDecimal.valueOf(totalGhz)), 6);
            System.out.println("COST CHECK | Cost : " + cost);
            System.out.println("COST CHECK | perGhzMin : " + perGhzMinute);
            totalCost = String.valueOf(cost);

            totalEarn = bdRound(totalEarn.add(cost), 4);
            log("Total Earn : " + totalEarn);
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

        DecimalFormat ghzFormat = new DecimalFormat("#.#");
        deviceID = systemInfo.getHardware().getComputerSystem().getSerialNumber();
        totalLogicCores = processor.getLogicalProcessorCount();
        totalPhysCores = processor.getPhysicalProcessorCount();
        long cpuHertz = processor.getMaxFreq();
        double cpuGhz = Double.parseDouble(ghzFormat.format(cpuHertz / 1000000000.0));
        double totalGhz = cpuGhz * totalLogicCores;
        log("Total CPU GHZ : " + totalGhz);

        return totalGhz;
    }

    public int getCurrentCPU() throws IOException {
        int cpu = 0;
        Process dockerInfo = Runtime.getRuntime().exec("docker info");

        String line;
        InputStream inputStream = dockerInfo.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        while ((line = bufferedReader.readLine()) != null) {
            System.out.println("[VBOX] " + line);

            for (int i = 1; i < totalLogicCores; i++) {
                if (line.contains("CPUs: " + i)) {
                    cpu = i;
                }
            }
        }
        int cpuPercentage = calculatePercentage(cpu);
//        double cpuPercentage = Math.ceil((cpu / (totalLogicCores - 1)) * 100);
        System.out.println("Logic Cores : " + totalLogicCores + " | CPU : " + cpu + " | CPU PERCENTAGE : " + cpuPercentage);
        this.cpuPercentage.setText("CPU : " + cpuPercentage + " %");
        return cpu;
    }

    public int calculatePercentage(int cpu) {

        float percentageF = (((float) cpu) / (totalLogicCores - 1)) * 100;
        int percentage = (int) percentageF;

        return percentage;
    }

    public double checkContainers(String containerName) {

        Collection<String> status = new ArrayList<>();
        status.add("exited");
        List<Container> containers = dockerClient.listContainersCmd()
                .withStatusFilter(status).exec();
        double computeMinutes = 0;
        double computeSeconds = 0;

        if (containers.size() > 0) {
//            computeMinutes = getComputeTime(containerName);                                 // Gets total compute time in seconds
            computeSeconds = getComputeTime(containerName);
            refreshTotalComputeTime(computeMinutes);
            log("Total Compute Time for " + taskParamsMap.get("subtaskID") + ": " + computeSeconds + " sec(s)");
        } else {
            log("CHECK | Number of containers: " + containers.size());
        }
        return computeSeconds;
    }

    public static Timestamp timeStamp(String timeString) throws ParseException {
        String time = timeString.substring(0, 22).trim().replace("T", " ");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SS");
        Date parsedDate = dateFormat.parse(time);
        Timestamp timestamp = new Timestamp(parsedDate.getTime());

        return timestamp;
    }

    public double getComputeTime(String containerName) {                                                                // TODO - REVIEW ACCURACY
        double minutes = 0;
        double seconds = 0;
        String startTime = dockerClient.inspectContainerCmd(containerName).exec().getState().getStartedAt();
        String finishTime = dockerClient.inspectContainerCmd(containerName).exec().getState().getFinishedAt();
        try {
            Timestamp start = timeStamp(startTime);
            Timestamp finish = timeStamp(finishTime);
            double milliseconds = finish.getTime() - start.getTime();
            minutes = round((milliseconds / (60 * 1000)), 2); // Reference : 1 second = 1000 milliseconds | 1 minute = 60 (seconds) * 1000 (milli)
            seconds = round((milliseconds / 1000), 4);

            log(containerName + " (Start)  : " + start.toString());
//            log(containerName + " (RAW -START) : " + startTime);
            log(containerName + " (Finish) : " + finish.toString());
//            log(containerName + " (RAW -FINISH) : " + finishTime);

            log("Total Compute Time (Milli) : " + milliseconds);
            log("Total Compute Time (Min) : " + minutes);
            log("Total Compute Time (Seconds) : " + seconds);
            log(" ------------- ");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return seconds;
    }

    public void antiScreensaver() throws AWTException {
        Robot robot = new Robot();
        Point pObj = MouseInfo.getPointerInfo().getLocation();
        robot.mouseMove(pObj.x - 1, pObj.y - 1);
        robot.mouseMove(pObj.x + 1, pObj.y + 1);
    }

    public void refreshTotalComputeTime(double minutes) {
        totalMinutes += minutes;
        totalMinutes = round(totalMinutes, 2);
//        if (totalMinutes >= 60) {
//            int hours = (totalMinutes / 60);
//            totalHours += hours;
//            totalMinutes -= (hours * 60);
//        }
        totalComputeTime.setText(String.format("Total Compute Time : " + timeFormat.format(totalMinutes) + " s "));
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

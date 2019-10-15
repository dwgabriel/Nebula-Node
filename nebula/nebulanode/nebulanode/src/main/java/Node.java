import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.PruneType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Node {

    private static DockerClient dockerClient;
    private static String userEmail;                                                                                           // NOT THE SAME AS nodeEmail. This is Demand User's identity, not Supply User's.
    private static String subtaskID;
    private static String taskID;
    private static String application;
    private static String tileScriptName;
    private static String originalTaskName;
    private static boolean logged = false;
    private static boolean pingStatus = false;
    private static String userHome = System.getProperty("user.home");
    private static File appData;
    private static File nebulaData;
    private static String OS = (System.getProperty("os.name")).toUpperCase();

    private static File taskCache;
    private static File results;
    Queue<String> queue = new LinkedList<>();

    String schedulerServer = "https://nebula-server.herokuapp.com/scheduler";
    String resultsServer = "https://nebula-server.herokuapp.com/complete";
    String local = "http://localhost:8080/scheduler";

    private Logger logger = LoggerFactory.getLogger(Node.class);

    JFrame frame;
    private JPanel panel1;
    private JLabel status;
    private JButton start;
    private JButton stop;
    private JButton quit;
    private JTextArea logArea;

    public static void main(String[] args) {
        Login login = new Login();

        while (!logged) {
            System.out.println("Running ");
            if (logged = login.run()) {
                System.out.println("You have successfully logged in.");
                Node node = new Node(login.getUsername(), login.getPass());
                node.startNode(node);
                break;
            }
            break;
        }
    }

    public Node(String user, String pass) {
        frame = new JFrame("Node");
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        status.setText("Welcome to Nebula Node");
        start.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createDatabase();
                status.setText("Task Cache created at : " + nebulaData.getAbsolutePath());
                try {
                    pingStatus = true;
                    status.setText("Node is running. Fetching work . . .");
                    pingServer();
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
        stop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pingStatus) {
                    pingStatus = false;
                    dockerStop();
                    status.setText("Node has been stopped.");
                } else {
                    status.setText("Node is not running.");
                }
            }
        });
        quit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirmed = JOptionPane.showConfirmDialog(null,
                        "Are you sure you want to stop making money?", "Quitter, quitter!",
                        JOptionPane.YES_NO_OPTION);

                if (confirmed == JOptionPane.YES_OPTION) {
                    if (computeStatus()) {
                        dockerStop();
                    }
                    System.exit(0);

                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int confirmed = JOptionPane.showConfirmDialog(frame, "Are you sure you want to stop making money?", "Quitter, quitter!",
                        JOptionPane.YES_NO_OPTION);

                if (confirmed == JOptionPane.YES_OPTION) {
                    if (computeStatus()) {
                        dockerStop();
                    }
                    System.exit(0);
                }
            }
        });
    }


    public void startNode(Node node) {
        if (OS.contains("WIN")) {
            appData = new File(System.getenv("APPDATA"));
            nebulaData = new File(appData, "Nebula");
        } else {
            appData = new File(System.getProperty("user.home"));
            nebulaData = new File(appData, "Nebula");
        }
        nebulaData.mkdir();

    }

    public void log(String message) {
        logger.info(message);
        logArea.append(message + "\n");
        System.out.println(message);
    }

    public void createDatabase() {
        taskCache = new File(nebulaData, "taskcache");
        results = new File(taskCache, "results");

        if (!taskCache.getAbsoluteFile().exists()) {
            taskCache.mkdir();
            results.mkdir();
            log("Database created at : " + taskCache.getAbsolutePath());
        } else {
            log("Database already exists. Location : " + taskCache.getAbsolutePath());
        }
    }

    public void pingServer() {
        final Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (pingStatus) {
                    try {
                        clearTaskCache();
                        if (queue.size() < 1) {
                            log("Running. Fetching jobs from Server...");
                            postStatus();
                        } else if (queue.size() >= 1) {
                            log("Queue is full.");
                        }

                        if (computeStatus()) {
                            log("Node is still rendering for task : " + taskID);
                        } else {
                            log("Node is now rendering for task " + taskID + ". Please DO NOT turn off computer.");
                            compute();
                        }
                        postResults();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    log("Ping to server stopped.");
                    timer.cancel();
                }
            }
        }, 1 * 1 * 1000, 1 * 60 * 1000);
    }

    public void postStatus() throws IOException { // Sends nebulanode.Node Status to Server as request for Tasks. Should receive 2 Items - (a) Docker Blender . (b) Task data

        CloseableHttpClient httpClient = HttpClients.createDefault();
        String score = "95";
        int counter = 0;

        try {
            // Build entity for passing nebulanode.Node status and parameters.
            HttpEntity data = EntityBuilder.create()
                    .setContentEncoding("UTF-8")
                    .setContentType(ContentType.APPLICATION_FORM_URLENCODED)
                    .setParameters(new BasicNameValuePair("Node-Email", "mrnode@gmail.com")
                            , new BasicNameValuePair("Device-Identity", "default_node")
                            , new BasicNameValuePair("Queue", Integer.toString(queue.size()))
                            , new BasicNameValuePair("Score", score))
                    .build();

            HttpUriRequest request = new HttpPost(schedulerServer);
            ((HttpPost) request).setEntity(data);
            CloseableHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                long taskSize = entity.getContentLength();
                log("Entity length : " + taskSize);
                entity.getContent();
                Header[] headers = response.getAllHeaders();
                for (Header header : headers) {
                    log("Key : " + header.getName() + " | Value : " + header.getValue());
                }
                userEmail = response.getFirstHeader("User-Email").getValue();
                tileScriptName = response.getFirstHeader("Tile-Script").getValue();
                originalTaskName = response.getFirstHeader("Task-Name").getValue();
                taskID = response.getFirstHeader("Task-Identity").getValue();
                subtaskID = response.getFirstHeader("Subtask-Identity").getValue();
                application = response.getFirstHeader("Application").getValue();

                File taskPackage = new File(taskCache, taskID.concat(".zip"));
                FileOutputStream fos = new FileOutputStream(taskPackage);
                entity.writeTo(fos);
                fos.close();
                queue.add(taskID);
                unzip(taskPackage, taskCache);

                File[] taskCacheArray = taskCache.listFiles();
                for (int i = 0; i < taskCacheArray.length; i++) {
                    log("Task Cache - " + i + " : " + taskCacheArray[i].getName());
                    setMetadata("User-Email", userEmail, taskCacheArray[i].toPath());
                    setMetadata("Task-Name", originalTaskName, taskCacheArray[i].toPath());
                    setMetadata("Task-Identity", taskID, taskCacheArray[i].toPath());
                    setMetadata("Subtask-Identity", subtaskID, taskCacheArray[i].toPath());
                    setMetadata("Application", application, taskCacheArray[i].toPath());
                }

            } else if (entity == null) {
                log("Entity null.");
            }
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
    }

    public void setMetadata(String metaName, String metaValue, Path metaPath) {                                                     // Setting Application info as Subtask attribute for retrieval later by ComputeTask.
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(metaPath, UserDefinedFileAttributeView.class);
            view.write(metaName, Charset.defaultCharset().encode(metaValue));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMetadata(Path metaPath, String metaName) {                                                                       // Getting Application info from Subtask attributes for ComputeTask.
        String metaValue = null;
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(metaPath, UserDefinedFileAttributeView.class);
            ByteBuffer buffer = ByteBuffer.allocate(view.size(metaName));
            view.read(metaName, buffer);
            buffer.flip();
            metaValue = Charset.defaultCharset().decode(buffer).toString();
            log("TEST | Attribute : " + metaValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaValue;
    }

    private void unzip(File zipFile, File destDir) {
        FileInputStream fis;
        byte[] buffer = new byte[(int) zipFile.length()];
        try {
            fis = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                log("Unzipping to " + newFile.getAbsolutePath());
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void postResults() throws IOException {                 // TODO - Currently scans the TaskCache folder to retrieve userEmail (and other metaData) -- THIS IS DEPENDENT ON THERE BEING ONLY ONE JOB IN QUEUE AT A TIME.

        File[] resultsArray = results.listFiles();
        Queue<File> resultQueue = new LinkedList<>();
        for (int i = 0; i < resultsArray.length; i++) {
            if (resultsArray[i].getName().contains(".png")) {
                ((LinkedList<File>) resultQueue).add(resultsArray[i].getAbsoluteFile());
            }
        }

        if (resultQueue.size() <= 0) {
            log("No tasks to return.");
        } else if (resultQueue.size() > 0) {
            log("Returning task " + subtaskID + " to Server now . . .");

            CloseableHttpClient httpClient = HttpClients.createDefault();

            File renderResult = resultQueue.peek().getAbsoluteFile();


            try {
                HttpEntity data = MultipartEntityBuilder.create()
                        .addPart("subtask", new FileBody(renderResult))
                        .addTextBody("Node-Email", "mrnode@gmail.com")
                        .addTextBody("Device-Identity", "default_node")
                        .addTextBody("Task-Identity", taskID)
                        .addTextBody("Subtask-Identity", subtaskID)
                        .addTextBody("User-Email", userEmail)
                        .build();

                HttpUriRequest request = new HttpPost(resultsServer);
                ((HttpPost) request).setEntity(data);
                CloseableHttpResponse response = httpClient.execute(request);

                int status = response.getStatusLine().getStatusCode();
                log("Executing request " + request.getRequestLine());
                log("Status Code for POST : " + status);
                response.close();
                httpClient.close();
                renderResult.delete();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void compute() {

        if (taskCache.listFiles().length <= 0) {
            log("There are no tasks to compute at this time.");
        } else if (taskCache.listFiles().length > 0) {
            status.setText("Node is now rendering. Please DO NOT turn off your computer");
            log("App Data : " + appData.getAbsolutePath());
            log("Database located at : " + nebulaData.getAbsolutePath());


            try {
                log("Computing (0) ...");

                DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .withDockerHost("tcp://192.168.99.100:2376")
                        .withDockerTlsVerify(true)
                        .withDockerCertPath(userHome + "/.docker/machine/certs")
                        .withDockerConfig(userHome + "/.docker")
                        .withRegistryEmail("darylgabrielwong@gmail.com")
                        .withRegistryUsername("darylgabrielwong")
                        .withRegistryPassword("DWGabriel4")
                        .build();
                log("Computing (1) ...");
                dockerClient = DockerClientBuilder.getInstance(config)
                        .build();
//            docker run -it -v taskCache:taskcache ikester/blender taskcache/blendfile.blend --python taskcache/thescript.py -o taskcache/frame_### -f 1
                log("Computing (2) ...");

                ArrayList<String> blenderCL = new ArrayList<>();
                blenderCL.add("taskcache/" + originalTaskName);
                blenderCL.add("--python");
                blenderCL.add("taskcache/" + tileScriptName);
                blenderCL.add("-o");
                blenderCL.add("/taskcache/results/" + subtaskID);
                blenderCL.add("-f");
                blenderCL.add("1");
                log("Computing (3) ...");

                String bindVolume = createVolume(taskCache.getAbsolutePath());
                log(bindVolume);
                log("Computing (4) ...");

                CreateContainerResponse container = dockerClient.createContainerCmd("ikester/blender")
                        .withCmd("bin/bash")
                        .withName("nebula_" + subtaskID)
                        .withBinds(Bind.parse(bindVolume))
                        .withCmd(blenderCL.get(0), blenderCL.get(1), blenderCL.get(2), blenderCL.get(3), blenderCL.get(4), blenderCL.get(5), blenderCL.get(6))
                        .exec();
                log("Computing (5) ...");

                dockerClient.startContainerCmd(container.getId()).exec();
                log("Docker container started.");

            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        }
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

    public boolean computeStatus() {

        if (dockerClient != null) {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            if (containers.size() > 0) {
                return true;
            } else {
                return false;
            }
        }
        return false;
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
        }
    }

    public void clearTaskCache() {
        if (dockerClient != null) {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            if (containers.size() > 0) {
                log("Still rendering . . . ");
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
        }
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
        panel1.setLayout(new GridLayoutManager(3, 3, new Insets(10, 10, 10, 10), 50, 20));
        panel1.setBackground(new Color(-15528407));
        panel1.setMinimumSize(new Dimension(675, 275));
        panel1.setPreferredSize(new Dimension(675, 275));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        logArea = new JTextArea();
        scrollPane1.setViewportView(logArea);
        start = new JButton();
        start.setBackground(new Color(-1));
        start.setForeground(new Color(-15124111));
        start.setText("Start");
        panel1.add(start, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, 30), null, 1, false));
        stop = new JButton();
        stop.setBackground(new Color(-1));
        stop.setForeground(new Color(-15124111));
        stop.setHorizontalTextPosition(0);
        stop.setText("Stop");
        panel1.add(stop, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, 30), null, 0, false));
        quit = new JButton();
        quit.setBackground(new Color(-1));
        quit.setForeground(new Color(-15124111));
        quit.setText("Quit");
        panel1.add(quit, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, 30), null, 0, false));
        status = new JLabel();
        status.setForeground(new Color(-1));
        status.setText("");
        panel1.add(status, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(138, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }
}

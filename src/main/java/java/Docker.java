import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.exec.PullImageCmdExec;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.tomcat.jni.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Docker {

    private static String userHome = System.getProperty("user.home");
    private static String nodeDir = System.getProperty("user.dir");
    private static File appData = new File(System.getenv("APPDATA"));
    private static File nebulaData = new File(appData, "Nebula");
    private static File taskCache = new File(nebulaData, "taskcache");
    private static String OS = (System.getProperty("os.name")).toUpperCase();

    private Logger logger = LoggerFactory.getLogger(Docker.class);
    private JTextArea logArea;

    private static DefaultDockerClientConfig config;
    private static DockerHttpClient dockerHttpClient;
    private static DockerClient dockerClient;

    public LinkedHashMap<String, String> taskParamsMap;

    public Docker(LinkedHashMap<String, String> taskParamsMap, JTextArea logArea) {
        this.taskParamsMap = taskParamsMap;
        this.logArea = logArea;

        try {
            boolean dockerSetup = setupDocker();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean compute(LinkedHashMap<String, String> taskParamsMap, File taskCache) throws InternalServerErrorException {
        boolean computing = false;

        try {
//            docker run -it -v taskCache:taskcache ikester/blender taskcache/blendfile.blend --python taskcache/thescript.py -o taskcache/frame_### -f 1
            dockerClient.pruneCmd(PruneType.CONTAINERS).exec();
            String bindVolume = createVolume(taskCache.getAbsolutePath());

            CreateContainerResponse container = null;

            log("CHECK -- compute() renderfileName : " + taskParamsMap.get("renderfileName") + " | App  : " + taskParamsMap.get("application"));


            if (taskParamsMap.get("application").contains("blender")) {
                container = createBlenderContainer(bindVolume,
                        taskParamsMap.get("subtaskID"),
                        taskParamsMap.get("tileScriptName"),
                        taskParamsMap.get("renderFrame"),
                        taskParamsMap.get("renderOutputType"),
                        taskParamsMap.get("application"));
            }

            dockerClient.startContainerCmd(container.getId()).exec();
            if (computeStatus()) {
                computing = true;
                log("[LOG] Docker container started.");
            } else {
                log("[LOG] Docker failed to render. Attempting to restart Docker . . .");
                dockerRestart();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return computing;
    }

    public void dockerRestart() throws IOException, InternalServerErrorException {
        Node.pingStatus = false;
        log("[LOG] Restarting Docker . . .");
        File source = new File(getSource());
        String rootDrive = String.valueOf(source.getAbsolutePath().charAt(0));

        try {
            Process process;
            ProcessBuilder restartProcess = new ProcessBuilder("cmd", "taskkill", "/IM", "\"Docker Desktop.exe\"", "/F", "&&", "start", "\"\"", "\"C:\\Program Files\\Docker\\Docker\\frontend\\Docker Desktop.exe\"");
            process = restartProcess.start();

            if (setupDocker()) {
                log("[LOG] Docker restarted succesfully.");
                Node.pingStatus = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    public File writeRestartDockerBat(String rootDrive) throws IOException {
//
//        File restartDockerBat = new File(taskCache, "restart_docker.bat");
//
//        PrintWriter fout = new PrintWriter(new FileWriter(restartDockerBat));
//        fout.println("echo off");
//        fout.println("taskkill /IM \"Docker Desktop.exe\" /F");
//        fout.println("start \"\" \"C:\\Program Files\\Docker\\Docker\\frontend\\Docker Desktop.exe\""); // HARDCODED. SHOULD REFLECT WHERE USERS INSTALL DOCKER
//        fout.flush();
//        fout.close();
//
//        return restartDockerBat;
//    }

    public String getSource() {
        String nebulaDirPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("%20", "");

        return nebulaDirPath;
    }

    public CreateContainerResponse createBlenderContainer(String bindVolume,
                                                          String subtaskID,
                                                          String tileScriptName,
                                                          String renderFrame,
                                                          String renderOutputType,
                                                          String application) {

        CreateContainerResponse container = null;
        ArrayList<String> blenderCL;

        try {
            System.out.println("CHECK -- createBlContainer | application : " + application);
            blenderCL = generateBlenderCL(tileScriptName,
                    subtaskID,
                    renderFrame,
                    renderOutputType,
                    Node.gpu,
                    application);

            HostConfig hostConfig = new HostConfig().withBinds(Bind.parse(bindVolume));

            container = dockerClient.createContainerCmd("ikester/blender")
                    .withHostConfig(hostConfig)
                    .withCmd("bin/bash")
                    .withName("nebula_" + subtaskID)
//                .withBinds(Bind.parse(bindVolume))
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
                            , blenderCL.get(13)
                            , blenderCL.get(14)
                            , blenderCL.get(15)).exec();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return container;
    }

//    public double checkContainers(String containerName) {
//
//        Collection<String> status = new ArrayList<>();
//        status.add("exited");
//        List<Container> containers = dockerClient.listContainersCmd()
//                .withStatusFilter(status).exec();
//        double computeMinutes = 0;
//        double computeSeconds = 0;
//
//        if (containers.size() > 0) {
//            computeSeconds = getComputeTime(containerName);
//            log("[LOG] Total Compute Time for " + taskParamsMap.get("subtaskID") + ": " + computeSeconds + " sec(s)");
//        } else {
//            log("[LOG] Number of containers: " + containers.size());
//        }
//        return computeSeconds;
//    }

    // todo - to be moved and generated from Server.
    public ArrayList<String> generateBlenderCL(String tileScriptName, String subtaskID, String renderFrame, String renderOutputType, String gpu, String application) {
        ArrayList<String> blenderCL = new ArrayList<>();

        System.out.println("APPLICTION : " + application);
        if (application.toLowerCase().contains("cycles")) {
            application = "CYCLES";
        } else if (application.toLowerCase().contains("eevee")) {
            application = "BLENDER_EEVEE";
        } else if (application.toLowerCase().contains("workbench")) {
            application = "BLENDER_WORKBENCH";
        }

        String renderfilePath = generateBlenderRenderfilePath(taskCache);

        blenderCL.add("-b");
        blenderCL.add(renderfilePath);
        blenderCL.add("-E");
        blenderCL.add(application);
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
        blenderCL.add("--cycles-device");

        if (gpu != null && gpu.equals("nvidia")) {
            blenderCL.add("CUDA+CPU");
        } else if (gpu != null && gpu.equals("amd")) {
            blenderCL.add("OPENCL+CPU");
        } else {
            blenderCL.add("CPU");
        }

        // TODO - ADD RENDER OUTPUT TYPE (.tga, etc.)

        return blenderCL;
    }

    public String generateBlenderRenderfilePath(File taskCache) {
        StringBuilder renderfilePath = new StringBuilder();

        try {
            File renderfile = Task.checkFile(".blend", taskCache, "end");
            String[] paths = renderfile.getAbsolutePath().split("\\\\");
            ArrayList<String> renderfilePaths = new ArrayList<>();
            for (int i=0; i< paths.length; i++) {
                String path = paths[i];

                if (path.equals("taskcache")) {
                    for (int j=i; j<paths.length; j++) {
                        renderfilePaths.add(paths[j]);
                    }
                }
            }

            for (int i=0; i<renderfilePaths.size(); i++) {
                String path = renderfilePaths.get(i);

                if (i == 0) {
                    renderfilePath.append(path);
                } else {
                    renderfilePath.append("/" + path);
                }
            }

            System.out.println("RENDERFILE PATH : " + renderfilePath);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderfilePath.toString();
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
            System.out.println("[LOG] BIND VOLUME : " + bindVolume);

            return bindVolume;
        }
    }

    public boolean computeStatus()  {
        boolean status = false;

        if (dockerClient != null) {
            List<Container> containers = dockerClient.listContainersCmd().exec();
            if (containers.size() > 0) {
                String containerStatus = containers.get(0).getStatus().toLowerCase();

                if (containerStatus.contains("up")) {
                    status = true;
                } else {
                    log("[LOG] Container status : Down");
                }
            } else {
                log("[LOG] No containers.");
            }
        } else {
            log("[ERROR] Docker Client doesn't exist. Restarting docker.");
        }
        return status;
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
            minutes = Node.round((milliseconds / (60 * 1000)), 2); // Reference : 1 second = 1000 milliseconds | 1 minute = 60 (seconds) * 1000 (milli)
            seconds = Node.round((milliseconds / 1000), 4);

            log("[LOG] " + containerName + " (Start)  : " + start.toString());
//            log(containerName + " (RAW -START) : " + startTime);
            log("[LOG] " + containerName + " (Finish) : " + finish.toString());
//            log(containerName + " (RAW -FINISH) : " + finishTime);

            log("[LOG] Total Compute Time (Milli) : " + milliseconds);
            log("[LOG] Total Compute Time (Min) : " + minutes);
            log("[LOG] Total Compute Time (Seconds) : " + seconds);
            log(" --------------------------- ");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return seconds;
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
//
//    public boolean setupDockerMachine() throws IOException {
//        String line;
//        System.out.println("Setting Node CPUs . . . ");
//
//        Process dockerMachineProcess = Runtime.getRuntime().exec("C:\\Program Files\\Docker Toolbox\\docker-start.cmd");
//        InputStream stdin = dockerMachineProcess.getInputStream();
//        BufferedReader brCleanUp = new BufferedReader(new InputStreamReader(stdin));
//        while ((line = brCleanUp.readLine()) != null) {
//            log("[Stdout] " + line);
//        }
//        if (dockerMachineProcess.exitValue() == 0) {
//            log("Docker Machine. Check");
//            return true;
//        } else {
//            log("ERROR : Docker Machine failed to set up properly.");
//        }
//        return false;
//    }

    public boolean setupDocker()  {

        boolean dockerSetup = false;

        try {

            log("[LOG] Setting up Docker . . .");
            config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("tcp://localhost:2375")
                    .withDockerTlsVerify(true)
                    .withDockerCertPath(userHome + "/.docker")
                    .withRegistryEmail("darylgabrielwong@gmail.com")
                    .withRegistryUsername("darylgabrielwong")
                    .withRegistryPassword("DWGabriel4")
                    .build();

            log("[LOG] - 1. Docker Config generated.");
            dockerHttpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .build();
            log("[LOG] - 2. Docker Http Client established.");

            dockerClient = DockerClientImpl.getInstance(config, dockerHttpClient);
            log("[LOG] - 3. Docker Client instance created.");

            if (dockerClient != null) {
                while (!dockerSetup) {
                    try (DockerHttpClient.Response response = dockerHttpClient.execute(DockerHttpClient.Request.builder().method("GET").path("/_ping").build())) {
                        if (response.getStatusCode() == 200) {
                            System.out.println("[LOG] Response : " + response.getStatusCode());
                            dockerSetup = true;
                            System.out.println("[LOG] Docker setup.");

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                log("[LOG] Docker failed to setup.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dockerSetup;
    }

    public boolean checkForApplication(String application) throws InterruptedException {
        boolean applicationReady = false;
        String requiredApp = null;
        List<Image> images = dockerClient.listImagesCmd().exec();

        if (application.contains("blender")) {
            requiredApp = "blender";
        }


        if (images.size() > 0) {
            Iterator<Image> iterator = images.iterator();

            while (iterator.hasNext()) {
                Image image = iterator.next();

                if (!image.toString().contains(requiredApp)) {    // todo - hardcoded for Blender, must be changed to be adaptive for all applications
                    log("[LOG] Required App not found. Node is downloading app . . .");
                    applicationReady = pullImage(requiredApp);
                } else {
                    log("[LOG] '" + application + "' docker image already exists.");
                    applicationReady = true;
                }
            }
        } else {
            log("[LOG] No images exist. Pulling " + application + " image . . .");
            applicationReady = pullImage(application);
        }

//        if (images == null || !images.toString().contains(taskParamsMap.get("application"))) {    // todo - hardcoded for Blender, must be changed to be adaptive for all applications
//            log("[LOG] Required App not found. Node is downloading app . . .");
//            applicationReady = pullImage(application);
//        } else {
//            log("[LOG] " + application + " docker image already exists.");
//            applicationReady = true;
//        }

        return applicationReady;
    }

    public boolean pullImage(String application) throws InterruptedException {
        boolean imagePulled = false;

        if (application.contains("blender")) {
            imagePulled = pullBlender();
        }
        // else if (*insert other application*) {
        // pullImage of respective applications
        //}

        return imagePulled;
    }

    public boolean pullBlender() throws InterruptedException {
        boolean blenderImagePulled = false;

        dockerClient.pullImageCmd("ikester/blender:latest").exec(new PullImageResultCallback()).awaitCompletion();

        List<Image> images = dockerClient.listImagesCmd().exec();
        if (images.toString().contains("blender")) {    // todo - hardcoded for Blender, must be changed to be adaptive for all applications
            blenderImagePulled = true;
        }

        return blenderImagePulled;
    }

    public static Timestamp timeStamp(String timeString) throws ParseException {
        String time = timeString.substring(0, 22).trim().replace("T", " ");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SS");
        Date parsedDate = dateFormat.parse(time);
        Timestamp timestamp = new Timestamp(parsedDate.getTime());

        return timestamp;
    }

    public static DockerClient getDockerClient() {
        return dockerClient;
    }

    public void log(String message) {
//        logger.info(message);
        logArea.append(message + "\n");
        System.out.println(message);
    }
}

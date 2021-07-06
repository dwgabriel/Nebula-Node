import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Task {
    File vrayDir = new File("C:\\Program Files\\Chaos Group\\V-Ray\\V-Ray for SketchUp\\extension\\vrayappsdk\\bin");
    File blenderDir = new File("C:\\Program Files\\Blender Foundation\\Blender 2.92");
    private File nebulaData = Node.getNebulaData();
    private File taskCache = new File(nebulaData, "taskcache");
    private File results = new File(taskCache, "results");
    private File renderfile;
    private File tileScript;
    private File dimensionScript;
    private File packedSkp;
    private File renderResult;
    private double computeSeconds;
    private double computeMinutes = round(computeSeconds / 60, 2);

    private boolean tasksReady = false;

    private Logger logger = LoggerFactory.getLogger(Task.class);
    private JTextArea logArea;
    private JLabel status;
    LinkedHashMap<String,String> taskParamsMap = new LinkedHashMap<>();
    LinkedHashMap<String, Integer> tileBorderMap;

    public boolean isTasksReady() {
        return tasksReady;
    }

    //    Docker docker = Node.docker;
    static Process renderProcess;

    public Task(LinkedHashMap<String, String> taskParamsMap,
                HttpEntity subtaskEntity,
                File taskCache,
                JTextArea logArea,
                JLabel status) {
       try {
           this.taskCache = taskCache;
           this.taskParamsMap = taskParamsMap;
           this.logArea = logArea;
           this.status = status;

           if (setupTaskFiles(subtaskEntity, taskParamsMap)) {
               log("[LOG] Task setup successfully.");
               tasksReady = true;
           } else {
               log("[ERROR] Failed to setup tasks for rendering.");
           }
       } catch (Exception e) {
           e.printStackTrace();
       }
    }

    public boolean startRendering() {
        boolean rendered = false;
        boolean renderCompleted = false;
        File renderResult = null;
        String application = taskParamsMap.get("application");

        try {
            if (application.contains("blender")) {

                renderCompleted = startBlenderRender(renderfile, tileScript);
            } else if (application.contains("vray")) {

                renderCompleted = startVrayRender(renderfile, tileScript);
            }

            if (renderCompleted) {
                renderResult = checkResults(results,
                        taskParamsMap.get("subtaskID"),
                        taskParamsMap.get("renderOutputType"),
                        tileScript);

                if (renderResult != null && renderResult.length() > 0) {
                    System.out.println("[LOG] RENDER RESULT : " + renderResult.getName() + " | SIZE : " + renderResult.length());

                    rendered = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rendered;
    }

    public boolean startBlenderRender(File renderfile, File tileScript) throws IOException, InterruptedException {
        boolean renderComplete = false;
        String subtaskID = taskParamsMap.get("subtaskID");
        int renderFrame = Integer.parseInt(taskParamsMap.get("renderFrame"));
        String renderOutputType = taskParamsMap.get("renderOutputType");
        String application = taskParamsMap.get("application");

        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "C: && cd \"" + blenderDir.getAbsolutePath() + "\" && blender.exe"
                + " -b \"" + renderfile.getAbsolutePath() + "\""
                + " -E " + setBlenderEngine(application)
                + " --python \"" + taskCache.getAbsolutePath() + File.separator + tileScript.getName() + "\""
                + " -o \"" + results.getAbsolutePath() + File.separator + subtaskID + "\""
                + " -F " + renderOutputType.toUpperCase()
                + " -f " + renderFrame
                + " -- --cycles-device " + setBlenderDevice(Node.gpu));

        renderProcess = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(renderProcess.getInputStream()));

        String line;
        boolean prepEcho = false;
        boolean renderEcho = false;
        boolean errorEcho = false;
        while ((line = reader.readLine()) != null) {
            log(line);

            if (line.toLowerCase().contains("preparing") || line.toLowerCase().contains("updating") || line.toLowerCase().contains("synchronizing") && !renderComplete) {
                status.setText("Preparing render . . .");
                if (!prepEcho) {
                    echoCmd("Preparing render . . .", 5);
                    prepEcho = true;
                }

            } else if (line.toLowerCase().contains("rendering") || line.toLowerCase().contains("rendered") || line.toLowerCase().contains("compositing") && !renderComplete) {
                status.setText("Rendering " + subtaskID);
                if (!renderEcho) {
                    echoCmd("Rendering " + subtaskID, 5);
                    renderEcho = true;
                }

            } else if (line.toLowerCase().contains("saved") && !renderComplete) {
                renderComplete = true;

                echoCmd("Completed " + subtaskID, 5);

            } else if (line.contains("Error:") && !renderComplete) {
                status.setText("Failed to render " + subtaskID);
                Runtime.getRuntime().exec("taskkill /F /IM blender.exe");
                log("[ERROR] Render failed. Returning task to Server . . .");

                if (!errorEcho) {
                    echoCmd("ERROR - Failed to render " + subtaskID, 5);
                    errorEcho = true;
                }
            }

            if (line.toLowerCase().contains("time")) {
                computeSeconds = parseBlenderTime(line);
            }
        }

        int exitCode = renderProcess.waitFor();
        System.out.println("\nExited with error code : " + exitCode);

        return renderComplete;

    }

    public boolean startVrayRender(File renderfile, File tileScript) throws IOException, InterruptedException {
        boolean renderComplete = false;
        String subtaskID = taskParamsMap.get("subtaskID");
        String frameCategory = taskParamsMap.get("frameCategory");
        int renderFrame = Integer.parseInt(taskParamsMap.get("renderFrame"));
        String renderOutputType = taskParamsMap.get("renderOutputType");
        String renderResultName = String.format(subtaskID + "." + renderOutputType);
        File renderResult = new File(results, renderResultName);
        String rtEngine = "";

        if (Node.gpu != null && Node.gpu.equals("nvidia")) {
            rtEngine = " -rtEngine=5";
        }

        tileBorderMap = getTileBorders(tileScript, renderfile);

        if (frameCategory.contains("singleFrame")) {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "C: && cd \"" + vrayDir.getAbsolutePath() + "\" && vray"
                    + " -sceneFile=\"" + renderfile.getAbsolutePath() + "\""
                    + " -imgFile=\"" + renderResult.getAbsolutePath() + "\""
                    + " -region=" + tileBorderMap.get("left") + ";"
                    + tileBorderMap.get("bottom") + ";"
                    + tileBorderMap.get("right") + ";"
                    + tileBorderMap.get("top")
                    + " -display=0"
                    + rtEngine);

            renderProcess = processBuilder.start();

        } else if (frameCategory.contains("multiFrame")) {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "C: && cd \"" + vrayDir.getAbsolutePath() + "\" && vray"
                    + " -sceneFile=\"" + renderfile.getAbsolutePath() + "\""
                    + " -imgFile=\"" + renderResult.getAbsolutePath() + "\""
                    + " -region=" + tileBorderMap.get("left") + ";"
                    + tileBorderMap.get("bottom") + ";"
                    + tileBorderMap.get("right") + ";"
                    + tileBorderMap.get("top")
                    + " -frames=" + renderFrame + ";" + renderFrame
                    + " -display=0"
                    + rtEngine);

            renderProcess = processBuilder.start();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(renderProcess.getInputStream()));

        String line;
        boolean prepEcho = false;
        boolean renderEcho = false;
        boolean errorEcho = false;
        while ((line = reader.readLine()) != null) {
            log(line);

            if (line.toLowerCase().contains("preparing") && !renderComplete) {
                status.setText("Preparing render . . .");

                if (!prepEcho) {
                    echoCmd("Preparing render . . .", 5);
                    prepEcho = true;
                }

            } else if (line.toLowerCase().contains("rendering image") && !renderComplete) {
                status.setText("Rendering " + subtaskID);

                if (!renderEcho) {
                    echoCmd("Rendering " + subtaskID, 5);
                    renderEcho = true;
                }

            } else if (line.toLowerCase().contains("frame took") && !renderComplete) {
                String[] split = line.split("took");
                computeSeconds = Double.parseDouble(split[1].replaceAll("s.", ""));
                renderComplete = true;
                echoCmd("Completed " + subtaskID, 5);


            } else if (line.contains("Errors:") && !renderComplete) {
                status.setText("Failed to render " + subtaskID);
                Runtime.getRuntime().exec("taskkill /F /IM vray.exe");
                log("[ERROR] Render failed. Returning task to Server . . .");

                if (!errorEcho) {
                    echoCmd("ERROR - Failed to render " + subtaskID, 5);
                    errorEcho = true;
                }

            }
        }

        int exitCode = renderProcess.waitFor();
        System.out.println("\nExited with error code : " + exitCode);

        return renderComplete;
    }

    public double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public File zipFiles(File resultsDir, String subtaskID, ArrayList<String> filePaths) {
        File zippedFile = null;
        try {
            log("[LOG] Zipping " + subtaskID + " results . . .");
            String zipFileName = subtaskID.concat("_R.zip");
            for (int i=0; i<filePaths.size(); i++) {
                System.out.println("TASK | " + i + ". " + filePaths.get(i));
            }
            System.out.println("TASK | Zip File Name : " + zipFileName);
            zippedFile = new File(resultsDir, zipFileName);
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zippedFile)));

            for (String aFile : filePaths) {
                File file = new File(aFile);
                FileInputStream fis = new FileInputStream(file);
                ZipEntry zipEntry = new ZipEntry(file.getName());
                zos.putNextEntry(zipEntry);
                int length;
                byte[] bytes = Files.readAllBytes(Paths.get(aFile));

                while ((length = fis.read(bytes)) > 0) {
                    zos.write(bytes, 0, length);
                }
                fis.close();
                zos.closeEntry();
            }
            zos.finish();
            zos.close();

        } catch (FileNotFoundException ex) {
            System.err.println("[ERROR] A file does not exist: " + ex);
        } catch (IOException ex) {
            System.err.println("[ERROR] I/O error: " + ex);
        }
        return zippedFile;
    }

    public File cropResults(String subtaskID, String renderOutputType, ArrayList<String> filePaths, File vrayTileScript, File resultsDir) throws IOException {
        File renderResult = null;
        boolean cropped = false;
        try {
            log("[LOG] Cropping " + subtaskID + " results . . .");
            for (int i = 0; i < filePaths.size(); i++) {
                File result = new File(filePaths.get(i));

                log("[LOG] CHECK - File Paths Size : " + filePaths.size() + " | " + i + ". Cropping " + result.getName());

                // This part is eternally confusing.
                // V-ray crop/region renders by the order of "left;bottom;right;top" - left & right together is the render region's width with left being the start. top & bottom together is the render region's height with bottom being the start.
                // ImageIO.write uses Parameter 1 and 2 to determine the top-left corner as start of the cropping. The 3rd & 4th parameter determines the Width and Height to crop respectively.
                int cropStartX = tileBorderMap.get("left");                                     // 1. X-coordinate of Top-Left
                int cropStartY = tileBorderMap.get("bottom");                                   // 2. Y-coordinate of Top-Left
                int cropWidth = (tileBorderMap.get("right") - tileBorderMap.get("left"));       // 3. Width to Crop
                int cropHeight = (tileBorderMap.get("top") - tileBorderMap.get("bottom"));      // 4. Height to Crop

                BufferedImage image = ImageIO.read(result);
                BufferedImage img = image.getSubimage(cropStartX, cropStartY, cropWidth, cropHeight);
                BufferedImage croppedImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);

                Graphics g = croppedImg.createGraphics();
                g.drawImage(img, 0, 0, null);
                cropped = ImageIO.write(croppedImg, renderOutputType, result);
            }

            if (cropped) {
                renderResult = zipFiles(resultsDir, subtaskID, filePaths);
            } else {
                log("[ERROR] Failed to crop render result.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderResult;
    }

    public File checkResults(File resultsDir, String subtaskID, String renderOutputType, File tileScript) {
        File renderResult = null;
        String application = taskParamsMap.get("application");
        long resultsDirSize = FileUtils.sizeOf(resultsDir);

        try {
            if (resultsDirSize > 0) {
                TimeUnit.SECONDS.sleep(5);
                long resultsDirSize2 = FileUtils.sizeOf(resultsDir);

                if (resultsDirSize == resultsDirSize2) {
                    File[] listOfResults = resultsDir.listFiles();
                    ArrayList<String> filePaths = new ArrayList<>();

                    for (int i = 0; i < listOfResults.length; i++) {
                        File file = listOfResults[i];
                        String filename = file.getName();

                        if (filename.contains(subtaskID) && !filename.contains(".zip") && !filename.contains("_R.zip")) {
                            filePaths.add(file.getAbsolutePath());
                        }
                    }

                    if (filePaths.size() > 0 && application.contains("vray")) {

                        renderResult = cropResults(subtaskID, renderOutputType, filePaths, tileScript, resultsDir);
                    } else if (filePaths.size() > 0 && application.contains("blender")) {

                        renderResult = zipFiles(results, subtaskID, filePaths);
                    } else {
                        log("[ERROR] Results not found.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return renderResult;
    }

    public static File getResult(File resultsDir, String subtaskID) throws InterruptedException {
        File renderResult = null;
        String renderResultName = subtaskID + "_R.zip";
        File[] listOfResults = resultsDir.listFiles();

            if (listOfResults.length > 0) {

                for (int i = 0; i < listOfResults.length; i++) {
                    File file = listOfResults[i];
                    String filename = file.getName();

                    if (filename.equals(renderResultName)) {
                        renderResult = file;
                    }
                }
            }

        return renderResult;
    }

    public String setBlenderEngine(String application) {

        if (application.toLowerCase().contains("cycles")) {
            application = "CYCLES";
        } else if (application.toLowerCase().contains("eevee")) {
            application = "BLENDER_EEVEE";
        } else if (application.toLowerCase().contains("workbench")) {
            application = "BLENDER_WORKBENCH";
        }

        return application;
    }

    public String setBlenderDevice(String gpu) {
        String blenderDevice;
        if (gpu != null && gpu.equals("nvidia")) {
            blenderDevice = "CUDA";
        } else if (gpu != null && gpu.equals("amd")) {
            blenderDevice = "OPENCL";
        } else {
            blenderDevice = "CPU";
        }

        log("[LOG] Blender is now rendering with " + blenderDevice);

        return blenderDevice;
    }

    public static long parseBlenderTime(String timeString) {
        String[] split = timeString.toLowerCase().split("time");
        String time = split[1].substring(1,10).replaceAll(" ", "");

        String[] splitSecond = time.split(":");
        long minute = Long.parseLong(splitSecond[0]);
        long seconds = Long.parseLong(splitSecond[1].substring(0,2)) + (minute*60);

        return seconds;
    }

    public LinkedHashMap<String, Float> getDimensions(String taskID) throws FileNotFoundException {
       File dimensionFile = new File(taskCache, taskID + ".txt");
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

    public LinkedHashMap<String, Integer> getTileBorders(File vrayTileScript, File renderfile) throws IOException, NumberFormatException {

        LinkedHashMap<String, Integer> tileBorderMap = new LinkedHashMap<>();

        BufferedReader bufferedReader = new BufferedReader(new FileReader(vrayTileScript));
        String line = bufferedReader.readLine();
        String [] tileBorders = line.split(",");

        LinkedHashMap<String, Float> dimensions = getDimensions(taskParamsMap.get("taskID"));
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

    // This method sets up the task files needed for rendering. In sequence, TileScript (subtaskPackage), Renderfile & DimensionsScript (vray only)
    // The Renderfile is downloaded from nebula_dropbox. The TileScript is received as an HTTP Entity from nebula_server.
    // After retrieving both of these files and saving them to 'taskCache', a check is run to ensure everything is a go to begin rendering.
    public boolean setupTaskFiles(HttpEntity entity, LinkedHashMap<String, String> taskParamsMap) throws IOException {
        boolean taskSetup = false;

        // Download Renderfile and PackedSkp (vrscene and assets) through DBox share links
        status.setText("Setting up task files...");
        log("[LOG] Setting up task files...");

        // Download TileScripts from Server to Node
        File subtaskPackage = new File(taskCache, taskParamsMap.get("subtaskID").concat(".zip"));
        ReadableByteChannel rbc = Channels.newChannel(entity.getContent());
        FileOutputStream fos = new FileOutputStream(subtaskPackage);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fos.flush();
        fos.close();

        if (subtaskPackage != null && unzip(subtaskPackage, taskCache)) {

            taskSetup = checkTaskFiles(taskParamsMap.get("application"));
        } else {
            log("[ERROR] Subtask Package does not exist or failed to unzip.");
        }

        return taskSetup;
    }

    public File stripPathVRSCENE(File vrscene, String taskID) throws IOException {
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

            writeImageDimensionFile(imgWidth, imgHeight, taskCache, taskParamsMap.get("taskID"));

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            wr.close();
        }

        return newVRSCENE;
    }

    public File readVrsceneDimensions (File vrscene, String taskID) throws IOException {
        File dimensionsScript = null;

        FileInputStream fis = new FileInputStream(vrscene);
        InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);

        String imgWidth = null;
        String imgHeight = null;

        try {
            for(String line; (line = br.readLine()) != null; ) {
                 if (line.contains("img_width")) {
                    String[] split = line.split("=");
                    imgWidth = split[1].replaceAll(";", "");

                } else if (line.contains("img_height")) {
                    String[] split = line.split("=");
                    imgHeight = split[1].replaceAll(";", "");
                }
            }

            dimensionsScript = writeImageDimensionFile(imgWidth, imgHeight, taskCache, taskID);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return dimensionsScript;
    }

    public File writeImageDimensionFile(String imgWidth, String imgHeight, File destDir, String taskID) throws IOException {
        File dimensionsScript = new File(destDir, taskID + ".txt");
        PrintWriter fout = new PrintWriter(new FileWriter(dimensionsScript));

        fout.println(imgWidth + "," + imgHeight);

        fout.flush();
        fout.close();

        return dimensionsScript;
    }

    // This method ensures that taskfiles are in order before rendering. Namely, the Renderfile, TileScript & PackedSkp (VRAY) needed to render specific regions of the Renderfile.
    // If they're not in order, a boolean false is thrown to cause a Re-scheduling event. This acts as a failsafe to ensure no nebula_nodes are hogging onto a task and let server know to re-schedule.
    // TODO - THIS METHOD NEEDS TO BE OPTIMIZED. IT'S A CLUSTERFUCK OF IF STATEMENTS.
    public boolean checkTaskFiles(String application) {
        boolean tasksFilesReady = false;

        try {
            renderfile = checkRenderfile();
            tileScript = checkTileScript();

            if (application.contains("vray") &&
                    renderfile.length() > 0 &&
                    tileScript.length() > 0) {

                    dimensionScript = checkDimensionScript(renderfile);

                    if (dimensionScript != null && dimensionScript.length() > 0) {

                        packedSkp = checkPackedSkp();

                        if (packedSkp != null && packedSkp.length() > 0) {
                            tasksFilesReady = true;
                        } else {
                            log("[ERROR] FAILED TO SETUP PACKED_SKP.");
                        }
                    } else {
                        log("[ERROR] FAILED TO SETUP DIMENSION SCRIPT.");
                    }
            } else if (application.contains("blender") &&
                    renderfile.length() > 0 &&
                    tileScript.length() > 0) {

                    tasksFilesReady = true;
            } else {
                log("[ERROR] Task Files have failed to setup for 1 of 3 reasons. (1) Unknown application. (2) Renderfile not found/ downloaded properly. (3) TileScript not found/ downloaded properly. ");
                log("[ERROR] Renderfile : " + renderfile.getName() + " | Size : " + renderfile.length());
                log("[ERROR] TileScript : " + tileScript.getName() + " | Size : " + tileScript.length());

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return tasksFilesReady;
    }

    public static File checkFile(String filename, File dir, String action) {
        File file = null;

        if (dir.exists()) {
            ArrayList<File> fileArrayList = new ArrayList(Arrays.asList(dir.listFiles()));
            Iterator<File> iterator = fileArrayList.iterator();

            while (file == null && iterator.hasNext()) {
                File f = iterator.next();

                if (!f.isDirectory()) {
                    if (action.equals("contain") && f.getName().contains(filename)) {
                        file = f;
                        break;
                    } else if (action.equals("equal") && f.getName().equals(filename)) {
                        file = f;
                        break;
                    } else if (action.equals("end") && f.getName().endsWith(filename)) {
                        file = f;
                        break;
                    }
                } else {
                    file = checkFile(filename, f, action);
                }
            }
        }

        return file;
    }

    public File checkRenderfile() throws IOException {

        String application = taskParamsMap.get("application");
        String renderfileName = taskParamsMap.get("renderfileName");
        String taskID = taskParamsMap.get("taskID");
        String renderfileURL = taskParamsMap.get("renderfileURL");

        File renderfile = null;

        if (application.contains("vray")) {                                                                             // IF VRAY, Check for Stripped and Unstripped VRSCENE.
            String strippedRenderfileName = taskParamsMap.get("taskID") + ".vrscene";
            log("[LOG] Checking for " + strippedRenderfileName);

            renderfile = checkFile(strippedRenderfileName, taskCache, "contain");                                // renderfile = stripped;

            if (renderfile == null) {                                                                                   // If Stripped_VRSCENE is null, check for Unstripped_VRSCENE.
                log("[LOG] " + strippedRenderfileName + " not found. Checking for " + renderfileName);
                renderfile = checkFile(renderfileName, taskCache, "contain"); // renderfile = unstripped;

                if (renderfile == null) {                                                                               // If Both null, download VRSCENE from Dbx with TaskParamsMap parameters.
                    log("[LOG] " + renderfileName + " not found. Downloading from server. ");
                    File file = downloadFromDbx(renderfileURL, renderfileName); // renderfile = unstripped;
                    renderfile = setupRenderfile(file);
                } else {                                                                                                // If Unstripped_VRSCENE exists, Strip it first.
                    log("[LOG] " + renderfile.getName() + " found.");
                    renderfile = setupRenderfile(renderfile);
                }
            } else {
                log("[LOG] " + renderfile.getName() + " found.");
            }

        } else if (application.contains("blender")) {
            log("[LOG] Checking for " + renderfileName);
            renderfile = checkFile(renderfileName, taskCache, "contain");

            if (renderfile == null) {
                log("[LOG] " + renderfileName + " not found. Downloading from server. ");

                File file = downloadFromDbx(renderfileURL, renderfileName);
                renderfile = setupRenderfile(file);
            } else {
                renderfile = setupRenderfile(renderfile);
            }
        }

        return renderfile;
    }

    public File setupRenderfile(File renderfile) throws IOException {
        String taskID = taskParamsMap.get("taskID");
        String application = taskParamsMap.get("application");

        if (application.contains("vray") && renderfile.getName().contains("U")) {                                        // IF Vray, check if Render has been stripped of texture/asset paths. If not, StripPath method is called which initializes DimensionScript as well.
                                                                                                                         // If renderfile has been stripped, re-initialize DimensionsScript. If DimensionsScript doesn't exist, readVrsceneDimensions is called to create DimensionsScript.
            log("[LOG] Stripping VRSCENE asset paths...");
            renderfile = stripPathVRSCENE(renderfile, taskID);
            taskParamsMap.put("renderfileName", renderfile.getName());

        } else if (application.contains("blender") && !renderfile.getName().contains(".blend")) {

            if (unzip(renderfile, taskCache)) {
                renderfile = checkFile(".blend", taskCache, "end");
                taskParamsMap.put("blendfileName", renderfile.getName());

            } else {
                log("[LOG] Failed to unzip " + renderfile.getName());
            }
        }

        return renderfile;
    }

    public File checkDimensionScript(File vrscene) throws IOException {
        String dimensionScriptName = taskParamsMap.get("taskID") + ".txt";

        log("[LOG] Checking for " + dimensionScriptName);
        File dimensionScript = checkFile(dimensionScriptName, taskCache, "equal");

        if (dimensionScript == null) {
            log("[LOG] " + dimensionScriptName + " not found. Generating dimension script. ");
            dimensionScript = readVrsceneDimensions(vrscene, taskParamsMap.get("taskID"));
        }

        return dimensionScript;
    }


    public File checkTileScript() {
        String tileScriptName = taskParamsMap.get("tileScriptName");

        log("[LOG] Checking for " + tileScriptName);
        File tileScript = checkFile(tileScriptName, taskCache, "equal");

        if (tileScript != null) {
            return tileScript;
        } else {
            log("[ERROR] FAILED TO OBTAIN TILE SCRIPT.");
        }

        return null;
    }

    public File checkPackedSkp() throws IOException {
        File packedFile = null;
        try {
            String packedSkpName = taskParamsMap.get("packedSkpName");
            String packedSkpURL = taskParamsMap.get("packedSkpURL");

            log("[LOG] Checking for " + packedSkpName);
            packedFile = checkFile(packedSkpName, taskCache, "equal");

            if (packedFile == null) {
                log("[LOG] " + packedSkpName + " not found. Downloading " + packedSkpName + " from server. ");
                packedFile = downloadFromDbx(packedSkpURL, packedSkpName);
                checkPackedSkp();
            } else if (packedFile != null && unzip(packedFile, taskCache)) {
                log("[LOG] " + packedFile.getName() + " (" + packedFile.length() + ") unzipped to Task Cache Dir.");
            } else {
                log("[ERROR] " + packedFile.getName() + " (" + packedFile.length() + ") failed to unzip.");
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return packedFile;
    }

    // Lists any files in the given Directory.
    public List<File> listFilesInDir(File file) {

        File[] files = file.getAbsoluteFile().listFiles();
        Arrays.sort(files);//ensuring order 001, 002, ..., 010, ...
        log("[LOG] " + file.getName() + " | File Size: " + files.length);
        return Arrays.asList(files);
    }

    public File downloadFromDbx(String url, String filename) throws MalformedURLException {

        File file = new File(taskCache, filename);
        int renderfileSize = getFileSizeInKB(new URL(url));

        log("[LOG] Downloading " + filename + " . . .");

        try {
            URL download = new URL(url);
            ReadableByteChannel rbc = Channels.newChannel(download.openStream());
            FileOutputStream fileOut = new FileOutputStream(new File(taskCache.getAbsolutePath(), filename));
            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fileOut.flush();
            fileOut.close();
            rbc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return file;
    }

    public String buildResultParamsString(String cost,
                                          String nodeEmail,
                                          String deviceID,
                                          String ipAddress) {

        StringBuilder resultParams = new StringBuilder(
                        nodeEmail +
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
                        "," + taskParamsMap.get("renderfileName") +
                        "," + taskParamsMap.get("renderOutputType") +
                        "," + taskParamsMap.get("renderFrame") +
                        "," + taskParamsMap.get("uploadfileName") +
                        "," + taskParamsMap.get("userSubscription") +
                        "," + taskParamsMap.get("userAllowance") +
                        "," + taskParamsMap.get("computeRate"));

        return resultParams.toString();
    }

    public double getComputeSeconds() {
        return computeSeconds;
    }

    public int getFileSizeInKB(URL url) {
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


    public static File createNewFile(File destinationDir, String entryFileName) throws IOException {
        String newFileName = entryFileName;
        if (entryFileName.contains("\\")) {
            String[] split = entryFileName.split("\\\\");
            newFileName = split[split.length];
        } else if (entryFileName.contains("/")) {
            String[] split = entryFileName.split("/");
            newFileName = split[split.length - 1];
        }

        File destFile = new File(destinationDir, newFileName);

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + entryFileName);
        }

        return destFile;
    }
//    public static File createNewFile(File destinationDir, String entryFileName) throws IOException {
//        File destFile = new File(destinationDir, entryFileName);
//
//        String destDirPath = destinationDir.getCanonicalPath();
//        String destFilePath = destFile.getCanonicalPath();
//
//        if (!destFilePath.startsWith(destDirPath + File.separator)) {
//            throw new IOException("Entry is outside of the target dir: " + entryFileName);
//        }
//
//        return destFile;
//    }

    private boolean unzip(File file, File destDir) {
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
            log(e.getMessage());
            return false;
        }
        return unzipped;
    }

    public void echoCmd(String msg, int timeout) throws IOException {
        File batch = new File(taskCache, "echo.bat");

        PrintWriter fout = new PrintWriter(new FileWriter(batch));
        fout.println("@ECHO OFF");
        fout.println("cmd.exe /C echo \"" + msg + "\" && timeout /t " + timeout + " && exit");

        fout.flush();
        fout.close();

        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/C", "start", "\"" + msg + "\"", batch.getAbsolutePath());
        Process process = processBuilder.start();
    }

    public void log(String message) {
        logArea.append(message + "\n");
        System.out.println(message);
    }

    //    public File downloadRenderfileFromDbx(String url, String renderfileName, File taskCache, int renderfileLength) {
////        String fileName = String.format(FilenameUtils.getName(url)).replace("?dl=0", "");
////        String downloadURL = url.replace("?dl=0", "?dl=1");
//        File renderFile = new File(taskCache.getAbsolutePath(), renderfileName);
//
//        try {
//            URL download = new URL(url);
//            System.out.println("DOWNLOAD (DBOX) : " + renderFile.getName());
//            ReadableByteChannel rbc = Channels.newChannel(download.openStream());
//            FileOutputStream fileOut = new FileOutputStream(renderFile);
//            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
//            fileOut.flush();
//            fileOut.close();
//            rbc.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return renderFile;
//    }

//    public File downloadRenderfileFromGDrive(String url, String renderfileName, File originalTaskDir, int renderfileLength) {
//        String gdriveURL = url.replace("file/d/", "uc?export=download&id=");
//        gdriveURL = gdriveURL.replace("/view?usp=sharing", "");
//        File renderFile = new File(originalTaskDir.getAbsolutePath(), renderfileName);
//
//        try {
//            URL download = new URL(gdriveURL);
//            System.out.println("DOWNLOAD (GDRIVE) : " + download.openStream());
//            ReadableByteChannel rbc = Channels.newChannel(download.openStream());
//            FileOutputStream fileOut = new FileOutputStream(renderFile);
//            fileOut.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
//            fileOut.flush();
//            fileOut.close();
//            rbc.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return renderFile;
//    }

    //    public File writeBlenderCmd(File renderfile, File tileScript) throws IOException {
//        String subtaskID = taskParamsMap.get("subtaskID");
//        int renderFrame = Integer.parseInt(taskParamsMap.get("renderFrame"));
//        String renderOutputType = taskParamsMap.get("renderOutputType");
//        String scriptName = String.format(subtaskID + ".bat");
//        File renderBatchScript = new File(taskCache, scriptName);
//        String application = taskParamsMap.get("application");
//
//        // blender.exe -b taskcache/blendfile.blend -E blenderEngine
//
//        PrintWriter fout = new PrintWriter(new FileWriter(renderBatchScript));
//        fout.println("@ECHO OFF");
//        fout.println("cmd.exe /C \" cd\\ && C: && cd " + blenderDir.getAbsolutePath()                              // TODO - BLENDER COMMAND LINE
//                + " && blender.exe -b \"" + renderfile.getAbsolutePath() + "\""
//                + " -E " + setBlenderEngine(application)
//                + " --python " + taskCache.getAbsolutePath() + File.separator + tileScript.getName()
//                + " -o " + results.getAbsolutePath() + File.separator + subtaskID
//                + " -F "   + renderOutputType.toUpperCase()
//                + " -f " + renderFrame
//                + " -- --cycles-device " + setBlenderDevice(Node.gpu)
//                + "\" && exit");
//
//        fout.flush();
//        fout.close();
//
//        return renderBatchScript;
//    }

//    public File writeVrayRenderCmd(File renderfile, File tileScript) throws IOException {
//
//        String subtaskID = taskParamsMap.get("subtaskID");
//        String frameCategory = taskParamsMap.get("frameCategory");
//        int renderFrame = Integer.parseInt(taskParamsMap.get("renderFrame"));
//        String renderOutputType = taskParamsMap.get("renderOutputType");
//        String rtEngine = "";
//
//        if (Node.gpu != null && Node.gpu.equals("nvidia")) {
//            rtEngine = "-rtEngine=5";
//        }
//
//        String scriptName = String.format(subtaskID + ".bat");
//        String renderResultName = String.format(subtaskID + "." + renderOutputType);
//
//        File renderBatchScript = new File(taskCache, scriptName);
//        File renderResult = new File(results, renderResultName);
//        tileBorderMap = getTileBorders(tileScript, renderfile);
//
//        if (frameCategory.contains("singleFrame")) {
//            PrintWriter fout = new PrintWriter(new FileWriter(renderBatchScript));
//            fout.println("@ECHO OFF");
//
//            fout.println("cmd.exe /C \" cd\\ && C: && cd " + vrayDir.getAbsolutePath() +
//                    "&& vray -sceneFile=" + renderfile.getAbsolutePath()
//                    + " -imgFile=" + renderResult.getAbsolutePath()
//                    + " -region="   + tileBorderMap.get("left") + ";"
//                    + tileBorderMap.get("bottom") + ";"
//                    + tileBorderMap.get("right") + ";"
//                    + tileBorderMap.get("top") + "\" -display=0 -autoClose=1 "
//                    + rtEngine + " && exit");
//
//            fout.flush();
//            fout.close();
//
//        } else if (frameCategory.contains("multiFrame")) {
//
//            PrintWriter fout = new PrintWriter(new FileWriter(renderBatchScript));
//            fout.println("@ECHO OFF");
//            fout.println("cmd.exe /C \" cd\\ && C: && cd " + vrayDir.getAbsolutePath() +
//                    "&& vray -sceneFile=" + renderfile.getAbsolutePath()
//                    + " -imgFile=" + renderResult.getAbsolutePath()
//                    + " -region="   + tileBorderMap.get("left") + ";"
//                    + tileBorderMap.get("bottom") + ";"
//                    + tileBorderMap.get("right") + ";"
//                    + tileBorderMap.get("top")
//                    + "-frames=" + renderFrame + ";" + renderFrame + "\" -display=0 -autoClose=1 "
//                    + rtEngine + " && exit");
//
//            fout.flush();
//            fout.close();
//        }
//
//        return renderBatchScript;
//    }

    //    public boolean startRendering() {
//        boolean rendered = false;
//        File renderResult = null;
//        String application = taskParamsMap.get("application");
//
//        try {
//            if (application.contains("blender")) {
//
//                File renderBatchScript = writeBlenderCmd(renderfile, tileScript);
//                StopWatch stopWatch = new StopWatch();
//
//                ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/K", "start", renderBatchScript.getAbsolutePath());
//                renderProcess = processBuilder.start();
//
//                stopWatch.start();
//                double currentTime = 0.0;
//                boolean overtime = false;
//
//                while (renderProcess.isAlive()) {
//
//                    status.setText("Rendering " + taskParamsMap.get("subtaskID"));
//
//                    currentTime = round(stopWatch.getTime() / 1000, 4);
//                    renderResult = checkResults(results,
//                            taskParamsMap.get("subtaskID"),
//                            taskParamsMap.get("renderOutputType"),
//                            tileScript);
//
//                    if (renderResult != null && renderResult.length() > 0) {
//                        System.out.println("[LOG] RENDER RESULT : " + renderResult.getName() + " | SIZE : " + renderResult.length());
//
//                        rendered = true;
//                        stopWatch.stop();
//                        renderProcess.destroy();
//                        computeSeconds = round((stopWatch.getTime() / 1000), 4);
//                        break;
//                    }
//                }

//                if (docker.checkForApplication(application)) {
//
//                    if (docker.compute(taskParamsMap, taskCache)) {
//
//                        while (docker.computeStatus()) {
//                            renderResult = checkResults(results,
//                                    taskParamsMap.get("subtaskID"),
//                                    taskParamsMap.get("renderOutputType"),
//                                    tileScript);
//
//                            if (renderResult != null) {
//                                rendered = true;
//                                computeSeconds = docker.getComputeTime("nebula_" + taskParamsMap.get("subtaskID"));
//                                break;
//                            }
//                        }
//
//                        TimeUnit.SECONDS.sleep(3);
//                        renderResult = checkResults(results,
//                                taskParamsMap.get("subtaskID"),
//                                taskParamsMap.get("renderOutputType"),
//                                tileScript);
//
//                        if (renderResult != null) {
//                            rendered = true;
//                            computeSeconds = docker.getComputeTime("nebula_" + taskParamsMap.get("subtaskID"));
//                        }
//                    } else {
//                        log("[LOG] Docker failed to render " + taskParamsMap.get("subtaskID") + ". Returning task to server.");
//                    }
//                }
//            } else if (application.contains("vray")) {

//                File renderBatchScript = writeVrayRenderCmd(renderfile, tileScript);
//                StopWatch stopWatch = new StopWatch();
//
//                ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/K", "start", renderBatchScript.getAbsolutePath());
//                renderProcess = processBuilder.start();
//
//
//                stopWatch.start();
//                double currentTime = 0.0;
//                boolean overtime = false;
//
//                while (renderProcess.isAlive()) {
//
//                    status.setText("Rendering " + taskParamsMap.get("subtaskID"));
//
//                    currentTime = round(stopWatch.getTime() / 1000, 4);
//                    renderResult = checkResults(results,
//                            taskParamsMap.get("subtaskID"),
//                            taskParamsMap.get("renderOutputType"),
//                            tileScript);
//
//                    if (renderResult != null && renderResult.length() > 0) {
//                        System.out.println("[LOG] RENDER RESULT : " + renderResult.getName() + " | SIZE : " + renderResult.length());
//
//                        rendered = true;
//                        stopWatch.stop();
//                        renderProcess.destroy();
//                        computeSeconds = round((stopWatch.getTime() / 1000), 4);
//                        break;
//                    }
//                }
//            } else {
//                log("[ERROR] Failed to render. ");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return rendered;
//    }


}


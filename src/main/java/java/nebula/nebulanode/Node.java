package nebula.nebulanode;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by Daryl Wong on 3/26/2019.
 */
public class Node {

    private String username;
    private String deviceID;
    private String subtaskID;
    private String taskID;
    private String application;
    private String blenderCL;
    private String subtaskLength;
    private String tileScriptName;

    File taskCache = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulanode\\taskcache");
    File sharedDrive = new File("C:\\Users\\Daryl\\Google Drive\\render\\");


    // nebulanode.Node nebulanode.Compute - TaskCache to nebulanode.Compute App Info :
    // How do we store information of Application and pass it from NodeServlet to nebulanode.Compute.

    // Option 1 : Decide Application info based on Renderfile extension / file format                                   // LEAST PREFERRED
    //          - PROS : No need for Application info to be transferred through headers
    //          - CONS : If different applications share file formats, there will be discrepancies.

    // Option 2 : Renderfile maintaining data when in taskcache for retrieval later                                     // Preferred
    //          - PROS : High accuracy and consistency. Maintain Client selected information from end-to-end
    //          - CONS : Unsure if it's possible or works ******

    // Option 3 : HashMap of Subtask and Application info - Key : SubtaskID  |  Value : ApplicationInfo                 // Second Option
    //          - PROS : High accuracy and consistency. App information maintained within the same class.
    //          - CONS : Added complexity. HashMap needs to continually add to the list of TaskID:Apps




    public static void main(String[] args) throws IOException {

        Node node = new Node("mrnode", "node123456");
        node.postStatus();
//        node.computeTask();
    }

    public Node(String username, String deviceID) {
        this.username = username;
        this.deviceID = deviceID;
    }

    public void pingServer() throws IOException {
        Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    postStatus();
//                    postResults();
                    System.out.println("This is running.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1*60*1000, 2*60*1000);
//        compute();
    }

    public void postStatus() throws IOException { // Sends nebulanode.Node Status to Server as request for Tasks. Should receive 2 Items - (a) Docker Blender . (b) Task data

        CloseableHttpClient httpClient = HttpClients.createDefault();

        Queue<String> queue = new LinkedList<>();

        String score = "95";
        int counter = 0;

        // Collection of nebulanode.Node Status - Queue, Score (Requires proper automation*****)
        // 1. Retrieve username and device ID of Supply User
        // 2. Create live queue of jobs being done
        // 3. Create algorithm to measure Device Score

        try {
            // Build entity for passing nebulanode.Node status and parameters.
            HttpEntity data = EntityBuilder.create()
                    .setContentEncoding("UTF-8")
                    .setContentType(ContentType.APPLICATION_FORM_URLENCODED)
                    .setParameters(new BasicNameValuePair("username", "darylwong")
                            , new BasicNameValuePair("deviceID", "node123")
                            , new BasicNameValuePair("queue", Integer.toString(queue.size()))
                            , new BasicNameValuePair("score", score))
                    .build();

            HttpUriRequest request = RequestBuilder
                    .post("http://localhost:8080/scheduler")
                    .setEntity(data)
                    .build();

            CloseableHttpResponse response = httpClient.execute(request);

            HttpEntity entity = response.getEntity();

            if (entity != null) {
                long taskSize = entity.getContentLength();
                System.out.println("Entity length : " + taskSize);
                entity.getContent();

                    Header[] headers = response.getAllHeaders();
                    for (Header header : headers) {
                        System.out.println("Key : " + header.getName() + " | Value : " + header.getValue());
                    }

                    tileScriptName = response.getFirstHeader("Tile-Script").getValue();
                    taskID = response.getFirstHeader("Task-Identity").getValue();
                    subtaskID = response.getFirstHeader("Subtask-Identity").getValue();
                    application = response.getFirstHeader("Application").getValue();
                    blenderCL = response.getFirstHeader("Blender-CL").getValue();
                    subtaskLength = response.getFirstHeader("Subtask-Length").getValue();

                    File taskPackage = new File(taskCache, taskID.concat(".zip"));
                    FileOutputStream fos = new FileOutputStream(taskPackage);
                    entity.writeTo(fos);
                    fos.close();
                    queue.add(taskID);
                    unzip(taskPackage, taskCache);

            } else if (entity == null) {
                System.out.println("entity null.");
            }

            System.out.println("Executing request " + request.getRequestLine());
            int status = response.getStatusLine().getStatusCode();
            System.out.println("Status Code for POST : " + status);

        } finally {
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

    public static String getMetadata(Path metaPath, String metaName) {                                                                       // Getting Application info from Subtask attributes for ComputeTask.
        String metaValue = null;
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(metaPath, UserDefinedFileAttributeView.class);
            ByteBuffer buffer = ByteBuffer.allocate(view.size(metaName));
            view.read(metaName, buffer);
            buffer.flip();
            metaValue = Charset.defaultCharset().decode(buffer).toString();
            System.out.println("TEST | Attribute : " + metaValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metaValue;
    }

    private static void unzip(File zipFile, File destDir) {
        FileInputStream fis;
        byte[] buffer = new byte[(int) zipFile.length()];
        try {
            fis = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while(ze != null){
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                System.out.println("Unzipping to " + newFile.getAbsolutePath());
                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int length;
                while ((length = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            //close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void compute() throws IOException { // Auto-fill and execute Docker command to render.

        if (taskCache.listFiles().length <= 0) {
            System.out.println("There are no tasks to compute at this time.");
        } else if (taskCache.listFiles().length > 0) {

            // Creating Docker configurations, Docker Client and Docker Container to compute.
            DefaultDockerClientConfig config                                                                            // Docker Configurations : - Required for building private registry for Nebula DockerImages.
                    = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withRegistryEmail("darylgabrielwong@gmail.com")                                                    // DockerHub Login Email
                    .withRegistryPassword("DWGabriel4")                                                                 // DockerHub Login Password
                    .withRegistryUsername("darylgabrielwong")                                                           // DockerHub username (DockerHub account registry name as well)
                    .build();

            DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();                                // Build Docker client to communicate with DockerHub,Server,Host,etc.

            try (Stream<Path> fileStream = Files.walk(Paths.get(taskCache.getAbsolutePath()))) {                        // ***** Iterates the computation through subtasks in TashCache in nebulanode.Node Device.
                fileStream                                                                                              // ***** Requires Equalizer to be integrated, current splitting method doesnt work, therefore subtasks can't be computed.
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .forEach(file -> {
                            System.out.println(file.getName());
                            String this_subtaskID = getMetadata(file.toPath(), "Subtask-Identity");
                            String this_application = getMetadata(file.toPath(), "Application");
                            String this_blenderCL = getMetadata(file.toPath(), "Blender-CL");
                            System.out.println("Compute | Application : " + this_application);

            try {

//                docker run -it --rm -v ~/desktop/test:/test ikester/blender test/bmwgpu.blend
//                --python test/thescript.py -o /test/frame_### -f 1

                                String nodeDir = "/c/Users/Daryl Wong/desktop/nebula/code/nebulanode/taskcache";        // Bind Volume for TaskCache and Results directory
                                String destinationDir = "/results/";
                                String bindVolume = String.format(nodeDir + ":" + destinationDir);

                                CreateContainerResponse container = dockerClient.createContainerCmd(this_application)        // Create Container with Image (Selected Application)
                                        .withCmd(this_blenderCL)                                                            // Container start-up command
                                        .withName(this_subtaskID)                                                            // Name of container
                                        .exec();

                                dockerClient.startContainerCmd(container.getId()).exec();                               // Start container.

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
            }
        }
    }

    public void postResults() throws IOException {                 // TODO - Docker container either returns results directly to volume mounted in Server OR NodeDevice. // Returns computed results back to Server for merging and verification.

        File taskcache = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulanode\\taskcache");
        File results = new File("C:\\Users\\Daryl\\Desktop\\Nebula\\Code\\nebulanode\\results");

        if (taskcache.listFiles() == null || taskcache.listFiles().length < 1) {
            System.out.println("All tasks returned.");
        } else if (taskcache.listFiles() != null) {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            File renderResult = Files.walk(Paths.get(results.getAbsolutePath()))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .findFirst()
                    .get();

            //taskID = TODO - getTaskID from either : (a) getMetadata - or (b)

            System.out.println("Subtask ID : " + subtaskID);
            System.out.println("Task ID : " + taskID);


            try {
                // build multipart upload request - Requires integration of HTML/JSP file for interacting with webpage : request.getRequestDispatcher("WEB-INF/Resources/file.html").forward(request, response).
                HttpEntity data = MultipartEntityBuilder.create()
                        .addPart("subtask", new FileBody(renderResult))
                        .addTextBody("username", "mrnode")
                        .addTextBody("deviceID", "node456789")
                        .addTextBody("taskidentity", taskID)
                        .addTextBody("subtaskidentity", subtaskID)
                        .build();


                // build http request and assign multipart upload data
                HttpUriRequest request = RequestBuilder
                        .post("http://localhost:8080/complete")
                        .setEntity(data)
                        .build();

                CloseableHttpResponse response = httpClient.execute(request);

                System.out.println("Executing request " + request.getRequestLine());
                int status = response.getStatusLine().getStatusCode();
                System.out.println("Status Code for POST : " + status);
                response.close();
                httpClient.close();
                renderResult.delete();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        }
    }



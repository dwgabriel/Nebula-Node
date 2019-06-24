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
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Daryl Wong on 3/26/2019.
 */
public class Node {

    String username;
    String deviceID;
    String taskID;
    String application;

    Compute computer = new Compute();

    Map<String, String> taskAppMap = new LinkedHashMap<>();

    // Node Compute - TaskCache to Compute App Info :
    // How do we store information of Application and pass it from NodeServlet to Compute.

    // Option 1 : Decide Application info based on Renderfile extension / file format                                   // LEAST PREFERRED
    //          - PROS : No need for Application info to be transferred through headers
    //          - CONS : If different applications share file formats, there will be discrepancies.

    // Option 2 : Renderfile maintaining data when in taskcache for retrieval later                                     //
    //          - PROS : High accuracy and consistency. Maintain Client selected information from end-to-end
    //          - CONS : Unsure if it's possible or works ******

    // Option 3 : HashMap of Subtask and Application info - Key : SubtaskID  |  Value : ApplicationInfo
    //          - PROS : High accuracy and consistency. App information maintained within the same class.
    //          - CONS : Added complexity. HashMap needs to continually add to the list of TaskID:Apps




    public static void main(String[] args) throws IOException {

        Node node = new Node("mrnode", "node123456");
        node.postStatus();
        System.out.println(" POST CHECK ----------------------------------------------------");
        node.printInfo();
//        node.pingServer();

//        node.postResults();


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
                    System.out.println("This is running.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1*60*1000, 2*60*1000);
    }

    public void postStatus() throws IOException { // Sends Node Status to Server as request for Tasks. Should receive 2 Items - (a) Docker Blender . (b) Task data

        CloseableHttpClient httpClient = HttpClients.createDefault();

        File taskCache = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulanode\\taskcache");

        // Collection of Node Status - Queue, Score (Requires proper automation*****)
        // 1. Retrieve username and device ID of Supply User
        // 2. Create live queue of jobs being done
        // 3. Create algorithm to measure Device Score

        File[] queue = taskCache.listFiles();

        String score = "95";
        int counter = 0;


        try {
            // Docker pull App-Container based on info passed by Server
            /// [Insert code to automate Docker Pull]

            // Build entity for passing Node status and parameters.
            HttpEntity data = EntityBuilder.create()
                    .setContentEncoding("UTF-8")
                    .setContentType(ContentType.APPLICATION_FORM_URLENCODED)
                    .setParameters(new BasicNameValuePair("username", "darylwong")
                            , new BasicNameValuePair("deviceID", "node123")
                            , new BasicNameValuePair("queue", Integer.toString(queue.length))
                            , new BasicNameValuePair("score", score))
                    .build();

            // Builds POST Request for the server
            HttpUriRequest request = RequestBuilder
                    .post("http://localhost:8080/jobsplease")
                    .setEntity(data)
                    .build();

            CloseableHttpResponse response = httpClient.execute(request);

            // Retrieves Sub-tasks from Server using Entity (Response-Body)
            HttpEntity entity = response.getEntity();

            if (entity != null) {

                long taskSize = entity.getContentLength();
                entity.getContent();

                if (taskSize < 10) { // Needs optimal task Size **
                    System.out.println("Problem's here.");
                    String content = EntityUtils.toString(entity);
                    System.out.println("Content  : " + content);

                } else {

                    Header[] headers = response.getAllHeaders();
                    for (Header header : headers) {
                        System.out.println("Key : " + header.getName() + " ,Value : " + header.getValue());
                    }

                    String fileName = response.getFirstHeader("Content-Name").getValue();
                    taskID = response.getFirstHeader("Task-Identity").getValue();
                    application = response.getFirstHeader("Application").toString();
                    System.out.println("--------------------------------- PRINT CHECK ---------------------------------");
                    System.out.println("File Name : " + fileName);
                    System.out.println("Task Identity : " + taskID);
                    System.out.println("Application : " + application);
                    System.out.println("--------------------------------- Task App Map --------------------------------");
                    addInfo(taskID, application);
                    printInfo();

                    File newFile = new File(taskCache, fileName);
                    taskCache.createNewFile();

                    FileOutputStream fos = new FileOutputStream(newFile);
                    entity.writeTo(fos);
                    fos.close();
                }
            } else if (entity == null) {
                System.out.println("entity null.");
            }

            System.out.println("Executing request " + request.getRequestLine());
            int status = response.getStatusLine().getStatusCode();
            System.out.println("Status Code for POST : " + status);

        } finally {
            httpClient.close();
        }
//        computer.computeTask(application, taskID);

    }

    public void addInfo(String taskID, String application) {
        taskAppMap.put(taskID, application);
    }

    public void printInfo() {
        taskAppMap.entrySet().forEach(entry -> {
            System.out.println("TEST | " + entry.getKey() + " = " + entry.getValue());
        });
    }

    public void postResults() throws IOException { // Returns computed results back to Server for merging and verification.

        CloseableHttpClient httpClient = HttpClients.createDefault();

        File taskcache = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulanode\\taskcache");

//         1. Need better algorithm for cycling through files (Stupid Option : 2 directory of files, and delete as you go.)
        if (taskcache.listFiles() == null || taskcache.listFiles().length < 1) {
            System.out.println("All tasks returned.");
        } else if (taskcache.listFiles() != null) {

            File file = Files.walk(Paths.get(taskcache.getAbsolutePath())).filter(Files::isRegularFile).map(Path::toFile).findFirst().get();
            String fileName = file.getName();
            String taskID = fileName.substring(4, fileName.length());
            System.out.println("File Name : " + fileName);
            System.out.println("Task ID : " + taskID);


            try {
                // build multipart upload request - Requires integration of HTML/JSP file for interacting with webpage : request.getRequestDispatcher("WEB-INF/Resources/file.html").forward(request, response).
                HttpEntity data = MultipartEntityBuilder.create()
                        .addPart("file", new FileBody(file))
                        .addTextBody("username", "mrnode")
                        .addTextBody("deviceID", "node456789")
                        .addTextBody("taskidentity", taskID)
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
                file.delete();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        }
    }



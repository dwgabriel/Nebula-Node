
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Test {

    static File userHome = new File(System.getProperty("user.home"));
    //    static File userHome = new File(System.getProperty("user.home"), "/Library/Application Support");
    static File appData = new File(System.getenv("APPDATA"));
    static File nebulaData = new File(appData, "Nebula");
    static File taskCache = new File(nebulaData, "taskcache");
    static File results = new File(taskCache, "results");

    private static DecimalFormat timeFormat = new DecimalFormat("#.##");
    private static DefaultDockerClientConfig config;
    private static DockerClient dockerClient;
//    static final DbxRequestConfig dbxConfig = DbxRequestConfig.newBuilder("dropbox/nebula-render").build();
//    static final DbxClientV2 client = new DbxClientV2(dbxConfig, "rM8fF-GuUNAAAAAAAAAAK6ksJER9acjYeF1krFbX63InD8wn_Iq-5fDlV_1YM6gh");
    private static boolean updated = false;

    public static void main(String[] args) {
        try {
            


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean pullBlender() throws InterruptedException {
        boolean blenderImagePulled = false;

        config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://192.168.99.100:2376")
                .withDockerTlsVerify(true)
                .withDockerCertPath(userHome + "/.docker/machine/certs")
                .withDockerConfig(userHome + "/.docker")
                .withRegistryEmail("darylgabrielwong@gmail.com")
                .withRegistryUsername("darylgabrielwong")
                .withRegistryPassword("DWGabriel4")
                .build();

//        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
//                .dockerHost(config.getDockerHost())
//                .sslConfig(config.getSSLConfig()).build();
//
//        dockerClient = DockerClientImpl.getInstance(config, dockerHttpClient);

        dockerClient = DockerClientBuilder.getInstance(config)
                .build();

        dockerClient.pullImageCmd("alpine")
                .exec(new PullImageResultCallback())
                .awaitCompletion(300, TimeUnit.SECONDS);

         // TODO - HARDCODED, SHOULD NOT RELY ON TIME.

//        ListImagesCmd images = dockerClient.listImagesCmd();
        List<Image> images = dockerClient.listImagesCmd().exec();
        Iterator<Image> iterator = images.iterator();
        while (iterator.hasNext()) {
            System.out.println(iterator.next().toString());
            if (iterator.next().toString().contains("blender")) {
                blenderImagePulled = true;
            }
        }
//        System.out.println("DOCKER IMAGE : " + images);
//        if (images.toString().contains("blender")) {
//            blenderImagePulled = true;
//        }


        return blenderImagePulled;
    }

}

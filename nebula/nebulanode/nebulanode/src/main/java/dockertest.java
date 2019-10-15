
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.PruneType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.jaxrs.PruneCmdExec;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.tomcat.jni.OS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class dockertest {

    static File userHome = new File(System.getProperty("user.home"));
//    static File userHome = new File(System.getProperty("user.home"), "/Library/Application Support");
    static File appData = new File(System.getenv("APPDATA"));
    static File nebulaData = new File(appData, "Nebula");
    static File taskCache = new File(nebulaData, "taskcache");
    static File results = new File(taskCache, "results");
    static String containerName;

    static DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("tcp://192.168.99.100:2376")
            .withDockerTlsVerify(true)
            .withDockerCertPath(userHome + "/.docker/machine/certs")
            .withDockerConfig(userHome + "/.docker")
            .withRegistryEmail("darylgabrielwong@gmail.com")
            .withRegistryUsername("darylgabrielwong")
            .withRegistryPassword("DWGabriel4")
            .build();

    static DockerClient dockerClient = DockerClientBuilder.getInstance(config)
            .build();

    public static void main(String[] args) {

        String vol = createVolume(taskCache.getAbsolutePath());


//        System.out.println(vol);
//        System.out.println(taskCache.getAbsolutePath());

        compute();

    }

    public static String createVolume(String taskCachePath) {
        String bindVolume;

            taskCachePath = String.format("/" + taskCachePath);
            taskCachePath = taskCachePath.replaceAll(":", "");
            taskCachePath = taskCachePath.replace("\\", "/");
            taskCachePath = taskCachePath.replace("/C/", "/c/");

            bindVolume = String.format(taskCachePath + ":/taskcache");

            return bindVolume;
    }

    public static void compute() {
        System.out.println("App Data : " + userHome.getAbsolutePath());
        System.out.println("Database located at : " + nebulaData.getAbsolutePath());

        ArrayList<String> blenderCL = new ArrayList<>();

        blenderCL.add("taskcache/blendfile.blend");
        blenderCL.add("--python");
        blenderCL.add("taskcache/thescript.py");
        blenderCL.add("-o");
        blenderCL.add("/taskcache/results/frame_###");
        blenderCL.add("-f");
        blenderCL.add("1");

        String containerID = null;

        try {

//            docker run -it -v taskCache:taskcache ikester/blender taskcache/blendfile.blend --python taskcache/thescript.py -o taskcache/frame_### -f 1
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("tcp://192.168.99.100:2376")
                    .withDockerTlsVerify(true)
                    .withDockerCertPath(userHome + "/.docker/machine/certs")
                    .withDockerConfig(userHome + "/.docker")
                    .withRegistryEmail("darylgabrielwong@gmail.com")
                    .withRegistryUsername("darylgabrielwong")
                    .withRegistryPassword("DWGabriel4")
                    .build();

            DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                    .build();

            String vol = createVolume(taskCache.getAbsolutePath());
            System.out.println(vol);

            CreateContainerResponse container = dockerClient.createContainerCmd("ikester/blender")
                    .withCmd("bin/bash")
                    .withName("nebula2")
                    .withBinds(Bind.parse(vol))
                    .withCmd(blenderCL.get(0), blenderCL.get(1), blenderCL.get(2), blenderCL.get(3), blenderCL.get(4), blenderCL.get(5), blenderCL.get(6))
                    .exec();

            dockerClient.startContainerCmd(container.getId()).exec();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void checkContainers() {

        List<Container> containers = dockerClient.listContainersCmd().exec();

        if (containers.size() > 0) {
            System.out.println("Containers : " + containers.size());
        } else {
            System.out.println("Containers : " + containers.size());
        }
    }
}

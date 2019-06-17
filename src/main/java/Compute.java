import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Created by Daryl Wong on 4/8/2019.
 */
public class Compute {

    File taskCache = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulanode\\taskcache");


    // Compute class that allows Node to compute all pending and incoming tasks.
    // 1. Checks the Task Cache for pending tasks.
    // 2. Computes any existing tasks using compute() method.
    //      2a. - Unpack task to retrieve Function and Data for computation.
    //      2b. - Perform Function on Data.

    public void computeTask(String application, String taskID) throws IOException { // Auto-fill and execute Docker command to render.

        // Docker Pull to initiate App container.
        DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withRegistryEmail("darylgabrielwong@gmail.com")
                .withRegistryPassword("DWGabriel4")
                .withRegistryUrl("darylgabrielwong")
                .build();

        final DockerClient dockerClient = DockerClientBuilder
                .getInstance(config)
                .build();

        CreateContainerResponse container
                = dockerClient.createContainerCmd("/bin/bash")
                .withName("blender")
                .withHostName("deviceID")
                .withBinds(Bind.parse("C:\\Users\\Daryl Wong\\Desktop\\test:/test/"))
                .withWorkingDir("C:\\Users\\Daryl Wong\\Desktop\\test\\blendertest.blend")
                .withHostConfig(new HostConfig() {
                    @JsonProperty("AutoRemove")
                    public boolean autoRemove = true;
                })
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();


        if (taskCache.listFiles().length <= 0) {
            System.out.println("There are no tasks to compute at this time.");
        } else if (taskCache.listFiles().length > 0) {

            try (Stream<Path> fileStream = Files.walk(Paths.get(taskCache.getAbsolutePath()))) {
                fileStream
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .forEachOrdered(file ->  {
                            System.out.println(file.getName());

                            try {
                                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
            }
        }
    }
}

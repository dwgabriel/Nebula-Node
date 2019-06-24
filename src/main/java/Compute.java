import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
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

        // Creating Docker configurations, Docker Client and Docker Container to compute.
        DefaultDockerClientConfig config                                                                                // Dccker Configurations : - Required for building private registry for Nebula DockerImages.
                = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withRegistryEmail("darylgabrielwong@gmail.com")                                                        // DockerHub Login Email
                .withRegistryPassword("DWGabriel4")                                                                     // DockerHub Login Password
                .withRegistryUsername("darylgabrielwong")                                                               // DockerHub username (DockerHub account registry name as well)
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();                                    // Build Docker client to communicate with DockerHub,Server,Host,etc.

        CreateContainerResponse container = dockerClient.createContainerCmd("ikester/blender")                       // Create Container with Image (Selected Application)
                .withCmd("bin/bash")                                                                                    // Container start-up command
                .withName("nodecompute")                                                                                // Name of container
                .withBinds(Bind.parse("/c/Users/Daryl Wong/desktop/nebula/code/nebulanode/taskcache:/test/"))           // Mount-bind Volume - [Node Directory where Renderfile is Stored] : [Destination director where results will be Stored]
                .withCmd("test/blendertest.blend", "-o", "test/frame_###", "-f", "1")                                   // Arguments passed to Application - Renderfile, naming of Results (frame_###), Selection of frame to render (-f)
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();                                                       // Start container.

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

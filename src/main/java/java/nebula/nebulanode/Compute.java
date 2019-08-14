package nebula.nebulanode;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.SearchItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by Daryl Wong on 4/8/2019.
 */
public class Compute {

    File taskCache = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulanode\\taskcache");


    // nebulanode.Compute class that allows nebulanode.Node to compute all pending and incoming tasks.
    // 1. Checks the Task Cache for pending tasks.
    // 2. Computes any existing tasks using compute() method.
    //      2a. - Unpack task to retrieve Function and Data for computation.
    //      2b. - Perform Function on Data.

    public void computeTask() throws IOException { // Auto-fill and execute Docker command to render.

        if (taskCache.listFiles().length <= 0) {
            System.out.println("There are no tasks to compute at this time.");
        } else if (taskCache.listFiles().length > 0) {

            // Creating Docker configurations, Docker Client and Docker Container to compute.
            DefaultDockerClientConfig config                                                                                // Dccker Configurations : - Required for building private registry for Nebula DockerImages.
                    = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withRegistryEmail("darylgabrielwong@gmail.com")                                                        // DockerHub Login Email
                    .withRegistryPassword("DWGabriel4")                                                                     // DockerHub Login Password
                    .withRegistryUsername("darylgabrielwong")                                                               // DockerHub username (DockerHub account registry name as well)
                    .build();

            DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();                                    // Build Docker client to communicate with DockerHub,Server,Host,etc.

            try (Stream<Path> fileStream = Files.walk(Paths.get(taskCache.getAbsolutePath()))) {
                fileStream
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .forEachOrdered(file ->  {
                            System.out.println(file.getName());
                            String subtaskID = file.getName();
                            String application = getMetadata(subtaskID);

                            try {
                                String nodeDir = "/c/Users/Daryl Wong/desktop/nebula/code/nebulanode/taskcache";
                                String destinationDir = "/results/";
                                String bindVolume = String.format(nodeDir + ":" + destinationDir);

                                CreateContainerResponse container = dockerClient.createContainerCmd(application)                                // Create Container with Image (Selected Application)
                                        .withCmd("bin/bash")                                                                                    // Container start-up command
                                        .withName(subtaskID)                                                                                       // Name of container
                                        .withBinds(Bind.parse(bindVolume))                                                                      // Mount-bind Volume - [nebulanode.Node Directory where Renderfile is Stored] : [Destination director where results will be Stored]
                                        .withCmd("results/blendertest.blend", "-o", "results/frame_###", "-f", "1")                             // Arguments passed to Application - Renderfile, naming of Results (frame_###), Selection of frame to render (-f)
                                        .exec();

                                dockerClient.startContainerCmd(container.getId()).exec();                                                       // Start container.

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
            }
        }
    }

    public String getMetadata(String subtaskID) {
        Path taskPath = new File("C:\\Users\\Daryl Wong\\Desktop\\nebula\\code\\nebulanode\\taskcache\\" + subtaskID).toPath();
        String application = null;
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(taskPath, UserDefinedFileAttributeView.class);
            String name = "user.application";
            ByteBuffer buffer = ByteBuffer.allocate(view.size(name));
            view.read(name, buffer);
            buffer.flip();
            application = Charset.defaultCharset().decode(buffer).toString();
            System.out.println("TEST | Attribute : " + application);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return application;
    }

    public static void searchImageRepo(String application) {
        DefaultDockerClientConfig config
                = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withRegistryEmail("darylgabrielwong@gmail.com")
                .withRegistryPassword("DWGabriel4")
                .withRegistryUsername("darylgabrielwong")
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        List<SearchItem> dockerSearch = dockerClient.searchImagesCmd(application).exec();
        System.out.println("Search returned : " + dockerSearch.size());
    }

}

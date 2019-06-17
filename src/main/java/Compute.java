import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;

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
        final DockerClient dockerClient = DefaultDockerClient.builder()
                .build();

        try {
            dockerClient.pull(application);
        } catch (Exception e) {
            e.printStackTrace();
        }

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

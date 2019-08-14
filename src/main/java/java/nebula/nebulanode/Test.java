package nebula.nebulanode;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.SearchItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import org.apache.commons.io.FilenameUtils;

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
 * Created by Daryl Wong on 3/30/2019.
 */
public class Test {

    public static void main(String[] args) throws IOException {
        startNebula();
    }

public static void startNebula () {

    final String nodeDir = "/users/Daryl Wong/desktop/nebula/code/nebulaserver/nebuladatabase/tasks/054873/originaltask";                              // Bind Volume | nebulanode.Node Task Dir - Needs to automatically point at nebulanode.Node device's taskcache directory
    String destinationDir = "/originaltask";                                                                                // Bind Volume | nebulanode.Node Result Dir - Needs to automaticaly point at the nebulanode.Node device's result directory
    String bindVolume = String.format(nodeDir +  ":" + destinationDir);                                                 // Bind Volume | "NodeTaskDir : NodeResultDir"

    DefaultDockerClientConfig config
            = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withRegistryEmail("darylgabrielwong@gmail.com")
            .withRegistryPassword("DWGabriel4")
            .withRegistryUsername("darylgabrielwong")
            .build();

    DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

    CreateContainerResponse container = dockerClient.createContainerCmd("ikester/blender")
            .withCmd("bin/bash")
            .withName("nebula_blender")
            .withBinds(Bind.parse(bindVolume))
            .withCmd("originaltask/blendertest.blend", "-o", "originaltask/frame_###", "-f", "1")
            .exec();

    dockerClient.startContainerCmd(container.getId()).exec();
    }

    public static void getExtension() {
        File testFile = new File("C:\\Users\\Daryl Wong\\Desktop\\test\\bmwcpu.blend");
        String fileExt = FilenameUtils.getExtension(testFile.getName());
        System.out.println("File Extension : " + fileExt);
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

    public static void setMetadata(String subtaskID) {
//        Path testPath = new File("C:\\Users\\Daryl Wong\\Desktop\\nebula\\code\\nebulanode\\taskcache\\blendertest.blend").toPath();
        File testFile = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulanode\\taskcache\\" + subtaskID).getAbsoluteFile();
        String subtask = subtaskID;
                Path testPath = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulanode\\taskcache\\" + subtaskID).toPath();

        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(testPath, UserDefinedFileAttributeView.class);
            view.write("user.application", Charset.defaultCharset().encode("nebula/blender"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMetadata(String subtaskID, String metaName) {                                                                       // Getting Application info from Subtask attributes for ComputeTask.
//        Path taskPath = new File("C:\\Users\\Daryl Wong\\Desktop\\nebula\\code\\nebulanode\\taskcache\\" + subtaskID).toPath();
        Path taskPath = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulaserver\\nebuladatabase\\tasks\\224110\\originaltask\\blendertest.blend").toPath();
        String metaValue = new String();
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(taskPath, UserDefinedFileAttributeView.class);
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

    public static void listTaskCache() throws IOException {

        File taskCache = new File("C:\\Users\\Daryl Wong\\Desktop\\Nebula\\Code\\nebulanode\\taskcache");


        if (taskCache.listFiles().length <= 0) {
            System.out.println("There are no tasks to compute at this time.");
        } else if (taskCache.listFiles().length > 0) {

            try (Stream<Path> fileStream = Files.walk(Paths.get(taskCache.getAbsolutePath()))) {
                fileStream
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .forEachOrdered(file ->  {
                            System.out.println(file.getName());
                        });
            }
        }
    }
}
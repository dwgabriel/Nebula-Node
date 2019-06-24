import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

/**
 * Created by Daryl Wong on 3/30/2019.
 */
public class Test {

    LinkedHashMap<String, String> taskAppInfo = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {

        setMetadata("blender");
        getMetadata("blender");
    }
    public void addInfo(String taskID, String application) {
        taskAppInfo.put(taskID, application);
    }

    public void printInfo() {
        taskAppInfo.entrySet().forEach(entry -> {
            System.out.println("TEST | " + entry.getKey() + " = " + entry.getValue());
        });
    }


public static void startNebula () {

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
            .withBinds(Bind.parse("/c/Users/Daryl Wong/desktop/nebula/code/nebulanode/taskcache:/test/"))
            .withCmd("test/blendertest.blend", "-o", "test/frame_###", "-f", "1")
            .exec();

    dockerClient.startContainerCmd(container.getId()).exec();
    }

    public static void compute() {
        File testFile = new File("C:\\Users\\Daryl Wong\\Desktop\\test\\bmwcpu.blend");
        String fileExt = FilenameUtils.getExtension(testFile.getName());
        System.out.println("File Extension : " + fileExt);
    }

    public static void setMetadata(String application) {
        Path testPath = new File("C:\\Users\\Daryl Wong\\Desktop\\Misc\\mountainrange.jpg").toPath();
        try {
//            Files.setAttribute(testPath, "user:application", application.getBytes());
//            System.out.println("Attribute : " + Files.getAttribute(testPath, "user:application"));

            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(testPath, UserDefinedFileAttributeView.class);
            view.write("user:application", Charset.defaultCharset().encode("blender"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void getMetadata(String application) {
        Path testPath = new File("C:\\Users\\Daryl Wong\\Desktop\\Misc\\mountainrange.jpg").toPath();
        try {
            UserDefinedFileAttributeView view = Files
                    .getFileAttributeView(testPath, UserDefinedFileAttributeView.class);
            String name = "user:application";
            ByteBuffer buffer = ByteBuffer.allocate(view.size(name));
            view.read(name, buffer);
            buffer.flip();
            String value = Charset.defaultCharset().decode(buffer).toString();
            System.out.println("Attribute : " + value);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
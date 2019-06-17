import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;

import java.io.IOException;

/**
 * Created by Daryl Wong on 3/30/2019.
 */
public class Test {


    public static void main(String[] args) throws IOException {
        compute("darylgabrielwong/blender");
    }

    public static void compute (String application) throws IOException {

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





    }
}
package net.consensys.pantheon.tests.cluster;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.google.common.io.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DockerUtils {
  private static final Logger LOG = LogManager.getLogger();

  /**
   * Creates docker image
   *
   * @return docker image ID
   * @throws FileNotFoundException if the Dockerfile can not be located
   */
  public static String createPantheonDockerImage(final DockerClient dockerClient, final String name)
      throws FileNotFoundException {
    File dockerFile;
    try {
      final String resource = DockerUtils.class.getPackage().getName().replaceAll("\\.", "/");
      final URL url = Resources.getResource(resource);
      if (url == null) throw new FileNotFoundException("Resource Not Found: " + resource);
      dockerFile = findParentDirBySiblingName(new File(url.toURI()), "gradlew.bat");

      if (dockerFile == null) throw new FileNotFoundException("File Not Found: " + url);

      if (!dockerFile.exists()) throw new FileNotFoundException("File Not Found: " + dockerFile);
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }

    String imageId;
    try (BuildImageResultCallback callback =
        new BuildImageResultCallback() {
          @Override
          public void onNext(final BuildResponseItem item) {
            LOG.info("createDockerImage log:" + item);
            super.onNext(item);
          }
        }) {

      final Set<String> tags = new HashSet<>();
      if (name != null) {
        tags.add(name);
      }

      LOG.info("Creating Docker Image for : " + dockerFile);
      imageId = dockerClient.buildImageCmd(dockerFile).withTags(tags).exec(callback).awaitImageId();
      LOG.info("Created Docker Image [" + imageId + "] for : " + dockerFile);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to create Docker image for " + dockerFile, e);
    }
    return imageId;
  }

  /**
   * Creates docker image
   *
   * @param imageType used in locating the correct Dockerfile on disk. eg "pantheon", "geth"
   * @return docker image ID
   * @throws FileNotFoundException if the Dockerfile can not be located
   */
  public static String createDockerImage(
      final DockerClient dockerClient, final String name, final String imageType)
      throws FileNotFoundException {
    File dockerFile;
    try {
      final String resource =
          DockerUtils.class.getPackage().getName().replaceAll("\\.", "/")
              + "/docker/"
              + imageType
              + "/Dockerfile-"
              + imageType;
      final URL url = Resources.getResource(resource);
      if (url == null) throw new FileNotFoundException("Resource Not Found: " + resource);
      dockerFile = new File(url.toURI());
      if (!dockerFile.exists()) throw new FileNotFoundException("File Not Found: " + dockerFile);
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }

    final BuildImageResultCallback callback =
        new BuildImageResultCallback() {
          @Override
          public void onNext(final BuildResponseItem item) {
            LOG.info("createDockerImage log:" + item);
            super.onNext(item);
          }
        };

    final Set<String> tags = new HashSet<>();
    if (name != null) {
      tags.add(name);
    }

    return dockerClient.buildImageCmd(dockerFile).withTags(tags).exec(callback).awaitImageId();
  }

  public static File findParentDirBySiblingName(final File thisFile, final String siblingName) {
    File parent = thisFile.getParentFile();
    while (parent != null) {
      final File file = new File(parent.getAbsolutePath() + File.separatorChar + siblingName);
      if (file.exists()) {
        return parent;
      }
      parent = parent.getParentFile();
    }
    return null;
  }
}

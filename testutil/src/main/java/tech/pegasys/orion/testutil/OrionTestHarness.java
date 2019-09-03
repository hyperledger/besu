/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.orion.testutil;

import static com.google.common.io.Files.readLines;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import net.consensys.orion.cmd.Orion;
import net.consensys.orion.config.Config;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrionTestHarness {
  private static final Logger LOG = LogManager.getLogger();

  private final Orion orion;
  private final OrionConfiguration orionConfiguration;

  private Config config;

  private boolean isRunning;

  protected static final String HOST = "127.0.0.1";

  protected OrionTestHarness(final OrionConfiguration orionConfiguration) {
    this.orionConfiguration = orionConfiguration;
    this.orion = new Orion();
  }

  public Orion getOrion() {
    return orion;
  }

  public void start() {
    if (!isRunning) {
      config = buildConfig();
      orion.run(System.out, System.err, config);
      isRunning = true;
      LOG.info("Orion node port: {}", orion.nodePort());
      LOG.info("Orion client port: {}", orion.clientPort());
    }
  }

  public void stop() {
    if (isRunning) {
      orion.stop();
      isRunning = false;
    }
  }

  public void close() {
    stop();
    try {
      MoreFiles.deleteRecursively(config.workDir(), RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (final IOException e) {
      LOG.info("Failed to clean up temporary file: {}", config.workDir(), e);
    }
  }

  public Config getConfig() {
    return config;
  }

  public String getDefaultPublicKey() {
    return config.publicKeys().stream().map(OrionTestHarness::readFile).findFirst().orElseThrow();
  }

  public List<String> getPublicKeys() {
    return config.publicKeys().stream()
        .map(OrionTestHarness::readFile)
        .collect(Collectors.toList());
  }

  private static String readFile(final Path path) {
    try {
      return readLines(path.toFile(), Charsets.UTF_8).get(0);
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    }
  }

  public URI clientUrl() {
    HttpUrl httpUrl =
        new HttpUrl.Builder().scheme("http").host(HOST).port(orion.clientPort()).build();

    return URI.create(httpUrl.toString());
  }

  public URI nodeUrl() {
    HttpUrl httpUrl =
        new HttpUrl.Builder().scheme("http").host(HOST).port(orion.nodePort()).build();

    return URI.create(httpUrl.toString());
  }

  public void addOtherNode(final URI otherNode) {
    orionConfiguration.addOtherNode(otherNode.toString());
  }

  private Config buildConfig() {
    // @formatter:off
    String confString =
        "tls=\"off\"\n"
            + "tlsservertrust=\"tofu\"\n"
            + "tlsclienttrust=\"tofu\"\n"
            + "nodeport=0\n"
            + "nodenetworkinterface = \""
            + HOST
            + "\"\n"
            + "clientport=0\n"
            + "clientnetworkinterface = \""
            + HOST
            + "\"\n"
            + "storage = \"leveldb:database/orion_node\"\n"
            + "publickeys = ["
            + joinPathsAsTomlListEntry(orionConfiguration.getPublicKey())
            + "]\n"
            + "privatekeys = ["
            + joinPathsAsTomlListEntry(orionConfiguration.getPrivateKey())
            + "]\n"
            + "workdir= \""
            + orionConfiguration.getTempDir().toString()
            + "\"\n";

    if (orionConfiguration.getOtherNodes().size() != 0) {
      confString +=
          "othernodes = [" + joinStringsAsTomlListEntry(orionConfiguration.getOtherNodes()) + "]\n";
    }

    // @formatter:on
    return Config.load(confString);
  }

  private static String joinPathsAsTomlListEntry(final Path... paths) {
    return joinStringsAsTomlListEntry(
        Arrays.stream(paths).map(p -> p.toAbsolutePath().toString()).collect(Collectors.toList()));
  }

  private static String joinStringsAsTomlListEntry(final List<String> strings) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String string : strings) {
      if (!first) {
        builder.append(",");
      }
      first = false;
      builder.append("\"").append(string).append("\"");
    }
    return builder.toString();
  }
}

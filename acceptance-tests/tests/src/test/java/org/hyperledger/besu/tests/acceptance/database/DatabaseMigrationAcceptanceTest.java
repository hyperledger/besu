/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.tests.acceptance.database;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.SearchItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.util.PlatformDetector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@RunWith(Parameterized.class)
public class DatabaseMigrationAcceptanceTest extends AcceptanceTestBase {

  private static final Logger LOG = LogManager.getLogger();
  private final String testName;
  private final String dockerImage;
  private final String dataPath;
  private final long expectedChainHeight;
  private final Address testAddress;
  private final Wei expectedBalance;
  private final Account testAccount;
  private static Map<String, Boolean> initializedImages = new HashMap<>();

  private Node node;

  public DatabaseMigrationAcceptanceTest(
      final String testName,
      final String dockerImage,
      final String dataPath,
      final long expectedChainHeight,
      final Address testAddress,
      final Wei expectedBalance) {
    this.testName = testName;
    this.dockerImage = dockerImage;
    this.dataPath = dataPath;
    this.expectedChainHeight = expectedChainHeight;
    this.testAddress = testAddress;
    this.expectedBalance = expectedBalance;

    this.testAccount = accounts.createAccount(this.testAddress);
  }

  @Parameters(name = "{0}")
  public static Object[][] getParameters() {
    return new Object[][] {
      // First 10 blocks of ropsten
      /*new Object[] {
        "version1",
        0xA,
        Address.fromHexString("0xd1aeb42885a43b72b518182ef893125814811048"),
        Wei.fromHexString("0x2b5e3af16b1880000")
      },
      new Object[] {
        "version1-orig-metadata",
        0xA,
        Address.fromHexString("0xd1aeb42885a43b72b518182ef893125814811048"),
        Wei.fromHexString("0x2b5e3af16b1880000")
      },*/
      new Object[] {
        "Before versioning was enabled",
        "docker.io/pegasyseng/pantheon:1.2.0",
        "version0",
        0xA,
        Address.fromHexString("0xd1aeb42885a43b72b518182ef893125814811048"),
        Wei.fromHexString("0x2b5e3af16b1880000")
      },
    };
  }

  @Before
  public void setUp() throws Exception {
    if (!initializedImages.getOrDefault(dockerImage, false)) {
      generateDatabaseFromDocker();
      initializedImages.put(dockerImage, true);
    }
    // node = besu.createNode(dataPath, this::configureNode);
    // cluster.start(node);
  }

  private void generateDatabaseFromDocker() throws Exception {
    LOG.info("Generating database with docker image: {}", dockerImage);
    final DockerClient dockerClient =
        DockerClientBuilder.getInstance(
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("unix:///var/run/docker.sock")
                    .build())
            .build();
    final Info info = dockerClient.infoCmd().exec();
    LOG.info("Docker info: {}", info.toString());
    final Path hostDataDir = copyDataDir(dataPath);
    LOG.info("Host data dir: {}", hostDataDir.toAbsolutePath().toString());
    Volume containerDataDir = new Volume("/home/besu");

    String hostDataPath = hostDataDir.toAbsolutePath().toString();
    if (PlatformDetector.getOSType().equals("osx")) {
      hostDataPath = "/private".concat(hostDataPath);
    }
    CreateContainerResponse container =
        dockerClient
            .createContainerCmd(dockerImage)
            .withName(testName.toLowerCase().replace(" ", "-"))
            .withVolumes(containerDataDir)
            .withBinds(new Bind(hostDataPath, containerDataDir))
            .withCmd(
                "--data-path=/home/besu",
                "--genesis-file=genesis.json",
                "blocks",
                "import",
                "--from=ropsten-10-blocks.bin")
            .exec();
    dockerClient.startContainerCmd(container.getId()).exec();

    showLog(dockerClient, container.getId(), true, 200);
    final WaitContainerResultCallback callback =
        dockerClient.waitContainerCmd(container.getId()).exec(new WaitContainerResultCallback());
    callback.awaitCompletion().close();
    dockerClient.removeContainerCmd(container.getId()).exec();
  }

  private static void showLog(
      final DockerClient dockerClient,
      final String containerId,
      final boolean follow,
      final int numberOfLines) {
    dockerClient
        .logContainerCmd(containerId)
        .withStdOut(true)
        .withStdErr(true)
        .withFollowStream(follow)
        .withTail(numberOfLines)
        .exec(
            new LogContainerResultCallback() {
              @Override
              public void onNext(Frame item) {
                System.out.println(new String(item.getPayload(), UTF_8));
              }
            });
  }

  private BesuNodeConfigurationBuilder configureNode(
      final BesuNodeConfigurationBuilder nodeBuilder) {
    final Path dataPath = copyDataDir(this.dataPath);
    final String genesisData = getGenesisConfiguration();
    return nodeBuilder
        .devMode(false)
        .dataPath(dataPath)
        .genesisConfigProvider((nodes) -> Optional.of(genesisData))
        .jsonRpcEnabled();
  }

  private String getGenesisConfiguration() {
    try {
      final String genesisPath = dataPath + "/genesis.json";
      final URL genesisResource = this.getClass().getResource(genesisPath);
      if (genesisResource == null) {
        throw new RuntimeException("Unable to locate genesis file: " + genesisPath);
      }
      return Resources.toString(genesisResource, Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldReturnCorrectBlockHeight() {
    // blockchain.currentHeight(expectedChainHeight).verify(node);
  }

  // @Test
  public void shouldReturnCorrectAccountBalance() {
    // testAccount.balanceEquals(Amount.wei(expectedBalance)).verify(node);
  }

  private Path copyDataDir(final String path) {
    final URL url = this.getClass().getResource(path);
    if (url == null) {
      throw new RuntimeException("Unable to locate resource at: " + path);
    }

    try {
      final Path tmpDir = Files.createTempDirectory("data");
      final Path toCopy = Paths.get(url.toURI());
      try (final Stream<Path> pathStream = Files.walk(toCopy)) {
        pathStream.forEach(source -> copy(source, tmpDir.resolve(toCopy.relativize(source))));
        return tmpDir.toAbsolutePath();
      }
    } catch (URISyntaxException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void copy(final Path source, final Path dest) {
    try {
      Files.copy(source, dest, REPLACE_EXISTING);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}

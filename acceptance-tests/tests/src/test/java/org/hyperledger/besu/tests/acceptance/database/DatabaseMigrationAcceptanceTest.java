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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.util.PlatformDetector;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DatabaseMigrationAcceptanceTest extends AcceptanceTestBase {

  private static final Logger LOG = LogManager.getLogger();
  private final String testName;
  private final String dockerImage;
  private final String dockerImageName;
  private final String dockerImageTag;
  private final String dataPath;
  private final long expectedChainHeight;
  private final Address testAddress;
  private final Wei expectedBalance;
  private final Account testAccount;
  private Path hostDataPath;
  private String hostDataPathForBind;

  private BesuNode node;

  public DatabaseMigrationAcceptanceTest(
      final String testName,
      final String dockerImageName,
      final String dockerImageTag,
      final String dataPath,
      final long expectedChainHeight,
      final Address testAddress,
      final Wei expectedBalance) {
    this.testName = testName;
    this.dockerImageName = dockerImageName;
    this.dockerImageTag = dockerImageTag;
    this.dockerImage = String.format("%s:%s", dockerImageName, dockerImageTag);
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
        "docker.io/pegasyseng/pantheon",
        "1.2.0",
        "version0",
        0xA,
        Address.fromHexString("0xd1aeb42885a43b72b518182ef893125814811048"),
        Wei.fromHexString("0x2b5e3af16b1880000")
      },
      new Object[] {
        "After versioning was enabled ",
        "docker.io/pegasyseng/pantheon",
        "1.2.1",
        "version1",
        0xA,
        Address.fromHexString("0xd1aeb42885a43b72b518182ef893125814811048"),
        Wei.fromHexString("0x2b5e3af16b1880000")
      }
    };
  }

  @Before
  public void setUp() throws Exception {
    generateDatabaseFromDocker();
    node = besu.createNode(dataPath, this::configureNode);
    cluster.start(node);
  }

  private void generateDatabaseFromDocker() throws Exception {
    LOG.info("Generating database for test case: {}", testName);
    final DockerBesu docker = DockerBesu.createDockerClient();
    LOG.info("Pulling docker image: {}", dockerImage);
    docker.pull(dockerImageName, dockerImageTag);
    hostDataPath = copyDataDir(dataPath);
    LOG.info("Host data dir: {}", hostDataPath.toAbsolutePath().toString());
    hostDataPathForBind = hostDataPath.toAbsolutePath().toString();
    if (PlatformDetector.getOSType().equals("osx")) {
      hostDataPathForBind = "/private".concat(hostDataPathForBind);
    }
    docker.runBesu(
        dockerImage,
        hostDataPathForBind,
        "--data-path=/home/besu",
        String.format("--genesis-file=%s/genesis.json", DockerBesu.containerDataPath),
        "blocks",
        "import",
        String.format("--from=%s/ropsten-10-blocks.bin", DockerBesu.containerDataPath));
  }

  private BesuNodeConfigurationBuilder configureNode(
      final BesuNodeConfigurationBuilder nodeBuilder) {
    final String genesisData = getGenesisConfiguration();
    return nodeBuilder
        .devMode(false)
        .dataPath(hostDataPath)
        .genesisConfigProvider((nodes) -> Optional.of(genesisData))
        .jsonRpcEnabled();
  }

  private String getGenesisConfiguration() {
    try {
      return Resources.toString(
          hostDataPath.resolve("genesis.json").toUri().toURL(), Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldReturnCorrectBlockHeight() {
    blockchain.currentHeight(expectedChainHeight).verify(node);
  }

  @Test
  public void shouldReturnCorrectAccountBalance() {
    testAccount.balanceEquals(Amount.wei(expectedBalance)).verify(node);
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

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
package org.hyperledger.enclave.testutil;

import static com.google.common.io.Files.readLines;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import io.vertx.core.json.JsonArray;
import org.assertj.core.util.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

public class TesseraTestHarness implements EnclaveTestHarness {
  private static final Logger LOG = LoggerFactory.getLogger(TesseraTestHarness.class);

  private final EnclaveConfiguration enclaveConfiguration;

  private boolean isRunning;
  private URI nodeURI;
  private URI q2TUri;
  private URI thirdPartyUri;

  private final String tesseraVersion = "latest";

  private final int thirdPartyPort = 9081;
  private final int q2TPort = 9082;
  public static final int p2pPort = 9001;

  private final String containerKeyDir = "/tmp/keys/";

  @SuppressWarnings("rawtypes")
  private GenericContainer tesseraContainer;

  private final Optional<Network> containerNetwork;
  private final String containerName;

  protected TesseraTestHarness(
      final EnclaveConfiguration enclaveConfiguration, final Optional<Network> containerNetwork) {
    this.enclaveConfiguration = enclaveConfiguration;
    this.containerNetwork = containerNetwork;
    this.containerName = enclaveConfiguration.getName();
    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
  }

  @Override
  public void start() {
    if (!isRunning) {
      final File tempFolder = Files.newTemporaryFolder();
      LOG.info("Temporary directory: " + tempFolder.getAbsolutePath());
      final String configFile = createConfigFile(enclaveConfiguration.getName());

      tesseraContainer = buildTesseraContainer(configFile);
      containerNetwork.ifPresent(network -> addNetwork(tesseraContainer, containerName, network));
      tesseraContainer.start();
      isRunning = true;

      try {
        final String host = "http://" + tesseraContainer.getHost();
        nodeURI = new URI(host + ":" + tesseraContainer.getMappedPort(p2pPort));
        LOG.info("Tessera node URI: {}", nodeURI);
        q2TUri = new URI(host + ':' + tesseraContainer.getMappedPort(q2TPort));
        LOG.info("Tessera client URI: {}", q2TUri);
        thirdPartyUri = new URI(host + ':' + tesseraContainer.getMappedPort(thirdPartyPort));
        LOG.info("Tessera thirdParty URI: {}", thirdPartyUri);
      } catch (final URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void stop() {
    if (isRunning) {
      tesseraContainer.stop();
      isRunning = false;
    }
  }

  @Override
  public void close() {
    stop();
  }

  @Override
  public List<Path> getPublicKeyPaths() {
    return Arrays.asList(enclaveConfiguration.getPublicKeys());
  }

  @Override
  public String getDefaultPublicKey() {
    return readFile(enclaveConfiguration.getPublicKeys()[0]);
  }

  @Override
  public List<String> getPublicKeys() {
    return Arrays.stream(enclaveConfiguration.getPublicKeys())
        .map(TesseraTestHarness::readFile)
        .collect(Collectors.toList());
  }

  private static String readFile(final Path path) {
    try {
      return readLines(path.toFile(), Charsets.UTF_8).get(0);
    } catch (final IOException e) {
      e.printStackTrace();
      return "";
    }
  }

  @Override
  public URI clientUrl() {
    return q2TUri;
  }

  @Override
  public URI nodeUrl() {
    return nodeURI;
  }

  @Override
  public void addOtherNode(final URI otherNode) {
    enclaveConfiguration.addOtherNode(otherNode.toString());
  }

  @Override
  public EnclaveType getEnclaveType() {
    return EnclaveType.TESSERA;
  }

  private String createConfigFile(final String nodeName) {
    // create a config file

    // @formatter:off
    String confString =
        "{\n"
            + "    \"mode\" : \"orion\",\n"
            + "    \"encryptor\":{\n"
            + "        \"type\":\"NACL\",\n"
            + "        \"properties\":{\n"
            + "        }\n"
            + "    },\n"
            + "    \"useWhiteList\": false,\n"
            + "    \"jdbc\": {\n"
            + "        \"username\": \"sa\",\n"
            + "        \"password\": \"\",\n"
            + "        \"url\": \"jdbc:h2:/tmp/db;MODE=Oracle;TRACE_LEVEL_SYSTEM_OUT=0\",\n"
            + "        \"autoCreateTables\": true\n"
            + "    },\n"
            + "    \"serverConfigs\":[\n"
            + "        {\n"
            + "            \"app\":\"ThirdParty\",\n"
            + "            \"enabled\": true,\n"
            + "            \"serverAddress\": \"http://"
            + nodeName
            + ":"
            + thirdPartyPort
            + "\",\n"
            + "            \"cors\" : {\n"
            + "                \"allowedMethods\" : [\"GET\", \"OPTIONS\"],\n"
            + "                \"allowedOrigins\" : [\"*\"]\n"
            + "            },\n"
            + "            \"communicationType\" : \"REST\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"app\":\"Q2T\",\n"
            + "            \"enabled\": true,\n"
            + "            \"serverAddress\":\"http://"
            + nodeName
            + ":"
            + q2TPort
            + "\",\n"
            + "            \"communicationType\" : \"REST\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"app\":\"P2P\",\n"
            + "            \"enabled\": true,\n"
            + "            \"serverAddress\":\"http://"
            + nodeName
            + ":"
            + p2pPort
            + "\",\n"
            + "            \"communicationType\" : \"REST\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"keys\": {\n"
            + "        \"passwords\": [],\n"
            + "        \"keyData\": "
            + buildKeyConfig()
            + "\n"
            + "    },\n"
            + "    \"alwaysSendTo\": []";

    if (enclaveConfiguration.getOtherNodes().size() != 0) {
      confString +=
          ",\n"
              + "    \"peer\": [\n"
              + "        {\n"
              + "            \"url\": \""
              + enclaveConfiguration.getOtherNodes().get(0)
              + "\"\n"
              + "        }\n"
              + "    ]";
    } else {
      confString += ",\n" + "    \"peer\": []";
    }

    confString += "\n}";

    final File configFile = Files.newTemporaryFile();
    try {
      final FileWriter fw = new FileWriter(configFile, StandardCharsets.UTF_8);
      fw.write(confString);
      fw.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    return configFile.getAbsolutePath();
  }

  private String buildKeyConfig() {
    final JsonArray keyArray = new JsonArray();
    final List<Path> pubKeysPaths = Arrays.asList(enclaveConfiguration.getPublicKeys());
    final List<Path> privKeyPaths = Arrays.asList(enclaveConfiguration.getPrivateKeys());

    for (int count = 0; count < pubKeysPaths.size(); count++) {
      final HashMap<String, String> stringStringHashMap = new HashMap<>();
      stringStringHashMap.put(
          "publicKeyPath", containerKeyDir + pubKeysPaths.get(count).getFileName());
      stringStringHashMap.put(
          "privateKeyPath", containerKeyDir + privKeyPaths.get(count).getFileName());
      keyArray.add(stringStringHashMap);
    }

    return keyArray.toString();
  }

  @SuppressWarnings("rawtypes")
  private GenericContainer buildTesseraContainer(final String configFilePath) {
    final String containerConfigFilePath = "/tmp/config.json";
    final String keyDir = enclaveConfiguration.getTempDir().toString();
    return new GenericContainer<>("quorumengineering/tessera:" + tesseraVersion)
        .withCopyFileToContainer(MountableFile.forHostPath(configFilePath), containerConfigFilePath)
        .withFileSystemBind(keyDir, containerKeyDir)
        .withCommand("--configfile " + containerConfigFilePath)
        .withExposedPorts(p2pPort, q2TPort, thirdPartyPort)
        .waitingFor(Wait.forHttp("/upcheck").withMethod("GET").forStatusCode(200));
  }

  @SuppressWarnings("rawtypes")
  private void addNetwork(
      final GenericContainer container, final String containerName, final Network network) {
    container.withNetwork(network).withNetworkAliases(containerName);
  }
}

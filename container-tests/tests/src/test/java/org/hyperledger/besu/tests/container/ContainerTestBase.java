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
package org.hyperledger.besu.tests.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.http.HttpService;
import org.web3j.quorum.Quorum;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ContainerTestBase {
  // A docker image can be built using `./gradlew clean distDocker`
  // check the status of local images with `docker images`
  // To use that local image, change besuImage below eg
  //  private final String besuImage = "hyperledger/besu:21.7.0-SNAPSHOT";
  private final String besuImage = System.getProperty("containertest.imagename");

  private final String goQuorumVersion = "22.4.4";
  private final String tesseraVersion = "22.1.3";

  protected final String goQuorumTesseraPubKey = "3XGBIf+x8IdVQOVfIsbRnHwTYOJP/Fx84G8gMmy8qDM=";
  protected final String besuTesseraPubKey = "8JJLEAbq6o9m4Kqm++v0Y1n9Z2ryAFtZTyhnxSKWgws=";

  private final Network containerNetwork = Network.SHARED;
  protected Quorum besuWeb3j;
  protected Quorum goQuorumWeb3j;

  // General config
  private final String hostGenesisPath = "/genesis.json";
  private final String hostKeyPath = "/keys/";
  private final String containerKeyPath = "/tmp/keys/";
  private final Integer networkId = 2020;

  // Besu config
  private final Integer besuP2pPort = 30303;
  private final Integer besuRpcPort = 8545;
  private final String besuContainerGenesisPath = "/opt/besu/genesis.json";
  private final String besuNetworkAlias = "besuNode";
  private final String hostBesuKeyPath = "/besu/data/key";
  private final String containerBesuKeyPath = "/opt/besu/key";

  // GoQuorum + Tessera shared config
  private final String ipcBindDir = "/tmp/ipc/";
  private final String ipcDirPath = "/ipc/";
  private final String ipcFilePath = "test.ipc";
  private final String containerIpcPath = ipcBindDir + ipcFilePath;

  // Tessera config
  private final String tesseraContainerPrivKey1Path = "/tmp/keys/key1.key";
  private final String tesseraContainerPubKey1Path = "/tmp/keys/key1.pub";
  private final String tesseraContainerPrivKey2Path = "/tmp/keys/key2.key";
  private final String tesseraContainerPubKey2Path = "/tmp/keys/key2.pub";
  private final String tesseraContainerConfigFilePath = "/tmp/tessera/tesseraConfig.json";
  protected final int tesseraRestPort = 9081;
  private final int tesseraQ2TRestPort = 9003;
  private final String hostTesseraResources = "/tessera/";
  private final String containerTesseraResources = "/tmp/tessera/";
  private final int tesseraP2pPort = 9001;

  // GoQuorum config
  private final String goQuorumContainerDatadir = "/tmp/qdata/";
  private final String goQuorumContainerGenesis = "/tmp/qdata/genesis.json";
  private final String goQuorumContainerGethIpcPath = goQuorumContainerDatadir + "/geth.ipc";
  private final Integer goQuorumP2pPort = 30303;
  private final Integer goQuorumRpcPort = 8545;

  @Rule public final GenericContainer besuContainer = buildBesuContainer();

  @Rule
  public final GenericContainer tesseraGoQuorumContainer =
      buildGoQuorumTesseraContainer(
          ipcDirPath,
          ipcBindDir,
          containerIpcPath,
          tesseraContainerPrivKey1Path,
          tesseraContainerPubKey1Path);

  @Rule
  public final GenericContainer tesseraBesuContainer =
      buildBesuTesseraContainer(tesseraContainerPrivKey2Path, tesseraContainerPubKey2Path);

  @Rule
  public final GenericContainer goQuorumContainer =
      buildGoQuorumContainer(ipcDirPath, ipcBindDir, containerIpcPath);

  @Before
  public void setUp() throws IOException, InterruptedException {
    besuWeb3j =
        buildWeb3JQuorum(
            besuContainer.getContainerIpAddress(), besuContainer.getMappedPort(besuRpcPort));
    goQuorumWeb3j =
        buildWeb3JQuorum(
            goQuorumContainer.getContainerIpAddress(),
            goQuorumContainer.getMappedPort(goQuorumRpcPort));

    waitFor(10, () -> assertClientVersion(besuWeb3j, "besu"));
    waitFor(10, () -> assertClientVersion(goQuorumWeb3j, goQuorumVersion));

    // Tell GoQuorum to peer to Besu
    goQuorumContainer.execInContainer(
        "geth",
        "--exec",
        "admin.addPeer(\"enode://11b72d1e2fdde254a493047d4061f3b62cc2ba59f4c1b0cf41dda4ba0d77f0bfe4f932ccf9f60203b1d47753f69edf1c80e755e3159e596b1f6aa03cb0c275c4@"
            + besuNetworkAlias
            + ":"
            + besuP2pPort
            + "\")",
        "attach",
        goQuorumContainerGethIpcPath);

    waitFor(30, () -> assertBlockHeight(besuWeb3j, 5));
    waitFor(30, () -> assertBlockHeight(goQuorumWeb3j, 5));
  }

  @After
  public void tearDown() throws IOException {
    boolean fileExists = Files.deleteIfExists(Path.of(getResourcePath(ipcDirPath + ipcFilePath)));
    if (!fileExists) {
      System.out.println("Unable to delete tx IPC file.");
    }
  }

  private GenericContainer buildBesuContainer() {
    return new GenericContainer(besuImage)
        .withNetwork(containerNetwork)
        .withNetworkAliases(besuNetworkAlias)
        .withExposedPorts(besuRpcPort, besuP2pPort)
        .withClasspathResourceMapping(hostBesuKeyPath, containerBesuKeyPath, BindMode.READ_ONLY)
        .withClasspathResourceMapping(hostGenesisPath, besuContainerGenesisPath, BindMode.READ_ONLY)
        .withClasspathResourceMapping(hostKeyPath, containerKeyPath, BindMode.READ_ONLY)
        .withCommand(
            "--genesis-file",
            besuContainerGenesisPath,
            "--network-id",
            networkId.toString(),
            "--p2p-port",
            besuP2pPort.toString(),
            "--rpc-http-enabled",
            "--rpc-http-port",
            besuRpcPort.toString(),
            "--rpc-http-api",
            "ADMIN,ETH,NET,WEB3,GOQUORUM",
            "--min-gas-price",
            "0",
            "--privacy-public-key-file",
            "/tmp/keys/key2.pub",
            "--privacy-url",
            "http://" + "besuTessera" + ':' + tesseraQ2TRestPort);
  }

  private GenericContainer buildGoQuorumTesseraContainer(
      final String ipcPath,
      final String ipcBindDir,
      final String containerIpcPath,
      final String privKeyPath,
      final String pubKeyPath) {
    return new GenericContainer("quorumengineering/tessera:" + tesseraVersion)
        .withNetwork(containerNetwork)
        .withNetworkAliases("goQuorumTessera")
        .withClasspathResourceMapping(
            hostTesseraResources, containerTesseraResources, BindMode.READ_ONLY)
        .withClasspathResourceMapping(ipcPath, ipcBindDir, BindMode.READ_WRITE)
        .withClasspathResourceMapping(hostKeyPath, containerKeyPath, BindMode.READ_ONLY)
        .withCommand(
            "--configfile " + tesseraContainerConfigFilePath,
            "-o serverConfigs[0].serverAddress=http://localhost:" + tesseraRestPort,
            "-o serverConfigs[1].serverAddress=unix:" + containerIpcPath,
            "-o serverConfigs[2].serverAddress=http://" + "goQuorumTessera" + ":" + tesseraP2pPort,
            "-o keys.keyData[0].privateKeyPath=" + privKeyPath,
            "-o keys.keyData[0].publicKeyPath=" + pubKeyPath,
            "-o peer[0].url=http://" + "goQuorumTessera" + ":" + tesseraP2pPort + "/",
            "-o peer[1].url=http://" + "besuTessera" + ":" + tesseraP2pPort + "/")
        .withExposedPorts(tesseraP2pPort, tesseraRestPort)
        .waitingFor(Wait.forHttp("/upcheck"));
  }

  private GenericContainer buildBesuTesseraContainer(
      final String privKeyPath, final String pubKeyPath) {
    return new GenericContainer("quorumengineering/tessera:" + tesseraVersion)
        .withNetwork(containerNetwork)
        .withNetworkAliases("besuTessera")
        .withClasspathResourceMapping(
            hostTesseraResources, containerTesseraResources, BindMode.READ_ONLY)
        .withClasspathResourceMapping(hostKeyPath, containerKeyPath, BindMode.READ_ONLY)
        .withCommand(
            "--configfile " + tesseraContainerConfigFilePath,
            "-o serverConfigs[0].serverAddress=http://localhost:" + tesseraRestPort,
            "-o serverConfigs[1].serverAddress=http://localhost:" + tesseraQ2TRestPort,
            "-o serverConfigs[2].serverAddress=http://" + "besuTessera" + ":" + tesseraP2pPort,
            "-o keys.keyData[0].privateKeyPath=" + privKeyPath,
            "-o keys.keyData[0].publicKeyPath=" + pubKeyPath,
            "-o peer[0].url=http://" + "besuTessera" + ":" + tesseraP2pPort + "/",
            "-o peer[1].url=http://" + "goQuorumTessera" + ":" + tesseraP2pPort + "/")
        .withExposedPorts(tesseraP2pPort, tesseraRestPort)
        .waitingFor(Wait.forHttp("/upcheck"));
  }

  private GenericContainer buildGoQuorumContainer(
      final String ipcPath, final String ipcBindDir, final String containerIpcPath) {
    return new GenericContainer("quorumengineering/quorum:" + goQuorumVersion)
        .withNetwork(containerNetwork)
        .dependsOn(tesseraGoQuorumContainer)
        .withExposedPorts(goQuorumRpcPort, goQuorumP2pPort)
        .withClasspathResourceMapping(
            "/goQuorum/qdata/", goQuorumContainerDatadir, BindMode.READ_ONLY)
        .withClasspathResourceMapping(hostGenesisPath, goQuorumContainerGenesis, BindMode.READ_ONLY)
        .withClasspathResourceMapping(ipcPath, ipcBindDir, BindMode.READ_WRITE)
        .withEnv("PRIVATE_CONFIG", containerIpcPath)
        .withCreateContainerCmdModifier(
            (Consumer<CreateContainerCmd>)
                cmd ->
                    cmd.withEntrypoint(
                        "/bin/ash",
                        "-c",
                        "geth init "
                            + goQuorumContainerGenesis
                            + " --datadir "
                            + goQuorumContainerDatadir
                            + " && geth --datadir "
                            + goQuorumContainerDatadir
                            + " --networkid "
                            + networkId.toString()
                            + " --rpc"
                            + " --rpcaddr 0.0.0.0"
                            + " --rpcport "
                            + goQuorumRpcPort.toString()
                            + " --rpcapi admin,eth,debug,miner,net,shh,txpool,personal,web3,quorum,quorumExtension,clique"
                            + " --emitcheckpoints"
                            + " --port "
                            + goQuorumP2pPort.toString()
                            + " --verbosity"
                            + " 3"
                            + " --nousb"
                            + " --nodekey "
                            + goQuorumContainerDatadir
                            + "/nodeKey"));
  }

  private Quorum buildWeb3JQuorum(final String containerIpAddress, final Integer mappedPort) {
    return Quorum.build(new HttpService("http://" + containerIpAddress + ":" + mappedPort));
  }

  private void assertClientVersion(final Web3j web3, final String clientString) throws IOException {
    final Web3ClientVersion clientVersionResult = web3.web3ClientVersion().send();
    assertThat(clientVersionResult.getWeb3ClientVersion()).contains(clientString);
  }

  private void assertBlockHeight(final Web3j web3j, final int blockHeight) throws IOException {
    final EthBlockNumber blockNumberResult = web3j.ethBlockNumber().send();
    assertThat(blockNumberResult.getBlockNumber().intValueExact())
        .isGreaterThanOrEqualTo(blockHeight);
  }

  public static void waitFor(final int timeout, final ThrowingRunnable condition) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(timeout, TimeUnit.SECONDS)
        .untilAsserted(condition);
  }

  private String getResourcePath(final String resourceName) {
    return getClass().getResource(resourceName).getPath();
  }

  protected Credentials loadCredentials() throws IOException, CipherException {
    return Credentials.create("8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
  }
}

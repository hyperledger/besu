/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.tests.acceptance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.tests.acceptance.bft.BftAcceptanceTestParameterization;
import org.hyperledger.besu.tests.acceptance.bft.ParameterizedBftTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
/*import org.hyperledger.besu.tests.web3j.generated.SimpleStorage;
import org.hyperledger.besu.tests.web3j.generated.SimpleStorageShanghai;*/
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;


import static org.apache.logging.log4j.util.LoaderUtil.getClassLoader;
import static org.assertj.core.api.Assertions.assertThat;

public class QuorumIBFTMigrationTest extends ParameterizedBftTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(QuorumIBFTMigrationTest.class);

  public static void removeDatabaseFilesAndFolder(final String... nodeNames) throws IOException {
    for (final String nodeName : nodeNames) {
      final Path dataDir = Paths.get("/tmp", nodeName);
      Files.deleteIfExists(dataDir.resolve("DATABASE_METADATA.json"));
      Files.deleteIfExists(dataDir.resolve("VERSION_METADATA.json"));
      final Path databaseDir = dataDir.resolve("database");
      try (Stream<Path> paths = Files.walk(databaseDir)) {
        paths.sorted((path1, path2) -> path2.compareTo(path1)) // Sort in reverse order to delete files before directories
                .forEach(path -> {
                  try {
                    Files.delete(path);
                  } catch (final IOException e) {
                    LOG.info("Failed to delete file: " + path, e);
                  }
                });
      }
      LOG.info("Deleted blockchain data for node : " + nodeName);
    }
  }

  public static void copyKeyFilesToNodeDataDirs(final String... nodeNames) throws IOException {
    for (String nodeName : nodeNames) {
      copyKeyFile(nodeName, "key");
      copyKeyFile(nodeName, "key.pub");
    }
  }

  private static void copyKeyFile(final String nodeName, final String keyFileName) throws IOException {
    String resourceFileName = nodeName + keyFileName;
    try (InputStream keyFileStream = getClassLoader().getResourceAsStream(resourceFileName)) {
      if (keyFileStream == null) {
        throw new IOException("Resource not found: " + resourceFileName);
      }
      Path targetPath = Paths.get("/tmp", nodeName, keyFileName);
      Files.createDirectories(targetPath.getParent());
      Files.copy(keyFileStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public static void runBesuCommand() throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder(
            "../../build/install/besu/bin/besu",
            "--genesis-file", "src/test/resources/qbft/qbft.json",
            "--data-path", "/tmp/miner1",
            "--data-storage-format", "FOREST",
            "blocks", "import", "src/test/resources/ibft.blocks"
    );

    processBuilder.directory(new File(System.getProperty("user.dir")));
    processBuilder.inheritIO(); // This will redirect the output to the console

    Process process = processBuilder.start();
    int exitCode = process.waitFor();
    if (exitCode == 0) {
      System.out.println("Import command executed successfully.");
    } else {
      throw new RuntimeException("Import command execution failed with exit code: " + exitCode);
    }
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("factoryFunctions")
  public void shouldBeStableDuringLongTest(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) throws Exception {
    setUp(testName, nodeFactory);

    // remove DATABASE_METADATA.json, VERSION_METADATA.json files and database folder from the datadirs
    try {
      removeDatabaseFilesAndFolder("miner1", "miner2", "miner3", "miner4", "miner5");
    } catch (IOException e) {
      LOG.info("Failed to remove database files and folder", e);
    }

    // Create a mix of Bonsai and Forest DB nodes
    final BesuNode minerNode1 = nodeFactory.createForestNodeFixedPort(besu, "miner1");
    final BesuNode minerNode2 = nodeFactory.createForestNodeFixedPort(besu, "miner2");
    final BesuNode minerNode3 = nodeFactory.createForestNodeFixedPort(besu, "miner3");
    final BesuNode minerNode4 = nodeFactory.createForestNodeFixedPort(besu, "miner4");
    final BesuNode minerNode5 = nodeFactory.createForestNodeFixedPort(besu, "miner5");

    // Copy key files to the node datadirs -  /tmp/miner1, /tmp/miner2, /tmp/miner3, /tmp/miner4, /tmp/miner5
    // Use the key files saved in resources directory
    copyKeyFilesToNodeDataDirs("miner1", "miner2", "miner3", "miner4", "miner5");

    // start one node and import blocks from import file
    // Use import file, genesis saved in resources directory

    runBesuCommand();

    //After the import is done, start the rest of the nodes using the same genesis and respective node keys

    cluster.start(minerNode1, minerNode2, minerNode3, minerNode4, minerNode5);

    // Check that the chain is progressing as expected
    cluster.verify(blockchain.reachesHeight(minerNode2, 1, 45));

    /*
    // Setup
    //    Deploy a contract that we'll invoke periodically to ensure state
    //    is correct during the test, especially after stopping nodes and
    //    applying new forks.
    SimpleStorage simpleStorageContract =
        minerNode1.execute(contractTransactions.createSmartContract(SimpleStorage.class));

   // Check the contract address is as expected for this sender & nonce
    contractVerifier
        .validTransactionReceipt("0x42699a7612a82f1d9c36148af9c77354759b210b")
        .verify(simpleStorageContract);

    // Before upgrading to newer forks, try creating a shanghai-evm contract and check that
    // the transaction fails
    try {
      minerNode1.execute(contractTransactions.createSmartContract(SimpleStorageShanghai.class));
      Assertions.fail("Shanghai transaction should not be executed on a pre-shanghai chain");
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .contains(
              "Revert reason: 'Transaction processing could not be completed due to an exception'");
    }

    // Should initially be set to 0
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(0));

    // Set to something new
    simpleStorageContract.set(BigInteger.valueOf(101)).send();

    // Check the state of the contract has updated correctly. We'll set & get this several times
    // during the test
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(101));

    // Step 1
    // Run for the configured time period, periodically checking that
    // the chain is progressing as expected
    BigInteger chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    assertThat(chainHeight.compareTo(BigInteger.ZERO)).isGreaterThanOrEqualTo(1);
    BigInteger lastChainHeight = chainHeight;

    Instant startTime = Instant.now();
    Instant nextStepEndTime = startTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      Thread.sleep(ONE_MINUTE);
      chainHeight = minerNode1.execute(ethTransactions.blockNumber());

      // With 1-second block period chain height should have moved on by at least 50 blocks
      assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(50))))
          .isGreaterThanOrEqualTo(1);
      lastChainHeight = chainHeight;
    }
    Instant previousStepEndTime = Instant.now();

    // Step 2
    // Stop one of the nodes, check that the chain continues mining
    // blocks
    stopNode(minerNode4);

    nextStepEndTime =
        previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      Thread.sleep(ONE_MINUTE);
      chainHeight = minerNode1.execute(ethTransactions.blockNumber());

      // Chain height should have moved on by at least 5 blocks
      assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(20))))
          .isGreaterThanOrEqualTo(1);
      lastChainHeight = chainHeight;
    }
    previousStepEndTime = Instant.now();

    // Step 3
    // Stop another one of the nodes, check that the chain now stops
    // mining blocks
    stopNode(minerNode3);

    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    lastChainHeight = chainHeight;

    // Leave the chain stalled for 3 minutes. Check no new blocks are mined. Then
    // resume the other validators.
    nextStepEndTime = previousStepEndTime.plus(3, ChronoUnit.MINUTES);
    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      Thread.sleep(ONE_MINUTE);
      chainHeight = minerNode1.execute(ethTransactions.blockNumber());

      // Chain height should not have moved on
      assertThat(chainHeight.equals(lastChainHeight)).isTrue();
    }

    // Step 4
    // Restart both of the stopped nodes. Check that the chain resumes
    // mining blocks
    startNode(minerNode3);

    startNode(minerNode4);

    previousStepEndTime = Instant.now();

    // This step gives the stalled chain time to re-sync and agree on the next BFT round
    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    nextStepEndTime =
        previousStepEndTime.plus((getTestDurationMins() / NUM_STEPS), ChronoUnit.MINUTES);
    lastChainHeight = chainHeight;

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      Thread.sleep(ONE_MINUTE);
      chainHeight = minerNode1.execute(ethTransactions.blockNumber());
      lastChainHeight = chainHeight;
    }
    previousStepEndTime = Instant.now();

    // By this loop it should be producing blocks again
    nextStepEndTime =
        previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      Thread.sleep(ONE_MINUTE);
      chainHeight = minerNode1.execute(ethTransactions.blockNumber());

      // Chain height should have moved on by at least 1 block
      assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(1))))
          .isGreaterThanOrEqualTo(1);
      lastChainHeight = chainHeight;
    }

    // Update our smart contract before upgrading from berlin to london
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(101));
    simpleStorageContract.set(BigInteger.valueOf(201)).send();
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(201));

    // Upgrade the chain from berlin to london in 120 blocks time
    upgradeToLondon(
        minerNode1, minerNode2, minerNode3, minerNode4, lastChainHeight.intValue() + 120);

    previousStepEndTime = Instant.now();

    chainHeight = minerNode1.execute(ethTransactions.blockNumber());
    nextStepEndTime =
        previousStepEndTime.plus(getTestDurationMins() / NUM_STEPS, ChronoUnit.MINUTES);
    lastChainHeight = chainHeight;

    while (System.currentTimeMillis() < nextStepEndTime.toEpochMilli()) {
      Thread.sleep(ONE_MINUTE);
      chainHeight = minerNode1.execute(ethTransactions.blockNumber());

      // Chain height should have moved on by at least 50 blocks
      assertThat(chainHeight.compareTo(lastChainHeight.add(BigInteger.valueOf(50))))
          .isGreaterThanOrEqualTo(1);
      lastChainHeight = chainHeight;
    }

    // Check that the state of our smart contract is still correct
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(201));

    // Update it once more to check new transactions are mined OK
    simpleStorageContract.set(BigInteger.valueOf(301)).send();
    assertThat(simpleStorageContract.get().send()).isEqualTo(BigInteger.valueOf(301));

    // Upgrade the chain to shanghai in 120 seconds. Then try to deploy a shanghai contract
    upgradeToShanghai(
        minerNode1, minerNode2, minerNode3, minerNode4, Instant.now().getEpochSecond() + 120);

    Thread.sleep(THREE_MINUTES);

    SimpleStorageShanghai simpleStorageContractShanghai =
        minerNode1.execute(contractTransactions.createSmartContract(SimpleStorageShanghai.class));

    // Check the contract address is as expected for this sender & nonce
    contractVerifier
        .validTransactionReceipt("0x05d91b9031a655d08e654177336d08543ac4b711")
        .verify(simpleStorageContractShanghai);

     */
  }

  /* private static void updateGenesisConfigToLondon(
      final BesuNode minerNode, final boolean zeroBaseFeeEnabled, final int blockNumber) {

    if (minerNode.getGenesisConfig().isPresent()) {
      final ObjectNode genesisConfigNode =
          JsonUtil.objectNodeFromString(minerNode.getGenesisConfig().get());
      final ObjectNode config = (ObjectNode) genesisConfigNode.get("config");
      config.put("londonBlock", blockNumber);
      config.put("zeroBaseFee", zeroBaseFeeEnabled);
      minerNode.setGenesisConfig(genesisConfigNode.toString());
    }
  }

  private static void updateGenesisConfigToShanghai(
      final BesuNode minerNode, final long blockTimestamp) {

    if (minerNode.getGenesisConfig().isPresent()) {
      final ObjectNode genesisConfigNode =
          JsonUtil.objectNodeFromString(minerNode.getGenesisConfig().get());
      final ObjectNode config = (ObjectNode) genesisConfigNode.get("config");
      config.put("shanghaiTime", blockTimestamp);
      minerNode.setGenesisConfig(genesisConfigNode.toString());
    }
  }

private void upgradeToLondon(
      final BesuNode minerNode1,
      final BesuNode minerNode2,
      final BesuNode minerNode3,
      final BesuNode minerNode4,
      final int londonBlockNumber)
      throws InterruptedException {
    // Node 1
    stopNode(minerNode1);
    updateGenesisConfigToLondon(minerNode1, true, londonBlockNumber);
    startNode(minerNode1);

    // Node 2
    stopNode(minerNode2);
    updateGenesisConfigToLondon(minerNode2, true, londonBlockNumber);
    startNode(minerNode2);

    // Node 3
    stopNode(minerNode3);
    updateGenesisConfigToLondon(minerNode3, true, londonBlockNumber);
    startNode(minerNode3);

    // Node 4
    stopNode(minerNode4);
    updateGenesisConfigToLondon(minerNode4, true, londonBlockNumber);
    startNode(minerNode4);
  }

  private void upgradeToShanghai(
      final BesuNode minerNode1,
      final BesuNode minerNode2,
      final BesuNode minerNode3,
      final BesuNode minerNode4,
      final long shanghaiTime)
      throws InterruptedException {
    // Node 1
    stopNode(minerNode1);
    updateGenesisConfigToShanghai(minerNode1, shanghaiTime);
    startNode(minerNode1);

    // Node 2
    stopNode(minerNode2);
    updateGenesisConfigToShanghai(minerNode2, shanghaiTime);
    startNode(minerNode2);

    // Node 3
    stopNode(minerNode3);
    updateGenesisConfigToShanghai(minerNode3, shanghaiTime);
    startNode(minerNode3);

    // Node 4
    stopNode(minerNode4);
    updateGenesisConfigToShanghai(minerNode4, shanghaiTime);
    startNode(minerNode4);
  }

  // Start a node with a delay before returning to give it time to start
  private void startNode(final BesuNode node) throws InterruptedException {
    cluster.startNode(node);
    Thread.sleep(TEN_SECONDS);
  }

  // Stop a node with a delay before returning to give it time to stop
  private void stopNode(final BesuNode node) throws InterruptedException {
    cluster.stopNode(node);
    Thread.sleep(TEN_SECONDS);
  }*/

  @Override
  public void tearDownAcceptanceTestBase() {
    cluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}

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
 *
 */

package org.hyperledger.besu.tests.acceptance.backup;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.tests.acceptance.AbstractPreexistingNodeTest;
import org.hyperledger.besu.tests.acceptance.database.DatabaseMigrationAcceptanceTest;
import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BackupRoundTripAcceptanceTest extends AbstractPreexistingNodeTest {

  private final Path backupPath;
  private final Path restorePath;
  private final Path rebackupPath;

  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private final List<AccountData> testAccounts;

  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private final long expectedChainHeight;

  public BackupRoundTripAcceptanceTest(
      final String testName,
      final String dataPath,
      final long expectedChainHeight,
      final List<AccountData> testAccounts)
      throws IOException {
    super(testName, dataPath);
    this.expectedChainHeight = expectedChainHeight;
    this.testAccounts = testAccounts;
    backupPath = Files.createTempDirectory("backup");
    backupPath.toFile().deleteOnExit();
    restorePath = Files.createTempDirectory("restore");
    restorePath.toFile().deleteOnExit();
    rebackupPath = Files.createTempDirectory("rebackup");
    rebackupPath.toFile().deleteOnExit();
  }

  @Parameters(name = "{0}")
  public static Object[][] getParameters() {
    return new Object[][] {
      // First 10 blocks of ropsten
      new Object[] {
        "Before versioning was enabled",
        "version0",
        0xA,
        singletonList(
            new AccountData(
                "0xd1aeb42885a43b72b518182ef893125814811048",
                BigInteger.valueOf(0xA),
                Wei.fromHexString("0x2B5E3AF16B1880000"))),
      },
      new Object[] {
        "After versioning was enabled and using multiple RocksDB columns",
        "version1",
        0xA,
        singletonList(
            new AccountData(
                "0xd1aeb42885a43b72b518182ef893125814811048",
                BigInteger.valueOf(0xA),
                Wei.fromHexString("0x2B5E3AF16B1880000")))
      }
    };
  }

  @Before
  public void setUp() throws Exception {
    final URL rootURL = DatabaseMigrationAcceptanceTest.class.getResource(dataPath);
    hostDataPath = copyDataDir(rootURL);
    final Path databaseArchive =
        Paths.get(
            DatabaseMigrationAcceptanceTest.class
                .getResource(String.format("%s/besu-db-archive.tar.gz", dataPath))
                .toURI());
    extract(databaseArchive, hostDataPath.toAbsolutePath().toString());
  }

  @Test
  public void backupRoundtripAndBack() throws IOException {

    // backup from existing files
    final BesuNode backupNode =
        besu.createNode(
            "backup " + testName,
            configureNodeCommands(
                hostDataPath,
                "operator",
                "x-backup-state",
                "--backup-path=" + backupPath.toString(),
                "--block=100"));
    cluster.startNode(backupNode);
    WaitUtils.waitFor(60, () -> backupNode.verify(exitedSuccessfully));
    final ObjectNode backupManifest =
        JsonUtil.objectNodeFromString(
            Files.readString(backupPath.resolve("besu-backup-manifest.json")));

    // restore to a new directory
    final BesuNode restoreNode =
        besu.createNode(
            "restore " + testName,
            configureNodeCommands(
                restorePath, "operator", "x-restore-state", "--backup-path=" + backupPath));
    cluster.startNode(restoreNode);
    WaitUtils.waitFor(60, () -> restoreNode.verify(exitedSuccessfully));

    // start up the backed-up version and assert some details
    final BesuNode runningNode = besu.createNode(testName, this::configureNode);
    cluster.start(runningNode);

    // height matches
    blockchain.currentHeight(expectedChainHeight).verify(runningNode);

    // accounts have value
    testAccounts.forEach(
        accountData ->
            accounts
                .createAccount(Address.fromHexString(accountData.getAccountAddress()))
                .balanceAtBlockEquals(
                    Amount.wei(accountData.getExpectedBalance().toBigInteger()),
                    accountData.getBlock())
                .verify(runningNode));

    runningNode.stop();

    // backup from the restore
    final BesuNode rebackupBesuNode =
        besu.createNode(
            "rebackup " + testName,
            configureNodeCommands(
                restorePath,
                "operator",
                "x-backup-state",
                "--backup-path=" + rebackupPath.toString(),
                "--block=100"));
    cluster.startNode(rebackupBesuNode);
    WaitUtils.waitFor(60, () -> rebackupBesuNode.verify(exitedSuccessfully));
    final ObjectNode rebackupManifest =
        JsonUtil.objectNodeFromString(
            Files.readString(rebackupPath.resolve("besu-backup-manifest.json")));

    // expect that the backup and rebackup manifests match
    assertThat(rebackupManifest).isEqualTo(backupManifest);
  }

  @Nonnull
  private UnaryOperator<BesuNodeConfigurationBuilder> configureNodeCommands(
      final Path dataPath, final String... commands) {
    return nodeBuilder -> super.configureNode(nodeBuilder).dataPath(dataPath).run(commands);
  }
}

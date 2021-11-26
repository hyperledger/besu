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

import static java.util.Collections.singletonList;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.tests.acceptance.AbstractPreexistingNodeTest;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DatabaseMigrationAcceptanceTest
    extends org.hyperledger.besu.tests.acceptance.AbstractPreexistingNodeTest {
  private final long expectedChainHeight;
  private BesuNode node;
  private final List<AccountData> testAccounts;

  public DatabaseMigrationAcceptanceTest(
      final String testName,
      final String dataPath,
      final long expectedChainHeight,
      final List<AccountData> testAccounts) {
    super(testName, dataPath);
    this.expectedChainHeight = expectedChainHeight;
    this.testAccounts = testAccounts;
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
    AbstractPreexistingNodeTest.extract(databaseArchive, hostDataPath.toAbsolutePath().toString());
    node = besu.createNode(testName, this::configureNode);
    cluster.start(node);
  }

  @Test
  public void shouldReturnCorrectBlockHeight() {
    blockchain.currentHeight(expectedChainHeight).verify(node);
  }

  @Test
  public void shouldReturnCorrectAccountBalance() {
    testAccounts.forEach(
        accountData ->
            accounts
                .createAccount(Address.fromHexString(accountData.getAccountAddress()))
                .balanceAtBlockEquals(
                    Amount.wei(accountData.getExpectedBalance().toBigInteger()),
                    accountData.getBlock())
                .verify(node));
  }
}

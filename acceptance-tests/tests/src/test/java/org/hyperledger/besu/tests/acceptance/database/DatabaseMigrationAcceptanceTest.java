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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DatabaseMigrationAcceptanceTest extends AcceptanceTestBase {

  private final String testName;
  private final String dataPath;
  private final long expectedChainHeight;
  private final Wei expectedBalance;
  private final Account testAccount;
  private Path hostDataPath;
  private BesuNode node;

  public DatabaseMigrationAcceptanceTest(
      final String testName,
      final String dataPath,
      final long expectedChainHeight,
      final Address testAddress,
      final Wei expectedBalance) {
    this.testName = testName;
    this.dataPath = dataPath;
    this.expectedChainHeight = expectedChainHeight;
    this.expectedBalance = expectedBalance;
    this.testAccount = accounts.createAccount(testAddress);
  }

  @Parameters(name = "{0}")
  public static Object[][] getParameters() {
    return new Object[][] {
      // First 10 blocks of ropsten
      new Object[] {
        "Before versioning was enabled",
        "version0",
        0xA,
        Address.fromHexString("0xd1aeb42885a43b72b518182ef893125814811048"),
        Wei.fromHexString("0x2B5E3AF16B1880000")
      },
      new Object[] {
        "After versioning was enabled ",
        "version1",
        0xA,
        Address.fromHexString("0xd1aeb42885a43b72b518182ef893125814811048"),
        Wei.fromHexString("0x2B5E3AF16B1880000")
      }
    };
  }

  @Before
  public void setUp() throws Exception {
    System.out.println("Setting up database migration test");
    final URL rootURL = DatabaseMigrationAcceptanceTest.class.getResource(dataPath);
    System.out.printf("Root URL: %s\n", rootURL.toString());
    final Path databaseArchive =
        Paths.get(
            DatabaseMigrationAcceptanceTest.class
                .getResource(String.format("%s/data.zip", dataPath))
                .toURI());
    System.out.printf("Database archive path: %s\n", databaseArchive.toString());
    hostDataPath = copyDataDir(rootURL);
    System.out.println("Listing host data path directory before DB extraction:");
    ls(hostDataPath);
    unzip(databaseArchive, hostDataPath.toAbsolutePath().toString());
    System.out.println("Listing host data path directory after DB extraction:");
    ls(hostDataPath);
    node = besu.createNode(testName, this::configureNode);
    cluster.start(node);
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
    testAccount.balanceEquals(Amount.wei(expectedBalance.toBigInteger())).verify(node);
  }

  /*private static void unzip(final Path zipFile, final String destDirectoryPath) throws IOException {
    final ZipFile zip = new ZipFile(zipFile.toFile());
    zip.extractAll(destDirectoryPath);
  }*/

  private static void unzip(final Path path, final String destDirectory) throws IOException {
    System.out.println("Unzip using IOUtils.");
    final Path dest = Paths.get(destDirectory);
    try (ZipFile zipFile = new ZipFile(path.toFile(), ZipFile.OPEN_READ, StandardCharsets.UTF_8)) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        final ZipEntry entry = entries.nextElement();
        final Path entryPath = dest.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        } else {
          Files.createDirectories(entryPath.getParent());
          try (InputStream in = zipFile.getInputStream(entry)) {
            try (OutputStream out = new FileOutputStream(entryPath.toFile())) {
              IOUtils.copy(in, out);
            }
          }
        }
      }
    }
  }

  private Path copyDataDir(final URL url) {
    if (url == null) {
      throw new RuntimeException("Unable to locate resource.");
    }

    try {
      System.out.println("Creating temp directory");
      final Path tmpDir = Files.createTempDirectory("data");
      System.out.printf("Temp directory path: %s\n", tmpDir.toString());
      Files.delete(tmpDir);
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

  private static void ls(final Path path) {
    ls(path.toFile());
  }

  private static void ls(final File file) {
    for (File f : Objects.requireNonNull(file.listFiles())) {
      if (f.isDirectory()) {
        ls(f);
      } else if (f.isFile()) {
        System.out.println(f.getName());
      }
    }
  }
}

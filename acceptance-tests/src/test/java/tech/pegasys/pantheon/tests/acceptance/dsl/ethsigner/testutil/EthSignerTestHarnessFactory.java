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
package tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil;

import static net.consensys.cava.io.file.Files.copyResource;

import tech.pegasys.ethsigner.core.EthSigner;
import tech.pegasys.ethsigner.core.signing.ConfigurationChainId;
import tech.pegasys.ethsigner.signer.filebased.CredentialTransactionSigner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.WalletUtils;

public class EthSignerTestHarnessFactory {

  private static final Logger LOG = LogManager.getLogger();
  private static final String HOST = "127.0.0.1";

  public static EthSignerTestHarness create(
      final Path tempDir, final String keyPath, final Integer pantheonPort, final long chainId)
      throws IOException, CipherException {

    final Path keyFilePath = copyResource(keyPath, tempDir.resolve(keyPath));

    final EthSignerConfig config =
        new EthSignerConfig(
            Level.DEBUG,
            InetAddress.getByName(HOST),
            pantheonPort,
            Duration.ofSeconds(10),
            InetAddress.getByName(HOST),
            0,
            new ConfigurationChainId(chainId),
            tempDir);

    final EthSigner ethSigner =
        new EthSigner(
            config,
            new CredentialTransactionSigner(
                WalletUtils.loadCredentials("", keyFilePath.toAbsolutePath().toFile())));
    ethSigner.run();

    waitForPortFile(tempDir);

    LOG.info("EthSigner port: {}", config.getHttpListenPort());

    return new EthSignerTestHarness(config, loadPortsFile(tempDir));
  }

  private static Properties loadPortsFile(final Path tempDir) {
    final Properties portsProperties = new Properties();
    try (final FileInputStream fis =
        new FileInputStream(new File(tempDir.toFile(), "ethsigner.ports"))) {
      portsProperties.load(fis);
      LOG.info("Ports for ethsigner {}", portsProperties);
    } catch (final IOException e) {
      throw new RuntimeException("Error reading EthSigner ports file", e);
    }
    return portsProperties;
  }

  private static void waitForPortFile(final Path tempDir) {
    final File file = new File(tempDir.toFile(), "ethsigner.ports");
    Awaitility.waitAtMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (file.exists()) {
                try (final Stream<String> s = Files.lines(file.toPath())) {
                  return s.count() > 0;
                }
              } else {
                return false;
              }
            });
  }
}

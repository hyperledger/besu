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
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.ethsigner.core.EthSigner;
import tech.pegasys.ethsigner.core.signing.ConfigurationChainId;
import tech.pegasys.ethsigner.signer.filebased.CredentialTransactionSigner;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.WalletUtils;

public class EthSignerTestHarnessFactory {

  private static final Logger LOG = LogManager.getLogger();
  private static final String HOST = "127.0.0.1";

  public static EthSignerTestHarness create(
      final Path tempDir,
      final String keyPath,
      final Integer pantheonPort,
      final Integer ethsignerPort,
      final long chainId)
      throws IOException, CipherException {

    final Path keyFilePath = copyResource(keyPath, tempDir.resolve(keyPath));

    final EthSignerConfig config =
        new EthSignerConfig(
            Level.DEBUG,
            InetAddress.getByName(HOST),
            pantheonPort,
            Duration.ofSeconds(10),
            InetAddress.getByName(HOST),
            ethsignerPort,
            new ConfigurationChainId(chainId),
            tempDir);

    final EthSigner ethSigner =
        new EthSigner(
            config,
            new CredentialTransactionSigner(
                WalletUtils.loadCredentials("", keyFilePath.toAbsolutePath().toFile())));
    ethSigner.run();

    final OkHttpClient client = new OkHttpClient.Builder().build();
    final Request request =
        new Request.Builder()
            .url("http://" + HOST + ":" + ethsignerPort + "/upcheck")
            .get()
            .build();

    waitFor(
        () -> {
          client.newCall(request).execute();
        });

    LOG.info("EthSigner port: {}", config.getHttpListenPort());

    return new EthSignerTestHarness(config);
  }
}

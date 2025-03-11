/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;

import com.google.auto.service.AutoService;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class TestTransactionPoolServicePlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(TestTransactionPoolServicePlugin.class);
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final SECPPrivateKey PRIVATE_KEY1 =
      SIGNATURE_ALGORITHM
          .get()
          .createPrivateKey(
              Bytes32.fromHexString(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"));
  private static final KeyPair KEYS1 =
      new KeyPair(PRIVATE_KEY1, SIGNATURE_ALGORITHM.get().createPublicKey(PRIVATE_KEY1));

  private ServiceManager serviceManager;
  private File callbackDir;

  @Override
  public void register(final ServiceManager serviceManager) {
    LOG.info("Registering TestTransactionPoolServicePlugin");
    this.serviceManager = serviceManager;
    callbackDir = new File(System.getProperty("besu.plugins.dir", "plugins"));
  }

  @Override
  public void start() {
    LOG.info("Starting TestTransactionPoolServicePlugin");

    final var txPoolService = serviceManager.getService(TransactionPoolService.class).orElseThrow();

    try (var bw =
        Files.newBufferedWriter(
            callbackDir.toPath().resolve("transactionPoolServicePluginTest.test"))) {

      // valid tx
      validateTransaction(txPoolService, bw, Wei.of(100_000));
      // invalid tx, below min gas price
      validateTransaction(txPoolService, bw, Wei.of(100));

    } catch (final IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  private static void validateTransaction(
      final TransactionPoolService txPoolService, final BufferedWriter bw, final Wei maxFeePerGas)
      throws IOException {
    final var txValid =
        Transaction.builder()
            .chainId(BigInteger.valueOf(20211))
            .to(Address.ZERO)
            .nonce(0)
            .gasLimit(21000)
            .maxFeePerGas(maxFeePerGas)
            .maxPriorityFeePerGas(Wei.of(100))
            .value(Wei.ONE)
            .payload(Bytes.EMPTY)
            .type(TransactionType.EIP1559)
            .signAndBuild(KEYS1);

    final var result = txPoolService.validateTransaction(txValid, false, false);

    bw.write(result.isValid() ? "valid" : result.getErrorMessage());
    bw.newLine();
  }

  @Override
  public void stop() {
    LOG.info("Stopping TestTransactionPoolServicePlugin");
  }
}

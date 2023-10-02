/*
 * Copyright Besu contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.util.Optional;
import java.util.Random;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

public class BaseTransactionPoolTest {

  protected static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  protected static final KeyPair KEYS1 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  protected static final KeyPair KEYS2 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  protected static final Address SENDER1 = Util.publicKeyToAddress(KEYS1.getPublicKey());
  protected static final Address SENDER2 = Util.publicKeyToAddress(KEYS2.getPublicKey());

  private static final Random randomizeTxType = new Random();

  protected final Transaction transaction0 = createTransaction(0);
  protected final Transaction transaction1 = createTransaction(1);
  protected final Transaction transaction2 = createTransaction(2);

  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  protected Transaction createTransaction(final long nonce) {
    return createTransaction(nonce, Wei.of(5000L), KEYS1);
  }

  protected Transaction createTransaction(final long nonce, final KeyPair keys) {
    return createTransaction(nonce, Wei.of(5000L), keys);
  }

  protected Transaction createTransaction(final long nonce, final Wei maxGasPrice) {
    return createTransaction(nonce, maxGasPrice, KEYS1);
  }

  protected Transaction createTransaction(final long nonce, final int payloadSize) {
    return createTransaction(nonce, Wei.of(5000L), payloadSize, KEYS1);
  }

  protected Transaction createTransaction(
      final long nonce, final Wei maxGasPrice, final KeyPair keys) {
    return createTransaction(nonce, maxGasPrice, 0, keys);
  }

  protected Transaction createEIP1559Transaction(
      final long nonce, final KeyPair keys, final int gasFeeMultiplier) {
    return createTransaction(
        TransactionType.EIP1559, nonce, Wei.of(5000L).multiply(gasFeeMultiplier), 0, keys);
  }

  protected Transaction createTransaction(
      final long nonce, final Wei maxGasPrice, final int payloadSize, final KeyPair keys) {

    // ToDo 4844: include BLOB tx here
    final TransactionType txType = TransactionType.values()[randomizeTxType.nextInt(3)];

    return createTransaction(txType, nonce, maxGasPrice, payloadSize, keys);
  }

  protected Transaction createTransaction(
      final TransactionType type,
      final long nonce,
      final Wei maxGasPrice,
      final int payloadSize,
      final KeyPair keys) {
    return prepareTransaction(type, nonce, maxGasPrice, payloadSize).createTransaction(keys);
  }

  protected TransactionTestFixture prepareTransaction(
      final TransactionType type, final long nonce, final Wei maxGasPrice, final int payloadSize) {

    var tx =
        new TransactionTestFixture()
            .to(Optional.of(Address.fromHexString("0x634316eA0EE79c701c6F67C53A4C54cBAfd2316d")))
            .value(Wei.of(nonce))
            .nonce(nonce)
            .type(type);
    if (payloadSize > 0) {
      var payloadBytes = Bytes.fromHexString("01".repeat(payloadSize));
      tx.payload(payloadBytes);
    }
    if (type.supports1559FeeMarket()) {
      tx.maxFeePerGas(Optional.of(maxGasPrice))
          .maxPriorityFeePerGas(Optional.of(maxGasPrice.divide(10)));
    } else {
      tx.gasPrice(maxGasPrice);
    }
    return tx;
  }

  protected Transaction createTransactionReplacement(
      final Transaction originalTransaction, final KeyPair keys) {
    return createTransaction(
        originalTransaction.getType(),
        originalTransaction.getNonce(),
        originalTransaction.getMaxGasPrice().multiply(2),
        0,
        keys);
  }

  protected PendingTransaction createRemotePendingTransaction(final Transaction transaction) {
    return new PendingTransaction.Remote(transaction);
  }

  protected PendingTransaction createRemotePendingTransaction(
      final Transaction transaction, final boolean hasPriority) {
    return PendingTransaction.newPendingTransaction(transaction, false, hasPriority);
  }

  protected PendingTransaction createLocalPendingTransaction(final Transaction transaction) {
    return new PendingTransaction.Local(transaction);
  }

  protected void assertTransactionPending(
      final PendingTransactions transactions, final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.getHash())).contains(t);
  }

  protected void assertTransactionNotPending(
      final PendingTransactions transactions, final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.getHash())).isEmpty();
  }

  protected void assertNoNextNonceForSender(
      final PendingTransactions pendingTransactions, final Address sender) {
    assertThat(pendingTransactions.getNextNonceForSender(sender)).isEmpty();
  }

  protected void assertNextNonceForSender(
      final PendingTransactions pendingTransactions, final Address sender1, final int i) {
    assertThat(pendingTransactions.getNextNonceForSender(sender1)).isPresent().hasValue(i);
  }

  protected void addLocalTransactions(
      final PendingTransactions sorter, final Account sender, final long... nonces) {
    for (final long nonce : nonces) {
      sorter.addTransaction(
          createLocalPendingTransaction(createTransaction(nonce)), Optional.of(sender));
    }
  }

  protected long getAddedCount(final String source, final String priority, final String layer) {
    return metricsSystem.getCounterValue(
        TransactionPoolMetrics.ADDED_COUNTER_NAME, source, priority, layer);
  }

  protected long getRemovedCount(
      final String source, final String priority, final String operation, final String layer) {
    return metricsSystem.getCounterValue(
        TransactionPoolMetrics.REMOVED_COUNTER_NAME, source, priority, operation, layer);
  }
}

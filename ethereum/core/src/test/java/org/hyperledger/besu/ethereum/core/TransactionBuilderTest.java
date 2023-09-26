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

package org.hyperledger.besu.ethereum.core;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.base.Suppliers;
import org.junit.jupiter.api.Test;

public class TransactionBuilderTest {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final KeyPair senderKeys = SIGNATURE_ALGORITHM.get().generateKeyPair();

  @Test
  public void guessTypeCanGuessAllTypes() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Transaction.Builder frontierBuilder = Transaction.builder();
    final Transaction.Builder eip1559Builder = Transaction.builder().maxFeePerGas(Wei.of(5));
    final Transaction.Builder accessListBuilder =
        Transaction.builder()
            .accessList(List.of(new AccessListEntry(gen.address(), List.of(gen.bytes32()))));

    final Set<TransactionType> guessedTypes =
        Stream.of(frontierBuilder, eip1559Builder, accessListBuilder)
            .map(transactionBuilder -> transactionBuilder.guessType().getTransactionType())
            .collect(toUnmodifiableSet());

    assertThat(guessedTypes)
        .containsExactlyInAnyOrder(
            TransactionType.FRONTIER, TransactionType.ACCESS_LIST, TransactionType.EIP1559);
  }

  @Test
  public void zeroBlobTransactionIsInvalid() {
    try {
      new TransactionTestFixture()
          .type(TransactionType.BLOB)
          .chainId(Optional.of(BigInteger.ONE))
          .versionedHashes(Optional.of(List.of()))
          .maxFeePerGas(Optional.of(Wei.of(5)))
          .maxPriorityFeePerGas(Optional.of(Wei.of(5)))
          .maxFeePerBlobGas(Optional.of(Wei.of(5)))
          .createTransaction(senderKeys);
      fail();
    } catch (IllegalArgumentException iea) {
      assertThat(iea).hasMessage("Blob transaction must have at least one blob");
    }
  }
}

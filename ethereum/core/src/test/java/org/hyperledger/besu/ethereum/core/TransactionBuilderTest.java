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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;

public class TransactionBuilderTest {

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

    assertThat(guessedTypes).containsExactlyInAnyOrder(TransactionType.values());
  }
}

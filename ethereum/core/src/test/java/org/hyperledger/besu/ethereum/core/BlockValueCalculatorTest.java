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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class BlockValueCalculatorTest {

  @Test
  public void shouldCalculateZeroBlockValueForEmptyTransactions() {
    final long baseFee = 15;
    final BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .prevRandao(Bytes32.random())
            .baseFeePerGas(Wei.of(baseFee))
            .buildHeader();
    final Block block =
        new Block(blockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));
    Wei blockValue =
        BlockValueCalculator.calculateBlockValue(
            new BlockWithReceipts(block, Collections.emptyList()));
    assertThat(blockValue).isEqualTo(Wei.ZERO);
  }

  @Test
  public void shouldCalculateCorrectBlockValue() {
    // Generate block with three transactions
    final long baseFee = 15;
    final long maxFee = 20;
    final Transaction tx1 =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(1)))
            .maxFeePerGas(Optional.of(Wei.of(maxFee)))
            .type(TransactionType.EIP1559)
            .createTransaction(SignatureAlgorithmFactory.getInstance().generateKeyPair());
    final Transaction tx2 =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(2)))
            .maxFeePerGas(Optional.of(Wei.of(maxFee)))
            .type(TransactionType.EIP1559)
            .createTransaction(SignatureAlgorithmFactory.getInstance().generateKeyPair());
    final Transaction tx3 =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(10)))
            .maxFeePerGas(Optional.of(Wei.of(maxFee)))
            .type(TransactionType.EIP1559)
            .createTransaction(SignatureAlgorithmFactory.getInstance().generateKeyPair());
    final TransactionReceipt receipt1 =
        new TransactionReceipt(Hash.EMPTY_TRIE_HASH, 71, Collections.emptyList(), Optional.empty());
    final TransactionReceipt receipt2 =
        new TransactionReceipt(
            Hash.EMPTY_TRIE_HASH, 143, Collections.emptyList(), Optional.empty());
    final TransactionReceipt receipt3 =
        new TransactionReceipt(
            Hash.EMPTY_TRIE_HASH, 214, Collections.emptyList(), Optional.empty());
    final BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .prevRandao(Bytes32.random())
            .baseFeePerGas(Wei.of(baseFee))
            .buildHeader();
    final Block block =
        new Block(blockHeader, new BlockBody(List.of(tx1, tx2, tx3), Collections.emptyList()));
    Wei blockValue =
        BlockValueCalculator.calculateBlockValue(
            new BlockWithReceipts(block, List.of(receipt1, receipt2, receipt3)));
    // Block value = 71 * 1 + (143-71) * 2 + (214-143) * 5 = 1427
    assertThat(blockValue).isEqualTo(Wei.of(570L));
  }

  @Test
  public void shouldCalculateCorrectBlockValueExceedingLong() {
    // Generate a block with one overpriced transaction where the resulting block value exceeds long
    final long baseFee = 7L;
    final long maxFee = 1L << 60L;
    final Transaction tx1 =
        new TransactionTestFixture()
            .maxPriorityFeePerGas(Optional.of(Wei.of(maxFee - baseFee)))
            .maxFeePerGas(Optional.of(Wei.of(maxFee)))
            .type(TransactionType.EIP1559)
            .createTransaction(SignatureAlgorithmFactory.getInstance().generateKeyPair());
    final TransactionReceipt receipt1 =
        new TransactionReceipt(
            Hash.EMPTY_TRIE_HASH, 21000L, Collections.emptyList(), Optional.empty());
    final BlockHeader blockHeader =
        new BlockHeaderTestFixture()
            .prevRandao(Bytes32.random())
            .baseFeePerGas(Wei.of(baseFee))
            .buildHeader();
    final Block block =
        new Block(blockHeader, new BlockBody(List.of(tx1), Collections.emptyList()));
    Wei blockValue =
        BlockValueCalculator.calculateBlockValue(new BlockWithReceipts(block, List.of(receipt1)));
    // Block value =~ max_long * 2
    assertThat(blockValue).isGreaterThan(Wei.of(Long.MAX_VALUE));
  }
}

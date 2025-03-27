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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingOptions;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TransactionReceiptParameterizedTest {

  private static Stream<Object[]> provider() {
    Object[][] encodingOptions = {
      {"NETWORK", TransactionReceiptEncodingOptions.NETWORK},
      {"NETWORK_FLAT", TransactionReceiptEncodingOptions.NETWORK_FLAT},
      {"STORAGE_WITH_COMPACTION", TransactionReceiptEncodingOptions.STORAGE_WITH_COMPACTION},
      {"STORAGE_WITHOUT_COMPACTION", TransactionReceiptEncodingOptions.STORAGE_WITHOUT_COMPACTION},
      {"TRIE", TransactionReceiptEncodingOptions.TRIE}
    };

    return Stream.of(encodingOptions)
        .flatMap(
            encodingOption ->
                Stream.of(TransactionType.values())
                    .flatMap(
                        transactionType ->
                            Stream.of(Hash.EMPTY_TRIE_HASH, null)
                                .flatMap(
                                    stateRoot -> {
                                      if (stateRoot == null) {
                                        return Stream.of(0, 1)
                                            .map(
                                                status ->
                                                    new Object[] {
                                                      encodingOption[0],
                                                      encodingOption[1],
                                                      transactionType,
                                                      status,
                                                      null
                                                    });
                                      }
                                      Object[][] objects = {
                                        {
                                          encodingOption[0],
                                          encodingOption[1],
                                          transactionType,
                                          null,
                                          stateRoot
                                        }
                                      };
                                      return Arrays.stream(objects);
                                    })));
  }

  @ParameterizedTest(name = "transactionType={2}, encoding={0}, status={3}, stateRoot={4}")
  @MethodSource("provider")
  public void toFromRlp(
      final String name,
      final TransactionReceiptEncodingOptions encodingOptions,
      final TransactionType transactionType,
      final Integer status,
      final Hash stateRoot) {
    final TransactionReceipt receipt = createTransactionReceipt(transactionType, status, stateRoot);
    Bytes encoded =
        RLP.encode(rlpOut -> TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions));
    final TransactionReceipt copy = TransactionReceiptDecoder.readFrom(RLP.input(encoded));
    assertThat(copy).isEqualTo(receipt);
  }

  private TransactionReceipt createTransactionReceipt(
      final TransactionType transactionType, final Integer status, final Hash stateRoot) {
    final BlockDataGenerator gen = new BlockDataGenerator();
    long cumulativeGasUsed = 1L;
    Bytes revertReason = Bytes.fromHexString("0x1122334455667788");
    if (stateRoot == null) {
      return new TransactionReceipt(
          transactionType,
          status,
        cumulativeGasUsed,
          Collections.singletonList(gen.log()),
          Optional.of(revertReason));
    } else {
      return new TransactionReceipt(
          transactionType,
          stateRoot,
        cumulativeGasUsed,
          Collections.singletonList(gen.log()),
          LogsBloomFilter.builder().insertLogs(Collections.singletonList(gen.log())).build(),
        Optional.of(revertReason));
    }
  }
}

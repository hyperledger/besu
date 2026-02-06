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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/** Tests for {@link TransactionReceiptDecoder}. */
public class TransactionReceiptDecoderStrategyTest {

  private static final long CUMULATIVE_GAS_USED = 70_000L;

  @Test
  void decodesReceipt() {
    final TransactionReceipt originalReceipt =
        new TransactionReceipt(
            TransactionType.EIP1559,
            1, // status success
            CUMULATIVE_GAS_USED,
            List.of(),
            Optional.empty());

    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    originalReceipt,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    final TransactionReceipt decoded =
        TransactionReceiptDecoder.readFrom(RLP.input(encoded), false);

    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(CUMULATIVE_GAS_USED);
    assertThat(decoded.getStatus()).isEqualTo(1);
  }

  @Test
  void roundTrip() {
    final TransactionReceipt original =
        new TransactionReceipt(
            TransactionType.ACCESS_LIST, 1, 50_000L, List.of(), Optional.empty());

    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    original,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    final TransactionReceipt decoded =
        TransactionReceiptDecoder.readFrom(RLP.input(encoded), false);

    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(original.getCumulativeGasUsed());
    assertThat(decoded.getStatus()).isEqualTo(original.getStatus());
    assertThat(decoded.getTransactionType()).isEqualTo(original.getTransactionType());
  }

  @Test
  void decodesEip1559Receipt() {
    final TransactionReceipt original =
        new TransactionReceipt(TransactionType.EIP1559, 1, 100_000L, List.of(), Optional.empty());

    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    original,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    final TransactionReceipt decoded =
        TransactionReceiptDecoder.readFrom(RLP.input(encoded), false);

    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(100_000L);
    assertThat(decoded.getStatus()).isEqualTo(1);
    assertThat(decoded.getTransactionType()).isEqualTo(TransactionType.EIP1559);
  }

  @Test
  void decodesBlobReceipt() {
    final TransactionReceipt original =
        new TransactionReceipt(TransactionType.BLOB, 1, 150_000L, List.of(), Optional.empty());

    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    original,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    final TransactionReceipt decoded =
        TransactionReceiptDecoder.readFrom(RLP.input(encoded), false);

    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(150_000L);
    assertThat(decoded.getStatus()).isEqualTo(1);
    assertThat(decoded.getTransactionType()).isEqualTo(TransactionType.BLOB);
  }
}

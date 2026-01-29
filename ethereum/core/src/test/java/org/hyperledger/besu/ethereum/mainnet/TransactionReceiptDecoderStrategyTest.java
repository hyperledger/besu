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
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TransactionReceiptDecoderStrategy}.
 *
 * <p>EIP-7778 changes how receipts are decoded based on the protocol spec:
 *
 * <ul>
 *   <li>Pre-Amsterdam (FRONTIER): gasSpent is not present in receipts
 *   <li>Amsterdam+ (AMSTERDAM): gasSpent is mandatory in receipts
 * </ul>
 */
public class TransactionReceiptDecoderStrategyTest {

  private static final long CUMULATIVE_GAS_USED = 70_000L;
  private static final long GAS_SPENT = 60_000L;

  @Test
  void frontierStrategy_decodesReceiptWithoutGasSpent() {
    // Create a pre-Amsterdam receipt (no gasSpent field)
    final TransactionReceipt originalReceipt =
        new TransactionReceipt(
            TransactionType.EIP1559,
            1, // status success
            CUMULATIVE_GAS_USED,
            List.of(),
            Optional.empty());

    // Encode the receipt without gasSpent
    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    originalReceipt,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    // Decode using FRONTIER strategy
    final TransactionReceipt decoded =
        TransactionReceiptDecoderStrategy.FRONTIER.decode(RLP.input(encoded), false);

    // FRONTIER strategy does not read gasSpent, so it should be empty
    assertThat(decoded.getGasSpent()).isEmpty();
    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(CUMULATIVE_GAS_USED);
    assertThat(decoded.getStatus()).isEqualTo(1);
  }

  @Test
  void amsterdamStrategy_decodesReceiptWithGasSpent() {
    // Create an Amsterdam+ receipt (with gasSpent field)
    final TransactionReceipt originalReceipt =
        new TransactionReceipt(
            TransactionType.EIP1559,
            1, // status success
            CUMULATIVE_GAS_USED,
            GAS_SPENT,
            List.of(),
            Optional.empty());

    // Encode the receipt with gasSpent
    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    originalReceipt,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    // Decode using AMSTERDAM strategy
    final TransactionReceipt decoded =
        TransactionReceiptDecoderStrategy.AMSTERDAM.decode(RLP.input(encoded), false);

    // AMSTERDAM strategy reads the mandatory gasSpent field
    assertThat(decoded.getGasSpent()).isPresent();
    assertThat(decoded.getGasSpent().get()).isEqualTo(GAS_SPENT);
    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(CUMULATIVE_GAS_USED);
    assertThat(decoded).isEqualTo(originalReceipt);
  }

  @Test
  void strategiesHandleGasSpentDifferently() {
    // Create a pre-Amsterdam receipt (no gasSpent)
    final TransactionReceipt preAmsterdamReceipt =
        new TransactionReceipt(
            TransactionType.EIP1559, 1, CUMULATIVE_GAS_USED, List.of(), Optional.empty());

    // Create an Amsterdam+ receipt (with gasSpent)
    final TransactionReceipt amsterdamReceipt =
        new TransactionReceipt(
            TransactionType.EIP1559,
            1,
            CUMULATIVE_GAS_USED,
            GAS_SPENT,
            List.of(),
            Optional.empty());

    // Encode pre-Amsterdam receipt (no gasSpent)
    final Bytes preAmsterdamEncoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    preAmsterdamReceipt,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    // Encode Amsterdam receipt (with gasSpent)
    final Bytes amsterdamEncoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    amsterdamReceipt,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    // FRONTIER strategy decodes pre-Amsterdam receipts, returns empty gasSpent
    final TransactionReceipt frontierDecoded =
        TransactionReceiptDecoderStrategy.FRONTIER.decode(RLP.input(preAmsterdamEncoded), false);

    // AMSTERDAM strategy decodes Amsterdam receipts, returns populated gasSpent
    final TransactionReceipt amsterdamDecoded =
        TransactionReceiptDecoderStrategy.AMSTERDAM.decode(RLP.input(amsterdamEncoded), false);

    // Key difference: FRONTIER has empty gasSpent, AMSTERDAM has populated gasSpent
    assertThat(frontierDecoded.getGasSpent()).isEmpty();
    assertThat(amsterdamDecoded.getGasSpent()).isPresent();
    assertThat(amsterdamDecoded.getGasSpent().get()).isEqualTo(GAS_SPENT);

    // Both strategies read the same core fields correctly
    assertThat(frontierDecoded.getCumulativeGasUsed()).isEqualTo(CUMULATIVE_GAS_USED);
    assertThat(amsterdamDecoded.getCumulativeGasUsed()).isEqualTo(CUMULATIVE_GAS_USED);
    assertThat(frontierDecoded.getStatus()).isEqualTo(1);
    assertThat(amsterdamDecoded.getStatus()).isEqualTo(1);
  }

  @Test
  void frontierStrategy_roundTripWithoutGasSpent() {
    // Create a pre-Amsterdam receipt
    final TransactionReceipt original =
        new TransactionReceipt(
            TransactionType.ACCESS_LIST, 1, 50_000L, List.of(), Optional.empty());

    // Encode → Decode with FRONTIER strategy
    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    original,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    final TransactionReceipt decoded =
        TransactionReceiptDecoderStrategy.FRONTIER.decode(RLP.input(encoded), false);

    // Round-trip should preserve all fields
    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(original.getCumulativeGasUsed());
    assertThat(decoded.getStatus()).isEqualTo(original.getStatus());
    assertThat(decoded.getTransactionType()).isEqualTo(original.getTransactionType());
    assertThat(decoded.getGasSpent()).isEmpty();
  }

  @Test
  void amsterdamStrategy_roundTripWithGasSpent() {
    // Create an Amsterdam+ receipt with gasSpent
    final TransactionReceipt original =
        new TransactionReceipt(
            TransactionType.BLOB, 1, 100_000L, 85_000L, List.of(), Optional.empty());

    // Encode → Decode with AMSTERDAM strategy
    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    original,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    final TransactionReceipt decoded =
        TransactionReceiptDecoderStrategy.AMSTERDAM.decode(RLP.input(encoded), false);

    // Round-trip should preserve all fields including gasSpent
    assertThat(decoded).isEqualTo(original);
    assertThat(decoded.getGasSpent()).isPresent();
    assertThat(decoded.getGasSpent().get()).isEqualTo(85_000L);
  }
}

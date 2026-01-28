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
package org.hyperledger.besu.ethereum.core.encoding.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AmsterdamTransactionReceiptDecoder}.
 *
 * <p>These tests verify that Amsterdam+ receipts with mandatory gasSpent field are correctly
 * decoded.
 */
class AmsterdamTransactionReceiptDecoderTest {

  /**
   * Tests that Amsterdam receipts with mandatory gasSpent are correctly decoded. The gasSpent field
   * should be present and populated.
   */
  @Test
  void decodeAmsterdamReceiptWithGasSpent() {
    final long cumulativeGasUsed = 70_000L; // pre-refund gas for block accounting
    final long gasSpent = 60_000L; // post-refund gas (what user pays)

    // Create an Amsterdam+ receipt with gasSpent
    final TransactionReceipt receipt =
        new TransactionReceipt(
            TransactionType.EIP1559,
            1, // status success
            cumulativeGasUsed,
            gasSpent,
            List.of(), // no logs
            Optional.empty()); // no revert reason

    // Encode the receipt
    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    receipt,
                    output,
                    TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

    // Decode using the Amsterdam decoder (expects mandatory gasSpent)
    final TransactionReceipt decoded =
        AmsterdamTransactionReceiptDecoder.readFrom(RLP.input(encoded), false);

    assertThat(decoded).isEqualTo(receipt);
    assertThat(decoded.getGasSpent()).isPresent();
    assertThat(decoded.getGasSpent().get()).isEqualTo(gasSpent);
    assertThat(decoded.getCumulativeGasUsed()).isEqualTo(cumulativeGasUsed);
  }

  /**
   * Tests that Amsterdam receipts with both gasSpent and revert reason are correctly decoded. The
   * field ordering should be: logs, gasSpent, revertReason.
   */
  @Test
  void decodeAmsterdamReceiptWithGasSpentAndRevertReason() {
    final TransactionReceiptEncodingConfiguration encodingOptions =
        new TransactionReceiptEncodingConfiguration.Builder()
            .withRevertReason(true)
            .withCompactedLogs(true)
            .withBloomFilter(false)
            .build();

    final long cumulativeGasUsed = 100_000L;
    final long gasSpent = 85_000L;
    final Bytes revertReason =
        Bytes.fromHexString(
            "0x08c379a0" // Error(string) selector
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "000000000000000000000000000000000000000000000000000000000000000d"
                + "496e76616c696420696e70757400000000000000000000000000000000000000");

    final TransactionReceipt receipt =
        new TransactionReceipt(
            TransactionType.EIP1559,
            0, // status failed
            cumulativeGasUsed,
            gasSpent,
            List.of(),
            Optional.of(revertReason));

    // Encode the receipt
    final Bytes encoded =
        RLP.encode(rlpOut -> TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions));

    // Decode using the Amsterdam decoder
    final TransactionReceipt decoded =
        AmsterdamTransactionReceiptDecoder.readFrom(RLP.input(encoded), true);

    assertThat(decoded).isEqualTo(receipt);
    assertThat(decoded.getGasSpent()).isPresent();
    assertThat(decoded.getGasSpent().get()).isEqualTo(gasSpent);
    assertThat(decoded.getRevertReason()).isPresent();
    assertThat(decoded.getRevertReason().get()).isEqualTo(revertReason);
  }

  /**
   * Tests that legacy (FRONTIER) typed receipts with gasSpent are correctly decoded by the
   * Amsterdam decoder.
   */
  @Test
  void decodeLegacyTypeAmsterdamReceiptWithGasSpent() {
    final long cumulativeGasUsed = 50_000L;
    final long gasSpent = 45_000L;

    final TransactionReceipt receipt =
        new TransactionReceipt(
            TransactionType.FRONTIER,
            1, // status success
            cumulativeGasUsed,
            gasSpent,
            List.of(),
            Optional.empty());

    // Encode the receipt with bloom filter (non-compacted)
    final Bytes encoded =
        RLP.encode(
            output ->
                TransactionReceiptEncoder.writeTo(
                    receipt,
                    output,
                    TransactionReceiptEncodingConfiguration.DEFAULT_NETWORK_CONFIGURATION));

    // Decode using the Amsterdam decoder
    final TransactionReceipt decoded =
        AmsterdamTransactionReceiptDecoder.readFrom(RLP.input(encoded), false);

    assertThat(decoded).isEqualTo(receipt);
    assertThat(decoded.getGasSpent()).isPresent();
    assertThat(decoded.getGasSpent().get()).isEqualTo(gasSpent);
    assertThat(decoded.getTransactionType()).isEqualTo(TransactionType.FRONTIER);
  }

  /** Tests round-trip encoding and decoding for various transaction types with gasSpent. */
  @Test
  void roundTripAllTransactionTypesWithGasSpent() {
    final TransactionType[] types = {
      TransactionType.FRONTIER,
      TransactionType.ACCESS_LIST,
      TransactionType.EIP1559,
      TransactionType.BLOB
    };

    for (TransactionType type : types) {
      final TransactionReceipt receipt =
          new TransactionReceipt(
              type,
              1,
              80_000L, // cumulativeGasUsed
              70_000L, // gasSpent
              List.of(),
              Optional.empty());

      final Bytes encoded =
          RLP.encode(
              output ->
                  TransactionReceiptEncoder.writeTo(
                      receipt,
                      output,
                      TransactionReceiptEncodingConfiguration.STORAGE_WITH_COMPACTION));

      final TransactionReceipt decoded =
          AmsterdamTransactionReceiptDecoder.readFrom(RLP.input(encoded), false);

      assertThat(decoded).describedAs("Round-trip for type %s", type).isEqualTo(receipt);
      assertThat(decoded.getGasSpent())
          .describedAs("gasSpent present for type %s", type)
          .isPresent();
    }
  }
}

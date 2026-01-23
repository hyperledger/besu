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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.encoding.receipt.AmsterdamTransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.FrontierTransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class TransactionReceiptTest {

  /**
   * Tests RLP round-trip for receipts with gasSpent (EIP-7778, Amsterdam+). Verifies that the
   * gasSpent field is properly encoded and decoded using the Amsterdam decoder.
   */
  @Test
  public void toFromRlpWithGasSpent() {
    final long cumulativeGasUsed = 70_000L; // pre-refund gas for block accounting
    final long gasSpent = 60_000L; // post-refund gas (what user pays)

    // Create an Amsterdam+ receipt with gasSpent
    final TransactionReceipt receipt =
        new TransactionReceipt(
            org.hyperledger.besu.datatypes.TransactionType.EIP1559,
            1, // status success
            cumulativeGasUsed,
            gasSpent,
            List.of(), // no logs
            Optional.empty()); // no revert reason

    // Use AmsterdamTransactionReceiptDecoder for Amsterdam+ receipts with mandatory gasSpent
    final TransactionReceipt copy =
        AmsterdamTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    output ->
                        TransactionReceiptEncoder.writeTo(
                            receipt,
                            output,
                            TransactionReceiptEncodingConfiguration
                                .DEFAULT_NETWORK_CONFIGURATION))),
            false);

    assertThat(copy).isEqualTo(receipt);
    assertThat(copy.getGasSpent()).isPresent();
    assertThat(copy.getGasSpent().get()).isEqualTo(gasSpent);
    assertThat(copy.getCumulativeGasUsed()).isEqualTo(cumulativeGasUsed);
  }

  /**
   * Tests RLP round-trip for receipts with both gasSpent and revert reason. Verifies correct
   * ordering of optional fields in RLP encoding using the Amsterdam decoder.
   */
  @Test
  public void toFromRlpWithGasSpentAndRevertReason() {
    final TransactionReceiptEncodingConfiguration encodingOptions =
        new TransactionReceiptEncodingConfiguration.Builder().withRevertReason(true).build();

    final long cumulativeGasUsed = 100_000L;
    final long gasSpent = 85_000L;
    final Bytes revertReason = Bytes.fromHexString("0x08c379a0"); // Error(string) selector

    final TransactionReceipt receipt =
        new TransactionReceipt(
            org.hyperledger.besu.datatypes.TransactionType.EIP1559,
            0, // status failed
            cumulativeGasUsed,
            gasSpent,
            List.of(),
            Optional.of(revertReason));

    // Use AmsterdamTransactionReceiptDecoder for Amsterdam+ receipts with mandatory gasSpent
    final TransactionReceipt copy =
        AmsterdamTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut -> TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions))),
            true);

    assertThat(copy).isEqualTo(receipt);
    assertThat(copy.getGasSpent()).isPresent();
    assertThat(copy.getGasSpent().get()).isEqualTo(gasSpent);
    assertThat(copy.getRevertReason()).isPresent();
    assertThat(copy.getRevertReason().get()).isEqualTo(revertReason);
  }

  /**
   * Tests that receipts without gasSpent (pre-Amsterdam) can still be decoded and have empty
   * gasSpent.
   */
  @Test
  public void preAmsterdamReceiptHasEmptyGasSpent() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt();

    // Pre-Amsterdam receipts should have empty gasSpent
    assertThat(receipt.getGasSpent()).isEmpty();

    // Verify round-trip still works
    final TransactionReceipt copy =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    output ->
                        TransactionReceiptEncoder.writeTo(
                            receipt,
                            output,
                            TransactionReceiptEncodingConfiguration
                                .DEFAULT_NETWORK_CONFIGURATION))),
            false);

    assertThat(copy).isEqualTo(receipt);
    assertThat(copy.getGasSpent()).isEmpty();
  }

  @Test
  public void toFromRlp() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt();
    final TransactionReceipt copy =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    output ->
                        TransactionReceiptEncoder.writeTo(
                            receipt,
                            output,
                            TransactionReceiptEncodingConfiguration
                                .DEFAULT_NETWORK_CONFIGURATION))),
            false);
    assertThat(copy).isEqualTo(receipt);
  }

  @Test
  public void toFromRlpWithReason() {
    final TransactionReceiptEncodingConfiguration encodingOptions =
        new TransactionReceiptEncodingConfiguration.Builder().withRevertReason(true).build();

    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt(Bytes.fromHexString("0x1122334455667788"));
    final TransactionReceipt copy =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut -> TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions))),
            true);
    assertThat(copy).isEqualTo(receipt);
  }

  @Test
  public void toFromRlpCompacted() {
    final TransactionReceiptEncodingConfiguration encodingOptions =
        new TransactionReceiptEncodingConfiguration.Builder()
            .withCompactedLogs(true)
            .withBloomFilter(false)
            .build();

    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt(Bytes.fromHexString("0x1122334455667788"));
    final TransactionReceipt copy =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut -> TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions))),
            true);
    assertThat(copy).isEqualTo(receipt);
  }

  @Test
  public void toFromRlpCompactedWithReason() {
    final TransactionReceiptEncodingConfiguration encodingOptions =
        new TransactionReceiptEncodingConfiguration.Builder()
            .withRevertReason(true)
            .withCompactedLogs(true)
            .withBloomFilter(false)
            .build();

    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt(Bytes.fromHexString("0x1122334455667788"));
    final TransactionReceipt copy =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut -> TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions))),
            true);
    assertThat(copy).isEqualTo(receipt);
  }

  @Test
  public void uncompactedAndCompactedDecodeToSameReceipt() {

    final TransactionReceiptEncodingConfiguration encodingOptionsWithCompaction =
        new TransactionReceiptEncodingConfiguration.Builder()
            .withCompactedLogs(true)
            .withBloomFilter(false)
            .build();

    final TransactionReceiptEncodingConfiguration encodingOptionsWithoutCompaction =
        new TransactionReceiptEncodingConfiguration.Builder()
            .withCompactedLogs(false)
            .withBloomFilter(true)
            .build();

    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt(Bytes.fromHexString("0x1122334455667788"));
    final Bytes compactedReceipt =
        RLP.encode(
            rlpOut ->
                TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptionsWithCompaction));
    final Bytes unCompactedReceipt =
        RLP.encode(
            rlpOut ->
                TransactionReceiptEncoder.writeTo(
                    receipt, rlpOut, encodingOptionsWithoutCompaction));
    assertThat(FrontierTransactionReceiptDecoder.readFrom(RLP.input(compactedReceipt), true))
        .isEqualTo(receipt);
    assertThat(FrontierTransactionReceiptDecoder.readFrom(RLP.input(unCompactedReceipt), true))
        .isEqualTo(receipt);
  }

  @Test
  public void toFromRlpEth69Receipt() {
    final TransactionReceiptEncodingConfiguration encodingConfiguration =
        TransactionReceiptEncodingConfiguration.ETH69_RECEIPT_CONFIGURATION;

    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt(Bytes.fromHexString("0x1122334455667788"));
    final TransactionReceipt copy =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut ->
                        TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingConfiguration))),
            true);
    assertThat(copy).isEqualTo(receipt);
  }

  @Test
  public void decodeEth69WithEmptyStringForType() {
    String encodedReceiptWith0x00AsType =
        "0xf85800808844e52a8ce6476327f84be494fccc2c35f0b84609e5f12c55dd85aba8d5d9bef7c08d72e5900112b81927ba5bb5f67ee594b4049bf0e4aed78db15d7bf2fc0c34e9a99de4efc08e8137ad659878f9e93df1f658367a";
    String encodedReceiptWith0x80AsType =
        "0xf85880808844e52a8ce6476327f84be494fccc2c35f0b84609e5f12c55dd85aba8d5d9bef7c08d72e5900112b81927ba5bb5f67ee594b4049bf0e4aed78db15d7bf2fc0c34e9a99de4efc08e8137ad659878f9e93df1f658367a";
    final TransactionReceipt with0x00 =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(Bytes.fromHexString(encodedReceiptWith0x00AsType)), false);
    final TransactionReceipt with0x80 =
        FrontierTransactionReceiptDecoder.readFrom(
            RLP.input(Bytes.fromHexString(encodedReceiptWith0x80AsType)), false);
    assertThat(with0x00).isEqualTo(with0x80);
  }
}

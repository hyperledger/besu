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

import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class TransactionReceiptTest {

  @Test
  public void toFromRlp() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt();
    final TransactionReceipt copy =
        TransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    output ->
                        TransactionReceiptEncoder.writeTo(
                            receipt, output, TransactionReceiptEncodingConfiguration.NETWORK))),
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
        TransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut ->
                        TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions))));
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
        TransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut ->
                        TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions))));
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
        TransactionReceiptDecoder.readFrom(
            RLP.input(
                RLP.encode(
                    rlpOut ->
                        TransactionReceiptEncoder.writeTo(receipt, rlpOut, encodingOptions))));
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
    assertThat(TransactionReceiptDecoder.readFrom(RLP.input(compactedReceipt))).isEqualTo(receipt);
    assertThat(TransactionReceiptDecoder.readFrom(RLP.input(unCompactedReceipt)))
        .isEqualTo(receipt);
  }
}

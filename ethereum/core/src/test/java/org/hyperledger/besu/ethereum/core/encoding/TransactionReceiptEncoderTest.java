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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TransactionReceiptEncoderTest {
  @Test
  public void testEncode() {
    Hash stateRoot = Hash.fromHexStringLenient("01");
    final long cumulativeGasUsed = 2;
    final List<Log> logs =
        List.of(
            new Log(
                Address.fromHexString("03"),
                Bytes.fromHexStringLenient("04"),
                List.of(LogTopic.fromHexString("05"))));
    final Optional<Bytes> revertReason = Optional.of(Bytes.fromHexString("06"));
    TransactionReceipt transactionReceipt =
        new TransactionReceipt(stateRoot, cumulativeGasUsed, logs, revertReason);

    TransactionReceiptEncoder transactionReceiptRlpEncoder = new TransactionReceiptEncoder();
    Bytes actualRlp =
        transactionReceiptRlpEncoder.encode(
            List.of(transactionReceipt), TransactionReceiptEncodingConfiguration.DEFAULT);

    String expectedRlpHex =
        "0xf90161a0000000000000000000000000000000000000000000000000000000000000000102b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000010800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000020f83af838940000000000000000000000000000000000000003e1a0000000000000000000000000000000000000000000000000000000000000000504";
    Assertions.assertEquals(expectedRlpHex, actualRlp.toHexString());
  }

  @Test
  public void testWriteSyncReceiptForRootCalcForFrontierTransaction() {
    Hash stateRoot = Hash.fromHexStringLenient("01");
    final long cumulativeGasUsed = 2;
    final List<Log> logs =
        List.of(
            new Log(
                Address.fromHexString("03"),
                Bytes.fromHexStringLenient("04"),
                List.of(LogTopic.fromHexString("05"))));
    final LogsBloomFilter bloomFilter = LogsBloomFilter.fromHexString("0x" + "deadbeef".repeat(64));
    final Optional<Bytes> revertReason = Optional.of(Bytes.fromHexString("06"));
    TransactionReceipt transactionReceipt =
        new TransactionReceipt(
            TransactionType.FRONTIER,
            stateRoot,
            cumulativeGasUsed,
            logs,
            bloomFilter,
            revertReason);

    Bytes encodedReceipt =
        RLP.encode(
            (rlpOut) ->
                TransactionReceiptEncoder.writeTo(
                    transactionReceipt, rlpOut, TransactionReceiptEncodingConfiguration.DEFAULT));
    Bytes encodedReceiptForTrieRoot =
        RLP.encode(
            (rlpOut) ->
                TransactionReceiptEncoder.writeTo(
                    transactionReceipt, rlpOut, TransactionReceiptEncodingConfiguration.TRIE_ROOT));

    Bytes encodedSyncReceipt =
        TransactionReceiptEncoder.writeSyncReceiptForRootCalc(
            new SyncTransactionReceipt(encodedReceipt));

    Assertions.assertEquals(
        encodedReceiptForTrieRoot.toHexString(), encodedSyncReceipt.toHexString());
  }

  @Test
  public void testWriteSyncReceiptForRootCalcForNonFrontierTransaction() {
    Hash stateRoot = Hash.fromHexStringLenient("01");
    final long cumulativeGasUsed = 2;
    final List<Log> logs =
        List.of(
            new Log(
                Address.fromHexString("03"),
                Bytes.fromHexStringLenient("04"),
                List.of(LogTopic.fromHexString("05"))));
    final LogsBloomFilter bloomFilter = LogsBloomFilter.fromHexString("0x" + "deadbeef".repeat(64));
    final Optional<Bytes> revertReason = Optional.of(Bytes.fromHexString("06"));
    TransactionReceipt transactionReceipt =
        new TransactionReceipt(
            TransactionType.EIP1559, stateRoot, cumulativeGasUsed, logs, bloomFilter, revertReason);

    Bytes encodedReceipt =
        RLP.encode(
            (rlpOut) ->
                TransactionReceiptEncoder.writeTo(
                    transactionReceipt, rlpOut, TransactionReceiptEncodingConfiguration.DEFAULT));
    Bytes encodedReceiptForTrieRoot =
        RLP.encode(
            (rlpOut) ->
                TransactionReceiptEncoder.writeTo(
                    transactionReceipt, rlpOut, TransactionReceiptEncodingConfiguration.TRIE_ROOT));

    Bytes encodedSyncReceipt =
        TransactionReceiptEncoder.writeSyncReceiptForRootCalc(
            new SyncTransactionReceipt(encodedReceipt));

    Assertions.assertEquals(
        encodedReceiptForTrieRoot.toHexString(), encodedSyncReceipt.toHexString());
  }

  @Test
  public void testRealWorldReceipt() {
    Bytes rawRlp = Bytes.fromHexString("0xc68001825208c0");

    TransactionReceipt transactionReceipt =
        TransactionReceiptDecoder.readFrom(RLP.input(rawRlp), false);
    SyncTransactionReceipt syncTransactionReceipt = new SyncTransactionReceipt(rawRlp);

    Bytes reencodedTransactionReceipt =
        RLP.encode(
            (rlpOut) ->
                TransactionReceiptEncoder.writeTo(
                    transactionReceipt, rlpOut, TransactionReceiptEncodingConfiguration.TRIE_ROOT));
    Bytes reencodedSyncTransactionReceipt =
        TransactionReceiptEncoder.writeSyncReceiptForRootCalc(syncTransactionReceipt);

    Assertions.assertEquals(
        reencodedTransactionReceipt.toHexString(), reencodedSyncTransactionReceipt.toHexString());
  }
}

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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SyncTransactionReceiptDecoderTest {

  private SyncTransactionReceiptDecoder syncTransactionReceiptDecoder;

  @BeforeEach
  public void beforeTest() {
    syncTransactionReceiptDecoder = new SyncTransactionReceiptDecoder();
  }

  @Test
  public void testDecodeLegacyReceipt() {
    final Hash stateRoot = Hash.fromHexStringLenient("01");
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

    SyncTransactionReceipt syncTransactionReceipt =
        syncTransactionReceiptDecoder.decode(encodedReceipt);

    Assertions.assertEquals(encodedReceipt, syncTransactionReceipt.rlpBytes());
    Assertions.assertEquals(
        Bytes.of(TransactionType.FRONTIER.getSerializedType()),
        syncTransactionReceipt.transactionTypeCode());
    Assertions.assertEquals(stateRoot, syncTransactionReceipt.statusOrStateRoot());
    Assertions.assertEquals(
        Bytes.of((byte) cumulativeGasUsed), syncTransactionReceipt.cumulativeGasUsed());
    Assertions.assertEquals(bloomFilter, syncTransactionReceipt.bloomFilter());
  }

  @Test
    public void testDecodeEth69Receipt() {
      final Hash stateRoot = Hash.fromHexStringLenient("01");
      final long cumulativeGasUsed = 2;
      final List<Log> logs =
              List.of(
                      new Log(
                              Address.fromHexString("03"),
                              Bytes.fromHexStringLenient("04"),
                              List.of(LogTopic.fromHexString("05"))));
      TransactionReceipt transactionReceipt =
              new TransactionReceipt(stateRoot, cumulativeGasUsed, logs, Optional.empty());

      Bytes encodedReceipt =
              RLP.encode(
                      (rlpOut) ->
                              TransactionReceiptEncoder.writeTo(
                                      transactionReceipt, rlpOut, TransactionReceiptEncodingConfiguration.ETH69_RECEIPT_CONFIGURATION));

      SyncTransactionReceipt syncTransactionReceipt =
              syncTransactionReceiptDecoder.decode(encodedReceipt);

      Assertions.assertEquals(encodedReceipt, syncTransactionReceipt.rlpBytes());
      Assertions.assertEquals(
              Bytes.of(TransactionType.FRONTIER.getEthSerializedType()),
              syncTransactionReceipt.transactionTypeCode());
      Assertions.assertEquals(stateRoot, syncTransactionReceipt.statusOrStateRoot());
      Assertions.assertEquals(
              Bytes.of((byte) cumulativeGasUsed), syncTransactionReceipt.cumulativeGasUsed());
      Assertions.assertEquals(1, syncTransactionReceipt.logs().size());
      Assertions.assertEquals(3, syncTransactionReceipt.logs().getFirst().size());
      String expectedBloomFilterHex = "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000010800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000020";
      Assertions.assertEquals(expectedBloomFilterHex, syncTransactionReceipt.bloomFilter().toHexString());
  }

  @Test
    public void testDecodeTypedReceipt() {
      final TransactionType transactionType = TransactionType.EIP1559;
      final Hash stateRoot = Hash.fromHexStringLenient("01");
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
                      transactionType,
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

      SyncTransactionReceipt syncTransactionReceipt =
              syncTransactionReceiptDecoder.decode(encodedReceipt);

      Assertions.assertEquals(encodedReceipt, syncTransactionReceipt.rlpBytes());
      Assertions.assertEquals(
              Bytes.of(transactionType.getSerializedType()),
              syncTransactionReceipt.transactionTypeCode());
      Assertions.assertEquals(stateRoot, syncTransactionReceipt.statusOrStateRoot());
      Assertions.assertEquals(
              Bytes.of((byte) cumulativeGasUsed), syncTransactionReceipt.cumulativeGasUsed());
      Assertions.assertEquals(bloomFilter, syncTransactionReceipt.bloomFilter());
  }
}

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
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BlockHeaderEncoderTest {
  @Test
  public void testEncode() {
    Hash parentHash = Hash.fromHexStringLenient("01");
    Hash ommersHash = Hash.fromHexStringLenient("02");
    Address coinbase = Address.fromHexString("03");
    Hash stateRoot = Hash.fromHexStringLenient("04");
    Hash transactionsRoot = Hash.fromHexStringLenient("05");
    Hash receiptsRoot = Hash.fromHexStringLenient("06");
    LogsBloomFilter logsBloomFilter =
        LogsBloomFilter.builder().insertBytes(Bytes.fromHexString("07")).build();
    Difficulty difficulty = Difficulty.of(8);
    long number = 9;
    long gasLimit = 10;
    long gasUsed = 11;
    long timestamp = 12;
    Bytes extraData = Bytes.fromHexString("000D");
    Wei baseFee = Wei.of(14);
    Bytes32 mixHashOrPrevRandao = Bytes32.fromHexStringLenient("0F");
    long nonce = 16;
    Hash withdrawalsRoot = Hash.fromHexStringLenient("11");
    Long blobGasUsed = 18L;
    BlobGas excessBlobGas = BlobGas.of(19);
    Bytes32 parentBeaconBlockRoot = Bytes32.fromHexStringLenient("14");
    Hash requestsHash = Hash.fromHexStringLenient("15");
    Hash balHash = Hash.fromHexStringLenient("16");
    BlockHeaderFunctions blockHeaderFunctions = new MainnetBlockHeaderFunctions();

    BlockHeader blockHeader =
        new BlockHeader(
            parentHash,
            ommersHash,
            coinbase,
            stateRoot,
            transactionsRoot,
            receiptsRoot,
            logsBloomFilter,
            difficulty,
            number,
            gasLimit,
            gasUsed,
            timestamp,
            extraData,
            baseFee,
            mixHashOrPrevRandao,
            nonce,
            withdrawalsRoot,
            blobGasUsed,
            excessBlobGas,
            parentBeaconBlockRoot,
            requestsHash,
            balHash,
            blockHeaderFunctions);

    BlockHeaderEncoder blockHeaderEncoder = new BlockHeaderEncoder();
    Bytes actualRlp = blockHeaderEncoder.encode(blockHeader);

    String expectedRlpHex =
        "0xf90276a00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002940000000000000000000000000000000000000003a00000000000000000000000000000000000000000000000000000000000000004a00000000000000000000000000000000000000000000000000000000000000005a00000000000000000000000000000000000000000000000000000000000000006b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008000000000000000020000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008090a0b0c82000da0000000000000000000000000000000000000000000000000000000000000000f8800000000000000100ea000000000000000000000000000000000000000000000000000000000000000111213a00000000000000000000000000000000000000000000000000000000000000014a00000000000000000000000000000000000000000000000000000000000000015a00000000000000000000000000000000000000000000000000000000000000016";
    Assertions.assertEquals(expectedRlpHex, actualRlp.toHexString());
  }
}

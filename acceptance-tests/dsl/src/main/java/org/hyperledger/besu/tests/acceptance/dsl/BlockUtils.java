/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.tests.acceptance.dsl;

import static org.hyperledger.besu.ethereum.core.Hash.fromHexString;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import org.web3j.protocol.core.methods.response.EthBlock.Block;

public class BlockUtils {

  public static BlockHeader createBlockHeader(
      final Block block, final BlockHeaderFunctions blockHeaderFunctions) {
    final Hash mixHash =
        block.getMixHash() == null
            ? Hash.fromHexStringLenient("0x0")
            : fromHexString(block.getMixHash());
    return new BlockHeader(
        fromHexString(block.getParentHash()),
        fromHexString(block.getSha3Uncles()),
        Address.fromHexString(block.getMiner()),
        fromHexString(block.getStateRoot()),
        fromHexString(block.getTransactionsRoot()),
        fromHexString(block.getReceiptsRoot()),
        LogsBloomFilter.fromHexString(block.getLogsBloom()),
        UInt256.fromHexString(block.getDifficultyRaw()),
        block.getNumber().longValue(),
        block.getGasLimit().longValue(),
        block.getGasUsed().longValue(),
        block.getTimestamp().longValue(),
        BytesValue.fromHexString(block.getExtraData()),
        mixHash,
        block.getNonce().longValue(),
        blockHeaderFunctions);
  }
}

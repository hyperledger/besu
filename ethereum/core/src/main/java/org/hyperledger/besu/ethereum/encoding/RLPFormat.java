/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.encoding;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public interface RLPFormat {

  @FunctionalInterface
  interface Encoder<T> {
    void encode(T object, RLPOutput output);
  }

  @FunctionalInterface
  interface Decoder<T> {
    T decode(RLPInput input);
  }

  // TODO move this to ProtocolSchedule probably?
  static RLPFormat getLatest() {
    return new BerlinRLPFormat();
  }

  void encode(Transaction transaction, RLPOutput rlpOutput);

  Transaction decodeTransaction(RLPInput rlpInput);

  void encode(BlockBody blockBody, RLPOutput rlpOutput);

  BlockBody decodeBlockBody(RLPInput input, BlockHeaderFunctions blockHeaderFunctions);

  static BlockHeader decodeBlockHeader(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions) {
    input.enterList();
    final Hash parentHash = Hash.wrap(input.readBytes32());
    final Hash ommersHash = Hash.wrap(input.readBytes32());
    final Address coinbase = Address.readFrom(input);
    final Hash stateRoot = Hash.wrap(input.readBytes32());
    final Hash transactionsRoot = Hash.wrap(input.readBytes32());
    final Hash receiptsRoot = Hash.wrap(input.readBytes32());
    final LogsBloomFilter logsBloom = LogsBloomFilter.readFrom(input);
    final Difficulty difficulty = Difficulty.of(input.readUInt256Scalar());
    final long number = input.readLongScalar();
    final long gasLimit = input.readLongScalar();
    final long gasUsed = input.readLongScalar();
    final long timestamp = input.readLongScalar();
    final Bytes extraData = input.readBytes();
    final Hash mixHash = Hash.wrap(input.readBytes32());
    final long nonce = input.readLong();
    final Long baseFee =
        ExperimentalEIPs.eip1559Enabled && !input.isEndOfCurrentList()
            ? input.readLongScalar()
            : null;
    input.leaveList();
    return new BlockHeader(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFee,
        mixHash,
        nonce,
        blockHeaderFunctions);
  }

  static void encode(final Block block, final RLPOutput rlpOutput) {
    rlpOutput.startList();

    block.getHeader().writeTo(rlpOutput);
    final BlockBody blockBody = block.getBody();
    rlpOutput.writeList(blockBody.getTransactions(), getLatest()::encode);
    rlpOutput.writeList(blockBody.getOmmers(), BlockHeader::writeTo);

    rlpOutput.endList();
  }

  // TODO refactor rlpinputs/outputs so they're always in the same place
  static Block decodeBlock(
      final ProtocolSchedule protocolSchedule,
      final BlockHeaderFunctions blockHeaderFunctions,
      final RLPInput rlpInput) {
    rlpInput.enterList();
    final BlockHeader header = decodeBlockHeader(rlpInput, blockHeaderFunctions);
    final List<Transaction> transactions =
        rlpInput.readList(
            protocolSchedule.getByBlockNumber(header.getNumber()).getRLPFormat()
                ::decodeTransaction);
    final List<BlockHeader> ommers =
        rlpInput.readList(rlp -> decodeBlockHeader(rlp, blockHeaderFunctions));
    rlpInput.leaveList();

    return new Block(header, new BlockBody(transactions, ommers));
  }
}

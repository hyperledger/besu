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
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
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

  ImmutableMap<TransactionType, Encoder<Transaction>> TYPED_TRANSACTION_ENCODERS =
      ImmutableMap.of(TransactionType.EIP1559, RLPFormat::encodeEIP1559);

  // TODO replace all the encoders with static methods since we trust our own data format
  static void encode(final Transaction transaction, final RLPOutput rlpOutput) {
    if (transaction.getType().equals(TransactionType.FRONTIER)) {
      encodeFrontier(transaction, rlpOutput);
    } else {
      final TransactionType type = transaction.getType();
      final RLPFormat.Encoder<Transaction> encoder =
          Optional.ofNullable(TYPED_TRANSACTION_ENCODERS.get(type))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "Developer Error. A supported transaction type %s has no associated"
                                  + " encoding logic",
                              type)));
      // TODO change this to normal rlp encoding instead of this bytes.concat stuff
      rlpOutput.writeRaw(
          Bytes.concatenate(
              Bytes.of((byte) type.getSerializedType()),
              RLP.encode(output -> encoder.encode(transaction, output))));
    }
  }

  private static void encodeFrontier(final Transaction transaction, final RLPOutput rlpOutput) {
    rlpOutput.startList();
    rlpOutput.writeLongScalar(transaction.getNonce());
    rlpOutput.writeUInt256Scalar(transaction.getGasPrice());
    rlpOutput.writeLongScalar(transaction.getGasLimit());
    rlpOutput.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    rlpOutput.writeUInt256Scalar(transaction.getValue());
    rlpOutput.writeBytes(transaction.getPayload());
    writeSignature(transaction, rlpOutput);
    rlpOutput.endList();
  }

  private static void encodeEIP1559(final Transaction transaction, final RLPOutput rlpOutput) {
    ExperimentalEIPs.eip1559MustBeEnabled();

    rlpOutput.startList();
    rlpOutput.writeLongScalar(transaction.getNonce());
    rlpOutput.writeNull();
    rlpOutput.writeLongScalar(transaction.getGasLimit());
    rlpOutput.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    rlpOutput.writeUInt256Scalar(transaction.getValue());
    rlpOutput.writeBytes(transaction.getPayload());
    rlpOutput.writeUInt256Scalar(
        transaction.getGasPremium().map(Quantity::getValue).map(Wei::ofNumber).orElseThrow());
    rlpOutput.writeUInt256Scalar(
        transaction.getFeeCap().map(Quantity::getValue).map(Wei::ofNumber).orElseThrow());
    writeSignature(transaction, rlpOutput);
    rlpOutput.endList();
  }

  static void writeSignature(final Transaction transaction, final RLPOutput rlpOutput) {
    rlpOutput.writeBigIntegerScalar(transaction.getV());
    rlpOutput.writeBigIntegerScalar(transaction.getSignature().getR());
    rlpOutput.writeBigIntegerScalar(transaction.getSignature().getS());
  }

  Transaction decodeTransaction(RLPInput rlpInput);

  static void encode(final BlockHeader blockHeader, final RLPOutput rlpOutput) {
    rlpOutput.startList();

    rlpOutput.writeBytes(blockHeader.getParentHash());
    rlpOutput.writeBytes(blockHeader.getOmmersHash());
    rlpOutput.writeBytes(blockHeader.getCoinbase());
    rlpOutput.writeBytes(blockHeader.getStateRoot());
    rlpOutput.writeBytes(blockHeader.getTransactionsRoot());
    rlpOutput.writeBytes(blockHeader.getReceiptsRoot());
    rlpOutput.writeBytes(blockHeader.getLogsBloom());
    rlpOutput.writeUInt256Scalar(blockHeader.getDifficulty());
    rlpOutput.writeLongScalar(blockHeader.getNumber());
    rlpOutput.writeLongScalar(blockHeader.getGasLimit());
    rlpOutput.writeLongScalar(blockHeader.getGasUsed());
    rlpOutput.writeLongScalar(blockHeader.getTimestamp());
    rlpOutput.writeBytes(blockHeader.getExtraData());
    rlpOutput.writeBytes(blockHeader.getMixHash());
    rlpOutput.writeLong(blockHeader.getNonce());
    if (ExperimentalEIPs.eip1559Enabled) {
      blockHeader.getBaseFee().ifPresent(rlpOutput::writeLongScalar);
    }

    rlpOutput.endList();
  }

  static void encode(final BlockBody blockBody, final RLPOutput rlpOutput) {
    rlpOutput.startList();

    rlpOutput.writeList(blockBody.getTransactions(), RLPFormat::encode);
    rlpOutput.writeList(blockBody.getOmmers(), RLPFormat::encode);

    rlpOutput.endList();
  }

  BlockBody decodeBlockBody(RLPInput input, BlockHeaderFunctions blockHeaderFunctions);

  BlockHeader decodeBlockHeader(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions);

  static void encode(final Block block, final RLPOutput rlpOutput) {
    rlpOutput.startList();

    RLPFormat.encode(block.getHeader(), rlpOutput);
    final BlockBody blockBody = block.getBody();
    rlpOutput.writeList(blockBody.getTransactions(), RLPFormat::encode);
    rlpOutput.writeList(blockBody.getOmmers(), RLPFormat::encode);

    rlpOutput.endList();
  }

  // TODO wtf do I do about this standalone and the specific ones
  static BlockHeader decodeBlockHeaderStandalone(
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
    final Long baseFee =
        ExperimentalEIPs.eip1559Enabled && !input.isEndOfCurrentList()
            ? input.readLongScalar()
            : null;
    final Hash mixHash = Hash.wrap(input.readBytes32());
    final long nonce = input.readLong();
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

  // TODO move this to frontier instead of default
  // TODO refactor rlpinputs/outputs so they're always in the same place
  static Block decodeBlockStandalone(
      final ProtocolSchedule protocolSchedule,
      final BlockHeaderFunctions blockHeaderFunctions,
      final RLPInput rlpInput) {
    rlpInput.enterList();
    final BlockHeader header = decodeBlockHeaderStandalone(rlpInput, blockHeaderFunctions);
    final RLPFormat rlpFormat =
        protocolSchedule.getByBlockNumber(header.getNumber()).getRLPFormat();
    final List<Transaction> transactions = rlpInput.readList(rlpFormat::decodeTransaction);
    final List<BlockHeader> ommers =
        rlpInput.readList(rlp -> rlpFormat.decodeBlockHeader(rlp, blockHeaderFunctions));
    rlpInput.leaveList();

    return new Block(header, new BlockBody(transactions, ommers));
  }
}

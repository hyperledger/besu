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

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

/** A mined Ethereum block header. */
public class BlockHeader extends SealableBlockHeader
    implements org.hyperledger.besu.plugin.data.BlockHeader {

  public static final int MAX_EXTRA_DATA_BYTES = 32;

  public static final long GENESIS_BLOCK_NUMBER = 0L;

  private final Hash mixHash;

  private final long nonce;

  private final Supplier<Hash> hash;

  private final Supplier<ParsedExtraData> parsedExtraData;

  public BlockHeader(
      final Hash parentHash,
      final Hash ommersHash,
      final Address coinbase,
      final Hash stateRoot,
      final Hash transactionsRoot,
      final Hash receiptsRoot,
      final LogsBloomFilter logsBloom,
      final UInt256 difficulty,
      final long number,
      final long gasLimit,
      final long gasUsed,
      final long timestamp,
      final BytesValue extraData,
      final Hash mixHash,
      final long nonce,
      final BlockHeaderFunctions blockHeaderFunctions) {
    super(
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
        extraData);
    this.mixHash = mixHash;
    this.nonce = nonce;
    this.hash = Suppliers.memoize(() -> blockHeaderFunctions.hash(this));
    this.parsedExtraData = Suppliers.memoize(() -> blockHeaderFunctions.parseExtraData(this));
  }

  /**
   * Returns the block mixed hash.
   *
   * @return the block mixed hash
   */
  @Override
  public Hash getMixHash() {
    return mixHash;
  }

  /**
   * Returns the block nonce.
   *
   * @return the block nonce
   */
  @Override
  public long getNonce() {
    return nonce;
  }
  /**
   * Returns the block extra data field, as parsed by the {@link BlockHeaderFunctions}.
   *
   * @return the block extra data field
   */
  public ParsedExtraData getParsedExtraData() {
    return parsedExtraData.get();
  }

  /**
   * Returns the block header hash.
   *
   * @return the block header hash
   */
  public Hash getHash() {
    return hash.get();
  }

  @Override
  public org.hyperledger.besu.plugin.data.Hash getBlockHash() {
    return hash.get();
  }

  /**
   * Write an RLP representation.
   *
   * @param out The RLP output to write to
   */
  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeBytesValue(parentHash);
    out.writeBytesValue(ommersHash);
    out.writeBytesValue(coinbase);
    out.writeBytesValue(stateRoot);
    out.writeBytesValue(transactionsRoot);
    out.writeBytesValue(receiptsRoot);
    out.writeBytesValue(logsBloom.getBytes());
    out.writeUInt256Scalar(difficulty);
    out.writeLongScalar(number);
    out.writeLongScalar(gasLimit);
    out.writeLongScalar(gasUsed);
    out.writeLongScalar(timestamp);
    out.writeBytesValue(extraData);
    out.writeBytesValue(mixHash);
    out.writeLong(nonce);

    out.endList();
  }

  public static BlockHeader readFrom(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions) {
    input.enterList();
    final BlockHeader blockHeader =
        new BlockHeader(
            Hash.wrap(input.readBytes32()),
            Hash.wrap(input.readBytes32()),
            Address.readFrom(input),
            Hash.wrap(input.readBytes32()),
            Hash.wrap(input.readBytes32()),
            Hash.wrap(input.readBytes32()),
            LogsBloomFilter.readFrom(input),
            input.readUInt256Scalar(),
            input.readLongScalar(),
            input.readLongScalar(),
            input.readLongScalar(),
            input.readLongScalar(),
            input.readBytesValue(),
            Hash.wrap(input.readBytes32()),
            input.readLong(),
            blockHeaderFunctions);
    input.leaveList();
    return blockHeader;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BlockHeader)) {
      return false;
    }
    final BlockHeader other = (BlockHeader) obj;
    return getHash().equals(other.getHash());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHash());
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("BlockHeader{");
    sb.append("hash=").append(getHash()).append(", ");
    sb.append("parentHash=").append(parentHash).append(", ");
    sb.append("ommersHash=").append(ommersHash).append(", ");
    sb.append("coinbase=").append(coinbase).append(", ");
    sb.append("stateRoot=").append(stateRoot).append(", ");
    sb.append("transactionsRoot=").append(transactionsRoot).append(", ");
    sb.append("receiptsRoot=").append(receiptsRoot).append(", ");
    sb.append("logsBloom=").append(logsBloom).append(", ");
    sb.append("difficulty=").append(difficulty).append(", ");
    sb.append("number=").append(number).append(", ");
    sb.append("gasLimit=").append(gasLimit).append(", ");
    sb.append("gasUsed=").append(gasUsed).append(", ");
    sb.append("timestamp=").append(timestamp).append(", ");
    sb.append("extraData=").append(extraData).append(", ");
    sb.append("mixHash=").append(mixHash).append(", ");
    sb.append("nonce=").append(nonce);
    return sb.append("}").toString();
  }

  public static org.hyperledger.besu.ethereum.core.BlockHeader convertPluginBlockHeader(
      final org.hyperledger.besu.plugin.data.BlockHeader pluginBlockHeader,
      final BlockHeaderFunctions blockHeaderFunctions) {
    return new org.hyperledger.besu.ethereum.core.BlockHeader(
        Hash.fromHexString(pluginBlockHeader.getParentHash().getHexString()),
        Hash.fromHexString(pluginBlockHeader.getOmmersHash().getHexString()),
        org.hyperledger.besu.ethereum.core.Address.fromHexString(
            pluginBlockHeader.getCoinbase().getHexString()),
        Hash.fromHexString(pluginBlockHeader.getStateRoot().getHexString()),
        Hash.fromHexString(pluginBlockHeader.getTransactionsRoot().getHexString()),
        Hash.fromHexString(pluginBlockHeader.getReceiptsRoot().getHexString()),
        LogsBloomFilter.fromHexString(pluginBlockHeader.getLogsBloom().getHexString()),
        UInt256.fromHexString(pluginBlockHeader.getDifficulty().getHexString()),
        pluginBlockHeader.getNumber(),
        pluginBlockHeader.getGasLimit(),
        pluginBlockHeader.getGasUsed(),
        pluginBlockHeader.getTimestamp(),
        BytesValue.wrap(pluginBlockHeader.getExtraData().getByteArray()),
        Hash.fromHexString(pluginBlockHeader.getMixHash().getHexString()),
        pluginBlockHeader.getNonce(),
        blockHeaderFunctions);
  }
}

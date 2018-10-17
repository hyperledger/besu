/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.messages;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RlpUtils;
import tech.pegasys.pantheon.util.bytes.Bytes32;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;

/** PV62 GetBlockHeaders Message. */
public final class GetBlockHeadersMessage extends AbstractMessageData {

  public static GetBlockHeadersMessage readFrom(final MessageData message) {
    if (message instanceof GetBlockHeadersMessage) {
      message.retain();
      return (GetBlockHeadersMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.GET_BLOCK_HEADERS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetBlockHeadersMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new GetBlockHeadersMessage(data);
  }

  public static GetBlockHeadersMessage create(
      final long blockNum, final int maxHeaders, final boolean reverse, final int skip) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    tmp.writeLongScalar(blockNum);
    return create(maxHeaders, reverse, skip, tmp);
  }

  public static GetBlockHeadersMessage create(
      final Hash hash, final int maxHeaders, final boolean reverse, final int skip) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    tmp.writeBytesValue(hash);
    return create(maxHeaders, reverse, skip, tmp);
  }

  public static GetBlockHeadersMessage createForSingleHeader(final Hash hash) {
    return create(hash, 1, false, 0);
  }

  public static GetBlockHeadersMessage createForContiguousHeaders(
      final long blockNum, final int maxHeaders) {
    return create(blockNum, maxHeaders, false, 0);
  }

  public static GetBlockHeadersMessage createForContiguousHeaders(
      final Hash blockHash, final int maxHeaders) {
    return create(blockHash, maxHeaders, false, 0);
  }

  private GetBlockHeadersMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.GET_BLOCK_HEADERS;
  }

  /**
   * Returns the block number that the message requests or {@link OptionalLong#EMPTY} if the request
   * specifies a block hash.
   *
   * @return Block Number Requested or {@link OptionalLong#EMPTY}
   */
  public OptionalLong blockNumber() {
    final ByteBuffer raw = data.nioBuffer();
    final int offsetList = RlpUtils.decodeOffset(raw, 0);
    final int lengthFirst = RlpUtils.decodeLength(raw, offsetList);
    final int offsetFirst = RlpUtils.decodeOffset(raw, offsetList);
    if (lengthFirst - offsetFirst == Bytes32.SIZE) {
      return OptionalLong.empty();
    } else {
      final byte[] tmp = new byte[lengthFirst];
      raw.position(offsetList);
      raw.get(tmp);
      return OptionalLong.of(RlpUtils.readLong(0, lengthFirst, tmp));
    }
  }

  /**
   * Returns the block hash that the message requests or {@link Optional#EMPTY} if the request
   * specifies a block number.
   *
   * @return Block Hash Requested or {@link Optional#EMPTY}
   */
  public Optional<Hash> hash() {
    final ByteBuffer raw = data.nioBuffer();
    final int offsetList = RlpUtils.decodeOffset(raw, 0);
    final int lengthFirst = RlpUtils.decodeLength(raw, offsetList);
    final int offsetFirst = RlpUtils.decodeOffset(raw, offsetList);
    if (lengthFirst - offsetFirst == Bytes32.SIZE) {
      final byte[] hashBytes = new byte[Bytes32.SIZE];
      raw.position(offsetFirst + offsetList);
      raw.get(hashBytes);
      return Optional.of(Hash.wrap(Bytes32.wrap(hashBytes)));
    } else {
      return Optional.empty();
    }
  }

  public int maxHeaders() {
    final ByteBuffer raw = data.nioBuffer();
    final int offsetList = RlpUtils.decodeOffset(raw, 0);
    final byte[] tmp = new byte[raw.capacity()];
    raw.get(tmp);
    final int offsetMaxHeaders = RlpUtils.nextOffset(tmp, offsetList);
    final int lenMaxHeaders = RlpUtils.decodeLength(tmp, offsetMaxHeaders);
    return Ints.checkedCast(RlpUtils.readLong(offsetMaxHeaders, lenMaxHeaders, tmp));
  }

  public int skip() {
    final ByteBuffer raw = data.nioBuffer();
    final int offsetList = RlpUtils.decodeOffset(raw, 0);
    final byte[] tmp = new byte[raw.capacity()];
    raw.get(tmp);
    final int offsetSkip = RlpUtils.nextOffset(tmp, RlpUtils.nextOffset(tmp, offsetList));
    final int lenSkip = RlpUtils.decodeLength(tmp, offsetSkip);
    return Ints.checkedCast(RlpUtils.readLong(offsetSkip, lenSkip, tmp));
  }

  public boolean reverse() {
    return (data.getByte(this.getSize() - 1) & 0xff) != RlpUtils.RLP_ZERO;
  }

  private static GetBlockHeadersMessage create(
      final int maxHeaders, final boolean reverse, final int skip, final BytesValueRLPOutput tmp) {
    tmp.writeIntScalar(maxHeaders);
    tmp.writeIntScalar(skip);
    tmp.writeIntScalar(reverse ? 1 : 0);
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new GetBlockHeadersMessage(data);
  }
}

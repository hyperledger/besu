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
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Iterator;
import java.util.Objects;

import com.google.common.collect.Iterators;
import io.netty.buffer.ByteBuf;

public final class NewBlockHashesMessage extends AbstractMessageData {

  public static NewBlockHashesMessage readFrom(final MessageData message) {
    if (message instanceof NewBlockHashesMessage) {
      message.retain();
      return (NewBlockHashesMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.NEW_BLOCK_HASHES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a NewBlockHashesMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new NewBlockHashesMessage(data);
  }

  public static NewBlockHashesMessage create(
      final Iterable<NewBlockHashesMessage.NewBlockHash> hashes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    for (final NewBlockHashesMessage.NewBlockHash hash : hashes) {
      tmp.startList();
      tmp.writeBytesValue(hash.hash());
      tmp.writeLongScalar(hash.number());
      tmp.endList();
    }
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new NewBlockHashesMessage(data);
  }

  private NewBlockHashesMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.NEW_BLOCK_HASHES;
  }

  public Iterator<NewBlockHashesMessage.NewBlockHash> getNewHashes() {
    final byte[] hashes = new byte[data.readableBytes()];
    data.getBytes(0, hashes);
    return new BytesValueRLPInput(BytesValue.wrap(hashes), false)
        .readList(
            rlpInput -> {
              rlpInput.enterList();
              final NewBlockHashesMessage.NewBlockHash res =
                  new NewBlockHashesMessage.NewBlockHash(
                      Hash.wrap(rlpInput.readBytes32()), rlpInput.readLongScalar());
              rlpInput.leaveList();
              return res;
            })
        .iterator();
  }

  @Override
  public String toString() {
    return String.format("NewBlockHashesMessage: [%s]", Iterators.toString(getNewHashes()));
  }

  public static final class NewBlockHash {

    private final Hash hash;

    private final long number;

    public NewBlockHash(final Hash hash, final long number) {
      this.hash = hash;
      this.number = number;
    }

    public long number() {
      return number;
    }

    public Hash hash() {
      return hash;
    }

    @Override
    public String toString() {
      return String.format("New Block Hash [%d: %s]", number, hash);
    }

    @Override
    public boolean equals(final Object that) {
      if (this == that) {
        return true;
      }
      if (!(that instanceof NewBlockHashesMessage.NewBlockHash)) {
        return false;
      }
      final NewBlockHashesMessage.NewBlockHash other = (NewBlockHashesMessage.NewBlockHash) that;
      return other.hash.equals(hash) && other.number == number;
    }

    @Override
    public int hashCode() {
      return Objects.hash(hash, number);
    }
  }
}

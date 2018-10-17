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
package tech.pegasys.pantheon.ethereum.p2p.utils;

import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import io.netty.buffer.ByteBuf;

/** Utility methods for working with {@link ByteBuf}'s. */
public class ByteBufUtils {

  private ByteBufUtils() {}

  public static byte[] toByteArray(final ByteBuf buffer) {
    final byte[] bytes = new byte[buffer.readableBytes()];
    buffer.getBytes(buffer.readerIndex(), bytes);
    return bytes;
  }

  /**
   * Creates an {@link RLPInput} for the data in <code>buffer</code>. The data is copied from <code>
   * buffer</code> so that the {@link RLPInput} and any data read from it are safe to use even after
   * <code>buffer</code> is released.
   *
   * @param buffer the data to read as RLP
   * @return an {@link RLPInput} for the data in <code>buffer</code>
   */
  public static RLPInput toRLPInput(final ByteBuf buffer) {
    return RLP.input(BytesValue.wrap(toByteArray(buffer)));
  }

  public static ByteBuf fromRLPOutput(final BytesValueRLPOutput out) {
    final ByteBuf data = NetworkMemoryPool.allocate(out.encodedSize());
    data.writeBytes(out.encoded().extractArray());
    return data;
  }
}

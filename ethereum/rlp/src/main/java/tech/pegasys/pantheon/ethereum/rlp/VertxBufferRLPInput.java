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
package tech.pegasys.pantheon.ethereum.rlp;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.math.BigInteger;

import io.vertx.core.buffer.Buffer;

/** A {@link RLPInput} that decode RLP encoded data stored in a Vert.x {@link Buffer}. */
public class VertxBufferRLPInput extends AbstractRLPInput {

  // The RLP encoded data.
  private final Buffer buffer;
  // Offset in buffer from which to read.
  private final int bufferOffset;

  /**
   * A new {@link RLPInput} that decodes data from the provided buffer.
   *
   * @param buffer The buffer from which to read RLP data.
   * @param bufferOffset The offset in {@code buffer} in which the data to decode starts.
   * @param lenient Whether the created decoded should be lenient, that is ignore non-fatal
   *     malformation in the input.
   */
  public VertxBufferRLPInput(final Buffer buffer, final int bufferOffset, final boolean lenient) {
    super(lenient);
    this.buffer = buffer;
    this.bufferOffset = bufferOffset;
    init(buffer.length(), false);
  }

  /**
   * The total size of the encoded data in the {@link Buffer} wrapped by this object.
   *
   * @return The total size of the encoded data that this input decodes (note that this value never
   *     changes, it is not the size of data remaining to decode, but the size to decode at creation
   *     time).
   */
  public int encodedSize() {
    return Math.toIntExact(size);
  }

  @Override
  protected byte inputByte(final long offset) {
    return buffer.getByte(Math.toIntExact(bufferOffset + offset));
  }

  @Override
  protected BytesValue inputSlice(final long offset, final int length) {
    return BytesValue.wrapBuffer(buffer, Math.toIntExact(bufferOffset + offset), length);
  }

  @Override
  protected Bytes32 inputSlice32(final long offset) {
    return Bytes32.wrap(inputSlice(offset, Bytes32.SIZE), 0);
  }

  @Override
  protected String inputHex(final long offset, final int length) {
    return inputSlice(offset, length).toString().substring(2);
  }

  @Override
  protected BigInteger getUnsignedBigInteger(final long offset, final int length) {
    return BytesValues.asUnsignedBigInteger(inputSlice(offset, length));
  }

  @Override
  protected int getInt(final long offset) {
    return buffer.getInt(Math.toIntExact(bufferOffset + offset));
  }

  @Override
  protected long getLong(final long offset) {
    return buffer.getLong(Math.toIntExact(bufferOffset + offset));
  }

  @Override
  public BytesValue raw() {
    return BytesValue.wrap(buffer.getBytes());
  }
}

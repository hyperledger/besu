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

import tech.pegasys.pantheon.util.bytes.MutableBytesValue;

import io.vertx.core.buffer.Buffer;

/**
 * A {@link RLPOutput} that writes/appends the result of RLP encoding to a Vert.x {@link Buffer}.
 */
public class VertxBufferRLPOutput extends AbstractRLPOutput {
  /**
   * Appends the RLP-encoded data written to this output to the provided Vert.x {@link Buffer}.
   *
   * @param buffer The buffer to which to append the data to.
   */
  public void appendEncoded(final Buffer buffer) {
    final int size = encodedSize();
    if (size == 0) {
      return;
    }

    // We want to append to the buffer, and Buffer always grows to accommodate anything writing,
    // so we write the last byte we know we'll need to make it resize accordingly.
    final int start = buffer.length();
    buffer.setByte(start + size - 1, (byte) 0);
    writeEncoded(MutableBytesValue.wrapBuffer(buffer, start, size));
  }
}

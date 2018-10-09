package net.consensys.pantheon.ethereum.rlp;

import net.consensys.pantheon.util.bytes.MutableBytesValue;

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

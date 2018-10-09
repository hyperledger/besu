package net.consensys.pantheon.ethereum.rlp;

import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.bytes.MutableBytesValue;

/** An {@link RLPOutput} that writes RLP encoded data to a {@link BytesValue}. */
public class BytesValueRLPOutput extends AbstractRLPOutput {
  /**
   * Computes the final encoded data.
   *
   * @return A value containing the data written to this output RLP-encoded.
   */
  public BytesValue encoded() {
    final int size = encodedSize();
    if (size == 0) {
      return BytesValue.EMPTY;
    }

    final MutableBytesValue output = MutableBytesValue.create(size);
    writeEncoded(output);
    return output;
  }
}

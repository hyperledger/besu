package tech.pegasys.pantheon.ethereum.rlp;

import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.MutableBytesValue;

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

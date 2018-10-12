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

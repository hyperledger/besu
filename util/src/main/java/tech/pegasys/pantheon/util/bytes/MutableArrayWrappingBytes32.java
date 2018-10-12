package tech.pegasys.pantheon.util.bytes;

/** An implementation of {@link MutableBytes32} backed by a byte array ({@code byte[]}). */
class MutableArrayWrappingBytes32 extends MutableArrayWrappingBytesValue implements MutableBytes32 {

  MutableArrayWrappingBytes32(final byte[] bytes) {
    this(bytes, 0);
  }

  MutableArrayWrappingBytes32(final byte[] bytes, final int offset) {
    super(bytes, offset, SIZE);
  }

  @Override
  public Bytes32 copy() {
    // We *must* override this method because ArrayWrappingBytes32 assumes that it is the case.
    return new ArrayWrappingBytes32(arrayCopy());
  }

  @Override
  public MutableBytes32 mutableCopy() {
    return new MutableArrayWrappingBytes32(arrayCopy());
  }
}

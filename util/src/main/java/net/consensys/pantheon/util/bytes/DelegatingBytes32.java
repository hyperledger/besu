package net.consensys.pantheon.util.bytes;

public class DelegatingBytes32 extends BaseDelegatingBytesValue<Bytes32> implements Bytes32 {
  protected DelegatingBytes32(final Bytes32 wrapped) {
    super(wrapped);
  }

  @Override
  public Bytes32 copy() {
    return wrapped.copy();
  }

  @Override
  public MutableBytes32 mutableCopy() {
    return wrapped.mutableCopy();
  }
}

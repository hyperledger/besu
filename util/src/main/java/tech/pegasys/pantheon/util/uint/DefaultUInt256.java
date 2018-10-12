package tech.pegasys.pantheon.util.uint;

import tech.pegasys.pantheon.util.bytes.Bytes32;

/**
 * Default implementation of a {@link UInt256}.
 *
 * <p>Note that this class is not meant to be exposed outside of this package. Use {@link UInt256}
 * static methods to build {@link UInt256} values instead.
 */
class DefaultUInt256 extends AbstractUInt256Value<UInt256> implements UInt256 {

  DefaultUInt256(final Bytes32 bytes) {
    super(bytes, UInt256Counter::new);
  }

  static Counter<UInt256> newVar() {
    return new UInt256Counter();
  }

  @Override
  public UInt256 asUInt256() {
    return this;
  }

  private static class UInt256Counter extends Counter<UInt256> {
    private UInt256Counter() {
      super(DefaultUInt256::new);
    }
  }
}

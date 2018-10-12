package tech.pegasys.pantheon.util.bytes;

public class DelegatingBytesValue extends BaseDelegatingBytesValue<BytesValue>
    implements BytesValue {

  protected DelegatingBytesValue(final BytesValue wrapped) {
    super(unwrap(wrapped));
  }

  // Make sure we don't end-up with giant chains of delegating through wrapping.
  private static BytesValue unwrap(final BytesValue v) {
    // Using a loop, because we could have DelegatingBytesValue intertwined with
    // DelegatingMutableBytesValue in theory.
    BytesValue wrapped = v;

    while (wrapped instanceof BaseDelegatingBytesValue) {
      wrapped = ((BaseDelegatingBytesValue) wrapped).wrapped;
    }
    return wrapped;
  }
}

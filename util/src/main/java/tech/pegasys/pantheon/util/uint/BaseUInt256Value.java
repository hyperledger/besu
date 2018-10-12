package tech.pegasys.pantheon.util.uint;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.util.bytes.Bytes32;

import java.math.BigInteger;
import java.util.function.Supplier;

/**
 * Base class for {@link UInt256Value}.
 *
 * <p>This class is abstract as it is not meant to be used directly, but it has no abstract methods.
 * As mentioned in {@link UInt256Value}, this is used to create strongly-typed type aliases of
 * {@link UInt256}. In other words, this allow to "tag" numbers with the unit of what they represent
 * for the type-system, which can help clarity, but also forbid mixing numbers that are mean to be
 * of different units (the strongly type part).
 *
 * <p>This class implements {@link UInt256Value}, but also add a few operations that take a {@link
 * UInt256} directly, for instance {@link #times(UInt256)}. The rational is that multiplying a given
 * quantity of something by a "raw" number is always meaningful, and return a new quantity of the
 * same thing.
 *
 * @param <T> The concrete type of the value.
 */
public abstract class BaseUInt256Value<T extends UInt256Value<T>> extends AbstractUInt256Value<T> {

  protected BaseUInt256Value(final Bytes32 bytes, final Supplier<Counter<T>> mutableCtor) {
    super(bytes, mutableCtor);
  }

  protected BaseUInt256Value(final long v, final Supplier<Counter<T>> mutableCtor) {
    this(UInt256Bytes.of(v), mutableCtor);
    checkArgument(v >= 0, "Invalid negative value %s for an unsigned scalar", v);
  }

  protected BaseUInt256Value(final BigInteger v, final Supplier<Counter<T>> mutableCtor) {
    this(UInt256Bytes.of(v), mutableCtor);
    checkArgument(v.signum() >= 0, "Invalid negative value %s for an unsigned scalar", v);
  }

  protected BaseUInt256Value(final String hexString, final Supplier<Counter<T>> mutableCtor) {
    this(Bytes32.fromHexStringLenient(hexString), mutableCtor);
  }

  public T times(final UInt256 value) {
    return binaryOp(value, UInt256Bytes::multiply);
  }

  public T mod(final UInt256 value) {
    return binaryOp(value, UInt256Bytes::modulo);
  }

  public int compareTo(final UInt256 other) {
    return UInt256Bytes.compareUnsigned(this.bytes, other.getBytes());
  }

  @Override
  public UInt256 asUInt256() {
    return new DefaultUInt256(bytes);
  }
}

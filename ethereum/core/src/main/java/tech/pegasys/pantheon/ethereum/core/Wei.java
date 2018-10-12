package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.BaseUInt256Value;
import tech.pegasys.pantheon.util.uint.Counter;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;

/** A particular quantity of Wei, the Ethereum currency. */
public final class Wei extends BaseUInt256Value<Wei> {

  public static final Wei ZERO = of(0);

  protected Wei(final Bytes32 bytes) {
    super(bytes, WeiCounter::new);
  }

  private Wei(final long v) {
    super(v, WeiCounter::new);
  }

  private Wei(final BigInteger v) {
    super(v, WeiCounter::new);
  }

  private Wei(final String hexString) {
    super(hexString, WeiCounter::new);
  }

  public static Wei of(final long value) {
    return new Wei(value);
  }

  public static Wei of(final BigInteger value) {
    return new Wei(value);
  }

  public static Wei of(final UInt256 value) {
    return new Wei(value.getBytes().copy());
  }

  public static Wei wrap(final Bytes32 value) {
    return new Wei(value);
  }

  public static Wei fromHexString(final String str) {
    return new Wei(str);
  }

  public static Wei fromEth(final long eth) {
    return Wei.of(BigInteger.valueOf(eth).multiply(BigInteger.TEN.pow(18)));
  }

  public static Counter<Wei> newCounter() {
    return new WeiCounter();
  }

  private static class WeiCounter extends Counter<Wei> {
    private WeiCounter() {
      super(Wei::new);
    }
  }
}

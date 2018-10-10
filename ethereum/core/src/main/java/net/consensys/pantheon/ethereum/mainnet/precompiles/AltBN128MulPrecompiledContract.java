package net.consensys.pantheon.ethereum.mainnet.precompiles;

import net.consensys.pantheon.crypto.altbn128.AltBn128Point;
import net.consensys.pantheon.crypto.altbn128.Fq;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.bytes.MutableBytesValue;

import java.math.BigInteger;
import java.util.Arrays;

public class AltBN128MulPrecompiledContract extends AbstractPrecompiledContract {

  private static final BigInteger MAX_N =
      new BigInteger(
          "115792089237316195423570985008687907853269984665640564039457584007913129639935");

  public AltBN128MulPrecompiledContract(final GasCalculator gasCalculator) {
    super("AltBn128Mul", gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return Gas.of(40_000L);
  }

  @Override
  public BytesValue compute(final BytesValue input) {
    final BigInteger x = extractParameter(input, 0, 32);
    final BigInteger y = extractParameter(input, 32, 32);
    final BigInteger n = extractParameter(input, 64, 32);

    final AltBn128Point p = new AltBn128Point(Fq.create(x), Fq.create(y));
    if (!p.isOnCurve() || n.compareTo(MAX_N) > 0) {
      return null;
    }
    final AltBn128Point product = p.multiply(n);

    final BytesValue xResult = product.getX().toBytesValue();
    final BytesValue yResult = product.getY().toBytesValue();
    final MutableBytesValue result = MutableBytesValue.create(64);
    xResult.copyTo(result, 32 - xResult.size());
    yResult.copyTo(result, 64 - yResult.size());

    return result;
  }

  private static BigInteger extractParameter(
      final BytesValue input, final int offset, final int length) {
    if (offset > input.size() || length == 0) {
      return BigInteger.ZERO;
    }
    final byte[] raw = Arrays.copyOfRange(input.extractArray(), offset, offset + length);
    return new BigInteger(1, raw);
  }
}

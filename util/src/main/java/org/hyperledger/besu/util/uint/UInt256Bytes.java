/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.util.uint;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.Bytes32s;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.bytes.MutableBytes32;

import java.math.BigInteger;
import java.util.function.BinaryOperator;

import com.google.common.annotations.VisibleForTesting;

/**
 * Static operations to work on bytes interpreted as 256 bytes unsigned integers.
 *
 * <p>This class is the base of the operations on {@link UInt256} and {@link UInt256Value}, but can
 * also be used to work directly on bytes if necessary.
 *
 * <p>All operations that write a result are written assuming that the result may be the same object
 * than one or more of the operands.
 */
public abstract class UInt256Bytes {

  private UInt256Bytes() {}

  interface UnaryOp {
    void applyOp(Bytes32 v, MutableBytes32 result);
  }

  interface BinaryOp {
    void applyOp(Bytes32 v1, Bytes32 v2, MutableBytes32 result);
  }

  interface BinaryLongOp {
    void applyOp(Bytes32 v1, long v2, MutableBytes32 result);
  }

  interface TernaryOp {
    void applyOp(Bytes32 v1, Bytes32 v2, Bytes32 v3, MutableBytes32 result);
  }

  private static final int SIZE = Bytes32.SIZE;

  private static final BigInteger P256 = BigInteger.valueOf(2).pow(8 * 32);

  /** The number of ints a word contains. */
  private static final int INT_SIZE = 32 / 4;

  private static final byte ALL_ZERO_BYTE = (byte) 0x00;
  private static final byte ALL_ONE_BYTE = (byte) 0xFF;

  private static final UInt256 U_31 = UInt256.of(31);

  /** This mask is used to obtain the value of an int as if it were unsigned. */
  private static final long LONG_MASK = 0xffffffffL;

  static void copyPadded(
      final BytesValue toCopy, final MutableBytes32 destination, final byte padByte) {
    final int copySize = toCopy.size();
    if (copySize == SIZE) {
      toCopy.copyTo(destination);
    } else if (copySize > SIZE) {
      toCopy.slice(copySize - SIZE, SIZE).copyTo(destination);
    } else {
      final int padding = SIZE - toCopy.size();
      destination.mutableSlice(0, padding).fill(padByte);
      toCopy.copyTo(destination, padding);
    }
  }

  private static void copy(final BigInteger result, final MutableBytes32 destination) {
    copyPadded(BytesValue.wrap(result.toByteArray()), destination, ALL_ZERO_BYTE);
  }

  private interface TriOperator<T> {
    T apply(T op1, T op2, T op3);
  }

  private static void doOnBigInteger(
      final Bytes32 v1,
      final Bytes32 v2,
      final MutableBytes32 dest,
      final BinaryOperator<BigInteger> operator) {
    final BigInteger i1 = BytesValues.asUnsignedBigInteger(v1);
    final BigInteger i2 = BytesValues.asUnsignedBigInteger(v2);
    final BigInteger result = operator.apply(i1, i2);
    copy(result, dest);
  }

  private static void doOnBigInteger(
      final Bytes32 v1,
      final Bytes32 v2,
      final Bytes32 v3,
      final MutableBytes32 dest,
      final TriOperator<BigInteger> operator) {
    final BigInteger i1 = BytesValues.asUnsignedBigInteger(v1);
    final BigInteger i2 = BytesValues.asUnsignedBigInteger(v2);
    final BigInteger i3 = BytesValues.asUnsignedBigInteger(v3);
    final BigInteger result = operator.apply(i1, i2, i3);
    copy(result, dest);
  }

  public static void add(final Bytes32 v1, final Bytes32 v2, final MutableBytes32 result) {
    long carry = 0;

    // Add ints from the right hand side, propagating any carry.
    for (int i = INT_SIZE - 1; i >= 0; i--) {
      final long sum = (v1.getInt(i * 4) & LONG_MASK) + (v2.getInt(i * 4) & LONG_MASK) + carry;
      result.setInt(i * 4, (int) sum);
      carry = sum >>> 32;
    }
    // Discard the final carry since we work modulo 256.
  }

  public static void add(final Bytes32 v1, final long v2, final MutableBytes32 result) {
    final long sum1 = (v1.getInt(SIZE - 4) & LONG_MASK) + (v2 & LONG_MASK);
    result.setInt(SIZE - 4, (int) sum1);

    final long sum2 = (v1.getInt(SIZE - 8) & LONG_MASK) + (v2 >>> 32) + (sum1 >>> 32);
    result.setInt(SIZE - 8, (int) sum2);

    long carry = (int) (sum2 >>> 32);
    for (int i = INT_SIZE - 3; i >= 0; i--) {
      final long sum = (v1.getInt(i * 4) & LONG_MASK) + carry;
      result.setInt(i * 4, (int) sum);
      carry = sum >>> 32;
    }
  }

  public static void addModulo(
      final Bytes32 v1, final Bytes32 v2, final Bytes32 modulo, final MutableBytes32 result) {
    if (modulo.isZero()) {
      result.clear();
    } else {
      doOnBigInteger(v1, v2, modulo, result, (op1, op2, mod) -> op1.add(op2).mod(mod));
    }
  }

  public static void subtract(final Bytes32 v1, final Bytes32 v2, final MutableBytes32 result) {
    final int sign = compareUnsigned(v1, v2);

    if (sign == 0) {
      result.clear();
      return;
    }

    final Bytes32 a;
    final Bytes32 b;
    if (sign > 0) {
      a = v1;
      b = v2;
    } else {
      a = v2;
      b = v1;
    }

    long diff = 0;

    for (int i = INT_SIZE - 1; i >= 0; i--) {
      diff = (a.getInt(i * 4) & LONG_MASK) - (b.getInt(i * 4) & LONG_MASK) - ((int) -(diff >> 32));
      result.setInt(i * 4, (int) diff);
    }

    if (sign < 0) {
      negateSigned(result, result);
    }
  }

  public static void subtract(final Bytes32 v1, final long v2, final MutableBytes32 result) {
    final int sign = fitsLong(v1) ? Long.compare(v1.getLong(SIZE - 8), v2) : 1;
    if (sign == 0) {
      result.clear();
      return;
    }

    if (sign < 0) {
      final long diff1 = (v2 & LONG_MASK) - (v1.getInt(SIZE - 4) & LONG_MASK);
      result.setInt(SIZE - 4, (int) diff1);

      final long diff2 = (v2 >>> 32) - (v1.getInt(SIZE - 8) & LONG_MASK) - ((int) -(diff1 >> 32));
      result.setInt(SIZE - 8, (int) diff2);

      long diff = diff2;
      for (int i = INT_SIZE - 3; i >= 0; i--) {
        diff = -(v1.getInt(i * 4) & LONG_MASK) - ((int) -(diff >> 32));
        result.setInt(i * 4, (int) diff);
      }
      negateSigned(result, result);
    } else {
      final long diff1 = (v1.getInt(SIZE - 4) & LONG_MASK) - (v2 & LONG_MASK);
      result.setInt(SIZE - 4, (int) diff1);

      final long diff2 = (v1.getInt(SIZE - 8) & LONG_MASK) - (v2 >>> 32) - ((int) -(diff1 >> 32));
      result.setInt(SIZE - 8, (int) diff2);

      long diff = diff2;
      for (int i = INT_SIZE - 3; i >= 0; i--) {
        diff = (v1.getInt(i * 4) & LONG_MASK) - ((int) -(diff >> 32));
        result.setInt(i * 4, (int) diff);
        if (diff == 0) break;
      }
    }
  }

  private static void negateSigned(final Bytes32 v, final MutableBytes32 result) {
    Bytes32s.not(v, result);
    add(result, UInt256.ONE.getBytes(), result);
  }

  private static boolean isPowerOf2(final long n) {
    return (n ^ (n - 1)) == 0;
  }

  public static void multiply(final Bytes32 v1, final Bytes32 v2, final MutableBytes32 result) {
    doOnBigInteger(v1, v2, result, BigInteger::multiply);
  }

  public static void multiply(final Bytes32 v1, final long v2, final MutableBytes32 result) {
    if (v2 == 0) {
      result.clear();
    } else if (v2 > 0 && isPowerOf2(v2)) {
      final int shifts = log2(v2);
      // We have to be careful with overflowing operation.
      if (bitLength(v1) >= SIZE - 1 - shifts) {
        doOnBigInteger(v1, of(v2), result, BigInteger::multiply);
      } else {
        shiftLeft(v1, shifts, result);
      }
    } else {
      doOnBigInteger(v1, of(v2), result, BigInteger::multiply);
    }
  }

  public static void multiplyModulo(
      final Bytes32 v1, final Bytes32 v2, final Bytes32 modulo, final MutableBytes32 result) {
    if (modulo.isZero()) {
      result.clear();
    } else {
      doOnBigInteger(v1, v2, modulo, result, (op1, op2, mod) -> op1.multiply(op2).mod(mod));
    }
  }

  public static void divide(final Bytes32 v1, final Bytes32 v2, final MutableBytes32 result) {
    if (v2.isZero()) {
      result.clear();
    } else {
      doOnBigInteger(v1, v2, result, BigInteger::divide);
    }
  }

  // Assumes v > 0
  private static int log2(final long v) {
    return 63 - Long.numberOfLeadingZeros(v);
  }

  private static int maskByteRightBits(final int bits) {
    return ~(0xFFFFFFFF << bits);
  }

  @VisibleForTesting
  static void shiftRight(final Bytes32 v1, final int v2, final MutableBytes32 result) {
    final int d = v2 / 8;
    final int s = v2 % 8;
    int resIdx = SIZE - 1;
    for (int i = SIZE - 1 - d; i >= 0; i--) {
      final int leftSide = (v1.get(i) & 0xFF) >>> s;
      final int rightSide = i == 0 ? 0 : v1.get(i - 1) << (8 - s);
      result.set(resIdx--, (byte) (leftSide | rightSide));
    }
    for (; resIdx >= 0; resIdx--) {
      result.set(resIdx, (byte) 0);
    }
  }

  @VisibleForTesting
  static void shiftLeft(final Bytes32 v1, final int v2, final MutableBytes32 result) {
    final int d = v2 / 8;
    final int s = v2 % 8;
    int resIdx = 0;
    for (int i = d; i < SIZE; i++) {
      final int leftSide = v1.get(i) << s;
      final int rightSide = i == SIZE - 1 ? 0 : (v1.get(i + 1) & 0xFF) >>> (8 - s);
      result.set(resIdx++, (byte) (leftSide | rightSide));
    }
    for (; resIdx < SIZE; resIdx++) {
      result.set(resIdx, (byte) 0);
    }
  }

  public static void divide(final Bytes32 v1, final long v2, final MutableBytes32 result) {
    if (v2 == 0) {
      result.clear();
    } else if (v2 > 0 && isPowerOf2(v2)) {
      shiftRight(v1, log2(v2), result);
    } else {
      doOnBigInteger(v1, of(v2), result, BigInteger::divide);
    }
  }

  public static void exponent(final Bytes32 v1, final Bytes32 v2, final MutableBytes32 result) {
    doOnBigInteger(v1, v2, result, (val, pow) -> val.modPow(pow, P256));
  }

  public static void modulo(final Bytes32 v1, final Bytes32 v2, final MutableBytes32 result) {
    if (v2.isZero()) {
      result.clear();
    } else {
      doOnBigInteger(v1, v2, result, BigInteger::mod);
    }
  }

  public static void modulo(final Bytes32 v1, final long v2, final MutableBytes32 result) {
    if (v2 == 0) {
      result.clear();
    } else if (v2 > 0 && isPowerOf2(v2)) {
      final int log2 = log2(v2);
      final int d = log2 / 8;
      final int s = log2 % 8;
      // Copy everything right of d
      v1.slice(d + 1).copyTo(result, d + 1);
      // Mask the byte at d to only include the s right-most bits ...
      result.set(SIZE - 1 - d, (byte) (v1.get(SIZE - 1 - d) & maskByteRightBits(s)));
      // and clear anything left of that d.
      for (int i = d + 1; i < SIZE; i++) {
        result.set(SIZE - 1 - i, (byte) 0);
      }
    } else {
      doOnBigInteger(v1, of(v2), result, BigInteger::mod);
    }
  }

  public static void signExtend(final Bytes32 v1, final Bytes32 v2, final MutableBytes32 result) {
    // Any value >= 31 imply an index <= 0, so no work to do (note that 0 itself is a valid index,
    // but copying the 0th byte to itself is only so useful).
    if (compareUnsigned(v2, U_31.getBytes()) >= 0) {
      v1.copyTo(result);
      return;
    }

    // This is safe, since other < 31.
    final int byteIndex = SIZE - 1 - v2.getInt(SIZE - 4);
    final byte toSet = v1.get(byteIndex) < 0 ? ALL_ONE_BYTE : ALL_ZERO_BYTE;
    result.mutableSlice(0, byteIndex).fill(toSet);
    v1.slice(byteIndex).copyTo(result, byteIndex);
  }

  // Other operations

  public static Bytes32 of(final long v) {
    checkArgument(v >= 0, "Argument must be positive, got %s", v);
    final MutableBytes32 bytes = MutableBytes32.create();
    bytes.setLong(Bytes32.SIZE - 8, v);
    return bytes;
  }

  public static Bytes32 of(final BigInteger v) {
    checkArgument(v.signum() >= 0, "Argument must be positive, got %s", v);
    BytesValue toCopy = BytesValue.wrap(v.toByteArray());
    // The result of BigInteger.toByteArray() contains the minimum amount of bytes needed to
    // represent the number _plus_ "at least one sign bit". This mean in practice that if the
    // (positive) number fits _exactly_ 256 bits, then it will have an extra 0 bit in front, so
    // 257 bits which will be rounded up to 33 bytes.
    checkArgument(toCopy.size() <= Bytes32.SIZE + 1, "Argument too big (%s bytes)", toCopy.size());

    if (toCopy.size() == Bytes32.SIZE + 1) {
      // Extra byte is due to sign bit. As the number has been checked to be positive, this must
      // be 0 (or it mean the extra byte is truly due to the number being to big to represent with
      // 32 bytes).
      checkArgument(toCopy.get(0) == 0, "Argument too big (%s bytes)", toCopy.size());
      toCopy = toCopy.slice(1);
    }

    final MutableBytes32 bytes = MutableBytes32.create();
    toCopy.copyTo(bytes, bytes.size() - toCopy.size());
    return bytes;
  }

  static boolean fitsInt(final Bytes32 bytes) {
    // Ints are 4 bytes, so anything but the 4 last bytes must be zeroes
    for (int i = 0; i < SIZE - 4; i++) {
      if (bytes.get(i) != 0) return false;
    }
    // Lastly, the left-most byte of the int must not start with a 1.
    return bytes.get(SIZE - 4) >= 0;
  }

  static boolean fitsLong(final Bytes32 bytes) {
    // Longs are 8 bytes, so anything but the 8 last bytes must be zeroes
    for (int i = 0; i < SIZE - 8; i++) {
      if (bytes.get(i) != 0) return false;
    }
    // Lastly, the left-most byte of the long must not start with a 1.
    return bytes.get(SIZE - 8) >= 0;
  }

  static int bitLength(final Bytes32 bytes) {
    for (int i = 0; i < SIZE; i++) {
      final byte b = bytes.get(i);
      if (b == 0) continue;

      return (SIZE * 8) - (i * 8) - (Integer.numberOfLeadingZeros(b & 0xFF) - 3 * 8);
    }
    return 0;
  }

  static int compareUnsigned(final Bytes32 v1, final Bytes32 v2) {
    for (int i = 0; i < SIZE; i++) {
      final int cmp = Integer.compare((v1.get(i)) & 0xFF, (v2.get(i)) & 0xFF);
      if (cmp != 0) return cmp;
    }
    return 0;
  }

  static String toString(final Bytes32 v) {
    return BytesValues.asUnsignedBigInteger(v).toString();
  }
}

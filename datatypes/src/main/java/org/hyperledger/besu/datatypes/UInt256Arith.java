package org.hyperledger.besu.datatypes;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class UInt256Arith {

  private static final long LONG_MASK = 0xffffffffL;

  public static Bytes divide(final boolean unsigned, final Bytes numerator, final Bytes denominator) {
    if (denominator.isZero()) {
      throw new ArithmeticException("divide by zero");
    }

    final byte[] numArray = numerator.size() > 32 ? numerator.slice(numerator.size() - 32).toArrayUnsafe() : numerator.toArrayUnsafe();
    final int numeratorOffset = numerator.numberOfLeadingZeroBytes();
    final int numeratorSize = numerator.size() - numeratorOffset;

    final byte[] denomArray = denominator.size() > 32 ? denominator.slice(denominator.size() - 32).toArrayUnsafe() : denominator.toArrayUnsafe();
    final int denominatorOffset = denominator.numberOfLeadingZeroBytes();
    final int denominatorSize = denominator.size() - denominatorOffset;

    final int cmp = compare(
      numArray, numeratorOffset, numeratorSize, denomArray, denominatorOffset, denominatorSize);

    if (cmp < 0) {
      return UInt256.ZERO;
    }

    if (cmp == 0) {
      return UInt256.ONE;
    }

    final int[] intResult = divideKnuth(toIntLimbs(numArray, numeratorOffset), toIntLimbs(denomArray, denominatorOffset));

    return Bytes.wrap(fromIntLimbs(intResult));
  }

  private static int compare(final byte[] numArray, final int numeratorOffset, final int numeratorSize, final byte[] denomArray,
                             final int denominatorOffset, final int denominatorSize) {
    if (numeratorSize != denominatorSize) {
      return Integer.compare(numeratorSize, denominatorSize);
    }
    for (int i = numeratorOffset, j = denominatorOffset; i < numeratorSize + numeratorOffset; i++, j++) {
      int b1 = (int) numArray[i] + Integer.MIN_VALUE;
      int b2 = (int) denomArray[j] + Integer.MIN_VALUE;
      if (b1 != b2) {
        return Integer.compare(b1, b2);
      }
    }
    return 0;
  }

  private static int[] divideKnuth(final int[] num, final int[] denom) {
    if (denom.length == 1) {
      return divideOneWord(num, denom[0]);
    }

    // assert div.intLen > 1
    // D1 normalize the divisor
    int shift = Integer.numberOfLeadingZeros(denom[0]);
    // Copy divisor value to protect divisor
    final int dlen = denom.length;
    int[] rem; // Remainder starts as dividend with space for a leading zero
    int[] divisor;
    if (shift > 0) {
      divisor = new int[denom.length];
      primitiveLeftShift(denom, shift, divisor, 0);
      if (Integer.numberOfLeadingZeros(num[0]) >= shift) {
        rem = new int[num.length + 1];
        primitiveLeftShift(num, shift, rem, 1);
      } else {
        rem = new int[num.length + 2];
        int rFrom = 0;
        int c=0;
        int n2 = 32 - shift;
        for (int i=1; i < num.length+1; i++,rFrom++) {
          int b = c;
          c = num[rFrom];
          rem[i] = (b << shift) | (c >>> n2);
        }
        rem[num.length+1] = c << shift;
      }
    } else {
      divisor = Arrays.copyOfRange(denom, 0, denom.length);
      rem = new int[num.length + 1];
      System.arraycopy(num, 0, rem, 1, num.length);
    }

    int nlen = rem.length - 1;

    // Set the quotient size
    final int limit = nlen - dlen + 1;
    int[] quotient = new int[limit];
    int[] q = quotient;

    // Insert leading 0 in rem
    rem[0] = 0;

    int dh = divisor[0];
    long dhLong = dh & LONG_MASK;
    int dl = divisor[1];

    // D2 Initialize j
    for (int j=0; j < limit-1; j++) {
      // D3 Calculate qhat
      // estimate qhat
      int qhat = 0;
      int qrem = 0;
      boolean skipCorrection = false;
      int nh = rem[j];
      int nh2 = nh + 0x80000000;
      int nm = rem[j+1];

      if (nh == dh) {
        qhat = ~0;
        qrem = nh + nm;
        skipCorrection = qrem + 0x80000000 < nh2;
      } else {
        long nChunk = (((long)nh) << 32) | (nm & LONG_MASK);
        qhat = (int) Long.divideUnsigned(nChunk, dhLong);
        qrem = (int) Long.remainderUnsigned(nChunk, dhLong);
      }

      if (qhat == 0)
        continue;

      if (!skipCorrection) { // Correct qhat
        long nl = rem[j+2] & LONG_MASK;
        long rs = ((qrem & LONG_MASK) << 32) | nl;
        long estProduct = (dl & LONG_MASK) * (qhat & LONG_MASK);

        if (unsignedLongCompare(estProduct, rs)) {
          qhat--;
          qrem = (int)((qrem & LONG_MASK) + dhLong);
          if ((qrem & LONG_MASK) >=  dhLong) {
            estProduct -= (dl & LONG_MASK);
            rs = ((qrem & LONG_MASK) << 32) | nl;
            if (unsignedLongCompare(estProduct, rs))
              qhat--;
          }
        }
      }

      // D4 Multiply and subtract
      rem[j] = 0;
      int borrow = mulsub(rem, divisor, qhat, dlen, j);

      // D5 Test remainder
      if (borrow + 0x80000000 > nh2) {
        // D6 Add back
        divadd(divisor, rem, j+1);
        qhat--;
      }

      // Store the quotient digit
      q[j] = qhat;
    } // D7 loop on j
    // D3 Calculate qhat
    // estimate qhat
    int qhat = 0;
    int qrem = 0;
    boolean skipCorrection = false;
    int nh = rem[limit - 1];
    int nh2 = nh + 0x80000000;
    int nm = rem[limit];

    if (nh == dh) {
      qhat = ~0;
      qrem = nh + nm;
      skipCorrection = qrem + 0x80000000 < nh2;
    } else {
      long nChunk = (((long) nh) << 32) | (nm & LONG_MASK);
      qhat = (int) Long.divideUnsigned(nChunk, dhLong);
      qrem = (int) Long.remainderUnsigned(nChunk, dhLong);
    }
    if (qhat != 0) {
      if (!skipCorrection) { // Correct qhat
        long nl = rem[limit + 1] & LONG_MASK;
        long rs = ((qrem & LONG_MASK) << 32) | nl;
        long estProduct = (dl & LONG_MASK) * (qhat & LONG_MASK);

        if (unsignedLongCompare(estProduct, rs)) {
          qhat--;
          qrem = (int) ((qrem & LONG_MASK) + dhLong);
          if ((qrem & LONG_MASK) >= dhLong) {
            estProduct -= (dl & LONG_MASK);
            rs = ((qrem & LONG_MASK) << 32) | nl;
            if (unsignedLongCompare(estProduct, rs))
              qhat--;
          }
        }
      }

      // D4 Multiply and subtract
      int borrow;
      rem[limit - 1] = 0;
      borrow = mulsubBorrow(rem, divisor, qhat, dlen, limit - 1);

      // D5 Test remainder
      if (borrow + 0x80000000 > nh2) {
        qhat--;
      }

      // Store the quotient digit
      q[(limit - 1)] = qhat;
    }

    return normalize(quotient);
  }

 private static int[] divideOneWord(final int[] numerator, final int denominator) {
   long divisorLong = denominator & LONG_MASK;

   // Special case of one word dividend
   if (numerator.length == 1) {
     return new int[] {Integer.divideUnsigned(numerator[0], denominator)};
   }

   int[] quotient = new int[numerator.length];

   long rem = 0;
   for (int xlen = numerator.length; xlen > 0; xlen--) {
     long dividendEstimate = (rem << 32) |
       (numerator[numerator.length - xlen] & LONG_MASK);
     int q = (int) Long.divideUnsigned(dividendEstimate, divisorLong);
     rem = Long.remainderUnsigned(dividendEstimate, divisorLong);
     quotient[numerator.length - xlen] = q;
   }

   return normalize(quotient);
  }

  // removes leading zeros from value array
  private static int[] normalize(final int[] value) {
    if (value.length == 0) {
      return value;
    }

    if (value[0] != 0) {
      return value;
    }

    int numZeros = 0;
    do {
      numZeros++;
    } while(numZeros < value.length && value[numZeros] == 0);

    final int[] trimmedValue = new int[value.length - numZeros];
    System.arraycopy(value, numZeros, trimmedValue, 0, value.length - numZeros);
    return trimmedValue;
  }

  private static byte[] fromIntLimbs(final int[] value) {
    final byte[] valueBytes = new byte[32];
    for (int i = valueBytes.length - 1, limbIndex = value.length - 1; i >= 0 && limbIndex >= 0; i -= 4, limbIndex--) {
      valueBytes[i - 3] = (byte) (value[limbIndex] >>> 24);
      valueBytes[i - 2] = (byte) (value[limbIndex] >>> 16);
      valueBytes[i - 1] = (byte) (value[limbIndex] >>> 8);
      valueBytes[i] = (byte) value[limbIndex];
    }
    return valueBytes;
  }

  private static int[] toIntLimbs(final byte[] value, final int offset) {
    if (offset < 0 || offset > value.length) {
      throw new IllegalArgumentException("offset out of range: [0, " + value.length + "], offset=" + offset);
    }
    if (value.length == offset) {
      return new int[1];
    }
    int limbsSize = (value.length - offset) / 4;
    limbsSize += (((value.length - offset) % 4) != 0) ? 1 : 0;
    int[] limbs = new int[limbsSize];

    int i = value.length - 1;
    for (int limbIndex = limbsSize - 1; limbIndex > 0; i -= 4, limbIndex--) {
      limbs[limbIndex] =
        (Byte.toUnsignedInt(value[i - 3]) << 24)
        | (Byte.toUnsignedInt(value[i - 2]) << 16)
        | (Byte.toUnsignedInt(value[i - 1]) << 8)
        | (Byte.toUnsignedInt(value[i]));
    }

    limbs[0] = (((i - 3 >= offset) ? Byte.toUnsignedInt(value[i - 3]) : 0) << 24)
      | (((i - 2 >= offset) ? Byte.toUnsignedInt(value[i - 2]) : 0) << 16)
      | (((i - 1 >= offset) ? Byte.toUnsignedInt(value[i - 1]) : 0) << 8)
      | ((i >= offset) ? Byte.toUnsignedInt(value[i]) : 0);

    return limbs;
  }

  private static void primitiveLeftShift(final int[] value, final int n, final int[] result, final int resFrom) {
    int n2 = 32 - n;
    final int m = value.length - 1;
    int b = value[0];
    for (int i = 0; i < m; i++) {
      int c = value[i + 1];
      result[resFrom + i] = (b << n) | (c >>> n2);
      b = c;
    }
    result[resFrom + m] = b << n;
  }

  private static boolean unsignedLongCompare(final long one, final long two) {
    return (one+Long.MIN_VALUE) > (two+Long.MIN_VALUE);
  }

  private static int mulsub(final int[] q, final int[] a, final int x, final int len, final int offSet) {
    int offset = offSet;
    long xLong = x & LONG_MASK;
    long carry = 0;
    offset += len;

    for (int j=len-1; j >= 0; j--) {
      long product = (a[j] & LONG_MASK) * xLong + carry;
      long difference = q[offset] - product;
      q[offset--] = (int)difference;
      carry = (product >>> 32)
        + (((difference & LONG_MASK) >
        (((~(int)product) & LONG_MASK))) ? 1:0);
    }
    return (int)carry;
  }

  private static int divadd(final int[] a, final int[] result, final int offset) {
    long carry = 0;

    for (int j=a.length-1; j >= 0; j--) {
      long sum = (a[j] & LONG_MASK) +
        (result[j+offset] & LONG_MASK) + carry;
      result[j+offset] = (int)sum;
      carry = sum >>> 32;
    }
    return (int)carry;
  }

  private static int mulsubBorrow(final int[] q, final int[] a, final int x, final int len, final int offSet) {
    int offset = offSet;
    long xLong = x & LONG_MASK;
    long carry = 0;
    offset += len;
    for (int j=len-1; j >= 0; j--) {
      long product = (a[j] & LONG_MASK) * xLong + carry;
      long difference = q[offset--] - product;
      carry = (product >>> 32)
        + (((difference & LONG_MASK) >
        (((~(int)product) & LONG_MASK))) ? 1:0);
    }
    return (int)carry;
  }

}

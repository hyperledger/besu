/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.word256;

/**
 * * Utility class for performing arithmetic operations on {@link Word256} values.
 *
 * <p>This class provides methods for addition, subtraction, multiplication, division, and modular
 * arithmetic on 256-bit words represented by four 64-bit long values.
 */
final class Word256Arithmetic {

  private Word256Arithmetic() {}

  /**
   * Adds two Word256 values.
   *
   * @param a the first Word256 value
   * @param b the second Word256 value
   * @return the sum of a and b as a new Word256
   */
  static Word256 add(final Word256 a, final Word256 b) {
    long r0 = a.l0 + b.l0;
    long carry = Long.compareUnsigned(r0, a.l0) < 0 ? 1 : 0;

    long r1 = a.l1 + b.l1 + carry;
    carry = Long.compareUnsigned(r1, a.l1) < 0 || (carry == 1 && r1 == a.l1) ? 1 : 0;

    long r2 = a.l2 + b.l2 + carry;
    carry = Long.compareUnsigned(r2, a.l2) < 0 || (carry == 1 && r2 == a.l2) ? 1 : 0;

    long r3 = a.l3 + b.l3 + carry;

    return new Word256(r0, r1, r2, r3);
  }

  /**
   * Subtracts one Word256 value from another.
   *
   * @param a the Word256 value to subtract from
   * @param b the Word256 value to subtract
   * @return the result of a - b as a new Word256
   */
  static Word256 sub(final Word256 a, final Word256 b) {
    long[] r;
    long r0, r1, r2, r3;
    long borrow;

    r = Word256Helpers.subtract64(a.l0, b.l0, 0);
    r0 = r[0];
    borrow = r[1];

    r = Word256Helpers.subtract64(a.l1, b.l1, borrow);
    r1 = r[0];
    borrow = r[1];

    r = Word256Helpers.subtract64(a.l2, b.l2, borrow);
    r2 = r[0];
    borrow = r[1];

    r = Word256Helpers.subtract64(a.l3, b.l3, borrow);
    r3 = r[0];

    return new Word256(r0, r1, r2, r3);
  }

  /**
   * Multiplies two Word256 values.
   *
   * @param a the first Word256 value
   * @param b the second Word256 value
   * @return the product of a and b as a new Word256
   */
  static Word256 mul(final Word256 a, final Word256 b) {
    // Extract the limbs (64-bit words), least significant first (l0 = low, l3 = high)
    final long x0 = a.l0, x1 = a.l1, x2 = a.l2, x3 = a.l3;
    final long y0 = b.l0, y1 = b.l1, y2 = b.l2, y3 = b.l3;

    // Result will contain the low 256 bits only
    final long[] result = new long[4];

    long carry0, r0, res1, res2;
    long carry1, carry2;

    // Step 1: Multiply x0 * y0 (least significant words)
    //         This gives the lowest 128 bits of the full result.
    //         Store lower 64 bits in result[0], keep upper 64 bits as carry0
    final long[] mul0 = Word256Helpers.multiplyHighLowUnsigned(x0, y0);
    carry0 = mul0[0];
    r0 = mul0[1];
    result[0] = r0;

    // Step 2: Add partial products x1*y0 and x2*y0 with carry propagation
    //         This will help us construct result[1] and intermediate values
    final long[] hop1 = Word256Helpers.unsignedMultiplyAdd(carry0, x1, y0);
    carry0 = hop1[0];
    res1 = hop1[1];

    // Step 3: Add x2 * y0 to res1, this gives us the next part of the result
    final long[] hop2 = Word256Helpers.unsignedMultiplyAdd(carry0, x2, y0);
    carry0 = hop2[0];
    res2 = hop2[1];

    // Step 4: Compute x0 * y1 and add it to res1
    //         This gives result[1] and updates carry1 for next step
    final long[] hop3 = Word256Helpers.unsignedMultiplyAdd(res1, x0, y1);
    carry1 = hop3[0];
    result[1] = hop3[1];

    // Step 5: Compute (x1 * y1) + res2 + carry1
    //         Continue accumulation into next limb
    final long[] step1 = Word256Helpers.unsignedMultiplyAddWithCarry(res2, x1, y1, carry1);
    carry1 = step1[0];
    res2 = step1[1];

    // Step 6: Compute x0 * y2 + res2
    //         Final step before computing result[3]
    final long[] hop4 = Word256Helpers.unsignedMultiplyAdd(res2, x0, y2);
    carry2 = hop4[0];
    result[2] = hop4[1];

    // Step 6: Accumulate final terms for result[3]
    // Includes:
    //   x3 * y0
    //   x2 * y1
    //   x1 * y2
    //   x0 * y3
    //   all carry values from previous steps
    final long z3 =
        Word256Helpers.unsignedMulAdd3(x3, y0, x2, y1, x0, y3)
            + Word256Helpers.unsignedMultiplyAndAdd(x1, y2, carry0, carry1, carry2);
    result[3] = z3;

    return new Word256(result[0], result[1], result[2], result[3]);
  }

  /**
   * Exponentiates a base Word256 value to the power of an exponent Word256 value.
   *
   * @param base the base Word256 value
   * @param exponent the exponent Word256 value
   * @return the result of base^exponent as a new Word256
   */
  static Word256 exp(final Word256 base, final Word256 exponent) {
    // Fast path: any number raised to the power of 0 is 1
    if (exponent.isZero()) {
      return Word256Constants.ONE;
    }

    // Fast path: 0 raised to any power (except 0) is 0
    if (base.isZero()) {
      return Word256Constants.ZERO;
    }

    // Find the index of the most significant bit set in the exponent
    final int highestBit = exponent.bitLength() - 1;

    // Initialize result = 1 (multiplicative identity)
    Word256 result = Word256Constants.ONE;

    // Start with power = base
    Word256 power = base;

    // Perform binary exponentiation (a.k.a. "square-and-multiply")
    // Loop through all bits of the exponent, from least to most significant
    for (int i = 0; i <= highestBit; i++) {
      // If bit i in the exponent is set, multiply result by current power
      if (exponent.getBit(i) == 1) {
        result = mul(result, power);
      }

      // Square the base (for the next bit), unless we’re at the last bit
      if (i != highestBit) {
        power = mul(power, power);
      }
    }

    return result;
  }
}

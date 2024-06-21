/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.verkletrie.bandersnatch.fr;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class Element {
  public static final Element ZERO = new Element(UInt256.ZERO);
  public static final Element ONE;
  static final Element Q_MODULUS;
  private static final Element R_SQUARE;

  static {
    {
      UInt256 z0 = UInt256.valueOf(new BigInteger("6347764673676886264", 10));
      UInt256 z1 = UInt256.valueOf(new BigInteger("253265890806062196", 10)).shiftLeft(64);
      UInt256 z2 = UInt256.valueOf(new BigInteger("11064306276430008312", 10)).shiftLeft(128);
      UInt256 z3 = UInt256.valueOf(new BigInteger("1739710354780652911", 10)).shiftLeft(192);
      ONE = new Element(z0.add(z1).add(z2).add(z3));
    }
    {
      UInt256 z0 = UInt256.valueOf(new BigInteger("8429901452645165025", 10));
      UInt256 z1 = UInt256.valueOf(new BigInteger("18415085837358793841", 10)).shiftLeft(64);
      UInt256 z2 = UInt256.valueOf(new BigInteger("922804724659942912", 10)).shiftLeft(128);
      UInt256 z3 = UInt256.valueOf(new BigInteger("2088379214866112338", 10)).shiftLeft(192);
      Q_MODULUS = new Element(z0.add(z1).add(z2).add(z3));
    }
    {
      UInt256 z0 = UInt256.valueOf(new BigInteger("15831548891076708299", 10));
      UInt256 z1 = UInt256.valueOf(new BigInteger("4682191799977818424", 10)).shiftLeft(64);
      UInt256 z2 = UInt256.valueOf(new BigInteger("12294384630081346794", 10)).shiftLeft(128);
      UInt256 z3 = UInt256.valueOf(new BigInteger("785759240370973821", 10)).shiftLeft(192);
      R_SQUARE = new Element(z0.add(z1).add(z2).add(z3));
    }
  }

  public static Element random() {
    UInt256 value = UInt256.fromBytes(Bytes32.random());
    UInt256 divisor = UInt256.fromBytes(Bytes32.rightPad(Q_MODULUS.value.slice(8)));
    value = value.mod(divisor);

    if (value.greaterThan(Q_MODULUS.value)) {
      value = value.subtract(Q_MODULUS.value);
    }
    return new Element(value);
  }

  final UInt256 value;

  public Element(final UInt256 value) {
    this.value = value;
  }

  public static Element fromBytes(final Bytes data, final ByteOrder byteOrder) {
    return new Element(
        UInt256.fromBytes(byteOrder == ByteOrder.BIG_ENDIAN ? data : data.reverse()));
  }

  public boolean biggerModulus() {
    return value.greaterOrEqualThan(Q_MODULUS.value);
  }

  public Element inverse() {
    if (isZero()) {
      return new Element(UInt256.ZERO);
    }
    UInt256 u = Q_MODULUS.value;
    UInt256 s = R_SQUARE.value;
    UInt256 v = value;
    UInt256 r = UInt256.ZERO;
    while (true) {
      while ((v.getLong(24) & 1L) == 0) {
        v = v.shiftRight(1);
        if ((s.getLong(24) & 1L) == 1) {
          s = s.add(Q_MODULUS.value);
        }
        s = s.shiftRight(1);
      }
      while ((u.getLong(24) & 1L) == 0) {
        u = u.shiftRight(1);
        if ((r.getLong(24) & 1L) == 1) {
          r = r.add(Q_MODULUS.value);
        }
        r = r.shiftRight(1);
      }
      boolean bigger = v.greaterOrEqualThan(u);
      if (bigger) {
        v = v.subtract(u);
        UInt256 oldS = s;
        s = s.subtract(r);
        if (s.greaterThan(oldS)) {
          s = s.add(Q_MODULUS.value);
        }

      } else {
        u = u.subtract(v);
        UInt256 oldR = r;
        r = r.subtract(s);
        if (r.greaterThan(oldR)) {
          r = r.add(Q_MODULUS.value);
        }
      }
      if (u.getLong(24) == 1L && u.shiftRight(8).equals(UInt256.ZERO)) {
        return new Element(r);
      }
      if (v.getLong(24) == 1L && v.shiftRight(8).equals(UInt256.ZERO)) {
        return new Element(s);
      }
    }
  }

  public Element neg() {
    if (isZero()) {
      return this;
    }
    return new Element(Q_MODULUS.value.subtract(this.value));
  }

  public byte[] limb(final int i) {
    return value.slice(32 - (i + 1) * 8, 8).toArrayUnsafe();
  }

  public boolean isZero() {
    return value.isZero();
  }

  public Element divide(final Element b) {
    Element bInv = b.inverse();
    return this.multiply(bInv);
  }

  private UInt256 madd0(final UInt256 a, final UInt256 b, final UInt256 c) {
    UInt256 product = a.multiply(b).add(c);
    return product;
  }

  private UInt256 madd1(final UInt256 a, final UInt256 b, final UInt256 c) {
    UInt256 product = a.multiply(b).add(c);
    return product;
  }

  private UInt256 madd2(final UInt256 a, final UInt256 b, final UInt256 c, final UInt256 d) {
    UInt256 product = a.multiply(b).add(c).add(d);
    return product;
  }

  private UInt256 madd3(
      final UInt256 a, final UInt256 b, final UInt256 c, final UInt256 d, final UInt256 e) {
    UInt256 product = a.multiply(b);
    product = product.add(c).add(d);
    product = product.add(e.shiftLeft(64));
    return product;
  }

  private UInt256 limb(final UInt256 value, final int index) {
    return UInt256.fromBytes(Bytes32.leftPad(value.slice(32 - (index + 1) * 8, 8)));
  }

  private UInt256 setLimb(final UInt256 value, final UInt256 limb, final int index) {
    MutableBytes32 mutable = value.toBytes().mutableCopy();
    mutable.set(32 - (index + 1) * 8, limb.slice(24, 8));
    return UInt256.fromBytes(mutable);
  }

  public Element multiply(final Element y) {

    UInt256 t = UInt256.ZERO;
    UInt256 c;

    // round 0
    {
      // v := x[0]
      UInt256 v = limb(this.value, 0);
      // c[1], c[0] = bits.Mul64(v, y[0])
      UInt256 tempC = v.multiply(limb(y.value, 0));
      c = setLimb(UInt256.ZERO, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      // m := c[0] * 17410672245482742751
      UInt256 constant = UInt256.valueOf(new BigInteger("17410672245482742751", 10));
      UInt256 c0 = limb(c, 0);
      UInt256 m = limb(constant.multiply(c0), 0);

      // c[2] = madd0(m, 8429901452645165025, c[0])
      UInt256 c2 = madd0(m, UInt256.valueOf(new BigInteger("8429901452645165025", 10)), limb(c, 0));
      c = setLimb(c, limb(c2, 1), 2);
      // c[1], c[0] = madd1(v, y[1], c[1])
      tempC = madd1(v, limb(y.value, 1), limb(c, 1));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      // c[2], t[0] = madd2(m, 18415085837358793841, c[2], c[0])
      tempC =
          madd2(
              m,
              UInt256.valueOf(new BigInteger("18415085837358793841", 10)),
              limb(c, 2),
              limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 0);
      // c[1], c[0] = madd1(v, y[2], c[1])
      tempC = madd1(v, limb(y.value, 2), limb(c, 1));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      // c[2], t[1] = madd2(m, 922804724659942912, c[2], c[0])
      tempC =
          madd2(
              m, UInt256.valueOf(new BigInteger("922804724659942912", 10)), limb(c, 2), limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 1);
      // c[1], c[0] = madd1(v, y[3], c[1])
      tempC = madd1(v, limb(y.value, 3), limb(c, 1));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      // t[3], t[2] = madd3(m, 2088379214866112338, c[0], c[2], c[1])
      tempC =
          madd3(
              m,
              UInt256.valueOf(new BigInteger("2088379214866112338", 10)),
              limb(c, 0),
              limb(c, 2),
              limb(c, 1));
      t = setLimb(t, limb(tempC, 1), 3);
      t = setLimb(t, limb(tempC, 0), 2);
    }
    // round 1
    {
      UInt256 v = limb(this.value, 1);
      // c[1], c[0] = madd1(v, y[0], t[0])
      UInt256 tempC = madd1(v, limb(y.value, 0), limb(t, 0));
      c = setLimb(UInt256.ZERO, limb(tempC, 0), 0);
      c = setLimb(c, limb(tempC, 1), 1);
      // m := c[0] * 17410672245482742751
      UInt256 m =
          setLimb(
              UInt256.ZERO,
              limb(
                  UInt256.valueOf(new BigInteger("17410672245482742751", 10)).multiply(limb(c, 0)),
                  0),
              0);
      //		c[2] = madd0(m, 8429901452645165025, c[0])
      UInt256 c2 = madd0(m, UInt256.valueOf(new BigInteger("8429901452645165025", 10)), limb(c, 0));
      c = setLimb(c, limb(c2, 1), 2);
      //		c[1], c[0] = madd2(v, y[1], c[1], t[1])
      tempC = madd2(v, limb(y.value, 1), limb(c, 1), limb(t, 1));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		c[2], t[0] = madd2(m, 18415085837358793841, c[2], c[0])
      tempC =
          madd2(
              m,
              UInt256.valueOf(new BigInteger("18415085837358793841", 10)),
              limb(c, 2),
              limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 0);
      //		c[1], c[0] = madd2(v, y[2], c[1], t[2])
      tempC = madd2(v, limb(y.value, 2), limb(c, 1), limb(t, 2));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		c[2], t[1] = madd2(m, 922804724659942912, c[2], c[0])
      tempC =
          madd2(
              m, UInt256.valueOf(new BigInteger("922804724659942912", 10)), limb(c, 2), limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 1);
      //		c[1], c[0] = madd2(v, y[3], c[1], t[3])
      tempC = madd2(v, limb(y.value, 3), limb(c, 1), limb(t, 3));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);

      //		t[3], t[2] = madd3(m, 2088379214866112338, c[0], c[2], c[1])
      tempC =
          madd3(
              m,
              UInt256.valueOf(new BigInteger("2088379214866112338", 10)),
              limb(c, 0),
              limb(c, 2),
              limb(c, 1));
      t = setLimb(t, limb(tempC, 1), 3);
      t = setLimb(t, limb(tempC, 0), 2);
    }
    // round 2
    {
      // v := x[2]
      UInt256 v = limb(this.value, 2);
      //		c[1], c[0] = madd1(v, y[0], t[0])
      UInt256 tempC = madd1(v, limb(y.value, 0), limb(t, 0));
      c = setLimb(UInt256.ZERO, limb(tempC, 0), 0);
      c = setLimb(c, limb(tempC, 1), 1);
      //		m := c[0] * 17410672245482742751
      UInt256 m =
          setLimb(
              UInt256.ZERO,
              limb(c, 0).multiply(UInt256.valueOf(new BigInteger("17410672245482742751", 10))),
              0);
      //		c[2] = madd0(m, 8429901452645165025, c[0])
      UInt256 c2 = madd0(m, UInt256.valueOf(new BigInteger("8429901452645165025", 10)), limb(c, 0));
      c = setLimb(c, limb(c2, 1), 2);
      //		c[1], c[0] = madd2(v, y[1], c[1], t[1])
      tempC = madd2(v, limb(y.value, 1), limb(c, 1), limb(t, 1));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		c[2], t[0] = madd2(m, 18415085837358793841, c[2], c[0])
      tempC =
          madd2(
              m,
              UInt256.valueOf(new BigInteger("18415085837358793841", 10)),
              limb(c, 2),
              limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 0);
      //		c[1], c[0] = madd2(v, y[2], c[1], t[2])
      tempC = madd2(v, limb(y.value, 2), limb(c, 1), limb(t, 2));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		c[2], t[1] = madd2(m, 922804724659942912, c[2], c[0])
      tempC =
          madd2(
              m, UInt256.valueOf(new BigInteger("922804724659942912", 10)), limb(c, 2), limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 1);
      //		c[1], c[0] = madd2(v, y[3], c[1], t[3])
      tempC = madd2(v, limb(y.value, 3), limb(c, 1), limb(t, 3));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		t[3], t[2] = madd3(m, 2088379214866112338, c[0], c[2], c[1])
      tempC =
          madd3(
              m,
              UInt256.valueOf(new BigInteger("2088379214866112338", 10)),
              limb(c, 0),
              limb(c, 2),
              limb(c, 1));
      t = setLimb(t, limb(tempC, 1), 3);
      t = setLimb(t, limb(tempC, 0), 2);
    }
    // round 3
    {
      // v := x[3]
      UInt256 v = limb(this.value, 3);
      //		c[1], c[0] = madd1(v, y[0], t[0])
      UInt256 tempC = madd1(v, limb(y.value, 0), limb(t, 0));
      c = setLimb(UInt256.ZERO, limb(tempC, 0), 0);
      c = setLimb(c, limb(tempC, 1), 1);
      //		m := c[0] * 17410672245482742751
      UInt256 m =
          setLimb(
              UInt256.ZERO,
              limb(c, 0).multiply(UInt256.valueOf(new BigInteger("17410672245482742751", 10))),
              0);
      //		c[2] = madd0(m, 8429901452645165025, c[0])
      UInt256 c2 = madd0(m, UInt256.valueOf(new BigInteger("8429901452645165025", 10)), limb(c, 0));
      c = setLimb(c, limb(c2, 1), 2);
      //		c[1], c[0] = madd2(v, y[1], c[1], t[1])
      tempC = madd2(v, limb(y.value, 1), limb(c, 1), limb(t, 1));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		c[2], z[0] = madd2(m, 18415085837358793841, c[2], c[0])
      tempC =
          madd2(
              m,
              UInt256.valueOf(new BigInteger("18415085837358793841", 10)),
              limb(c, 2),
              limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 0);
      //		c[1], c[0] = madd2(v, y[2], c[1], t[2])
      tempC = madd2(v, limb(y.value, 2), limb(c, 1), limb(t, 2));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		c[2], z[1] = madd2(m, 922804724659942912, c[2], c[0])
      tempC =
          madd2(
              m, UInt256.valueOf(new BigInteger("922804724659942912", 10)), limb(c, 2), limb(c, 0));
      c = setLimb(c, limb(tempC, 1), 2);
      t = setLimb(t, limb(tempC, 0), 1);
      //		c[1], c[0] = madd2(v, y[3], c[1], t[3])
      tempC = madd2(v, limb(y.value, 3), limb(c, 1), limb(t, 3));
      c = setLimb(c, limb(tempC, 1), 1);
      c = setLimb(c, limb(tempC, 0), 0);
      //		z[3], z[2] = madd3(m, 2088379214866112338, c[0], c[2], c[1])
      tempC =
          madd3(
              m,
              UInt256.valueOf(new BigInteger("2088379214866112338", 10)),
              limb(c, 0),
              limb(c, 2),
              limb(c, 1));
      t = setLimb(t, limb(tempC, 1), 3);
      t = setLimb(t, limb(tempC, 0), 2);
    }

    if (t.greaterThan(Q_MODULUS.value)) {
      t = t.subtract(Q_MODULUS.value);
    }

    return new Element(t);
  }

  public boolean lexicographicallyLargest() {
    return value.greaterThan((Q_MODULUS.value.subtract(1)).divide(2));
  }

  public Bytes32 getValue(final ByteOrder byteOrder) {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      return this.value;
    } else {
      return (Bytes32) this.value.reverse();
    }
  }

  public Bytes32 getBytes(final ByteOrder byteOrder) {
    Element toRegular = fromMontgomery();
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
      return toRegular.value;
    } else {
      return (Bytes32) toRegular.value.reverse();
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Element element = (Element) o;
    return Objects.equals(value, element.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "Element{" + "value=" + value + '}';
  }

  private UInt256 add(final UInt256 z) {
    UInt256 mutableZ = z;
    // m = z[0]n'[0] mod W
    // m := z[0] * 17410672245482742751
    UInt256 z0 = limb(mutableZ, 0);
    UInt256 m =
        setLimb(
            UInt256.ZERO,
            UInt256.valueOf(new BigInteger("17410672245482742751", 10)).multiply(z0),
            0);
    // C := madd0(m, 8429901452645165025, z[0])
    UInt256 tempC = madd0(m, limb(Q_MODULUS.value, 0), limb(mutableZ, 0));
    UInt256 c = setLimb(UInt256.ZERO, limb(tempC, 1), 0);
    // C, z[0] = madd2(m, 18415085837358793841, z[1], C)
    tempC = madd2(m, limb(Q_MODULUS.value, 1), limb(mutableZ, 1), c);
    c = setLimb(c, limb(tempC, 1), 0);
    mutableZ = setLimb(mutableZ, limb(tempC, 0), 0);
    // C, z[1] = madd2(m, 922804724659942912, z[2], C)
    tempC = madd2(m, limb(Q_MODULUS.value, 2), limb(mutableZ, 2), c);
    c = setLimb(c, limb(tempC, 1), 0);
    mutableZ = setLimb(mutableZ, limb(tempC, 0), 1);
    // C, z[2] = madd2(m, 2088379214866112338, z[3], C)
    tempC = madd2(m, limb(Q_MODULUS.value, 3), limb(mutableZ, 3), c);
    c = setLimb(c, limb(tempC, 1), 0);
    mutableZ = setLimb(mutableZ, limb(tempC, 0), 2);
    // z[3] = C
    mutableZ = setLimb(mutableZ, limb(c, 0), 3);
    return mutableZ;
  }

  /**
   * fromMontgomery converts the element from Montgomery to regular representation sets and returns
   * z = z * 1
   *
   * @return z * 1
   */
  public Element fromMontgomery() {
    UInt256 calc = add(this.value);
    calc = add(calc);
    calc = add(calc);
    calc = add(calc);

    if (calc.greaterThan(Q_MODULUS.value)) {
      return new Element(calc.subtract(Q_MODULUS.value));
    }
    return new Element(calc);
  }

  public Element toMontgomery() {
    return multiply(R_SQUARE);
  }
}

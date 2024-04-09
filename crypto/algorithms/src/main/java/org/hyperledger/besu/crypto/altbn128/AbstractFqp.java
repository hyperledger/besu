/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.crypto.altbn128;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 *
 * @param <T> the type parameter
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractFqp<T extends AbstractFqp> implements FieldElement<T> {
  private static final BigInteger BIGINT_2 = BigInteger.valueOf(2);

  /** The Degree. */
  protected final int degree;

  /** The Modulus coefficients. */
  protected final Fq[] modulusCoefficients;

  /** The Coefficients. */
  protected final Fq[] coefficients;

  /**
   * Instantiates a new Abstract fqp.
   *
   * @param degree the degree
   * @param modulusCoefficients the modulus coefficients
   * @param coefficients the coefficients
   */
  protected AbstractFqp(final int degree, final Fq[] modulusCoefficients, final Fq[] coefficients) {
    if (degree != coefficients.length) {
      throw new IllegalArgumentException(
          String.format("point is %d degree but got %d coefficients", degree, coefficients.length));
    }
    if (degree != modulusCoefficients.length) {
      throw new IllegalArgumentException(
          String.format(
              "point is %d degree but got %d modulus coefficients", degree, coefficients.length));
    }
    this.degree = degree;
    this.modulusCoefficients = modulusCoefficients;
    this.coefficients = coefficients;
  }

  /**
   * New instance t.
   *
   * @param coefficients the coefficients
   * @return the t
   */
  protected abstract T newInstance(final Fq[] coefficients);

  /**
   * Get coefficients fq [ ].
   *
   * @return the fq [ ]
   */
  public Fq[] getCoefficients() {
    return coefficients;
  }

  @Override
  public boolean isValid() {
    for (final Fq fq : coefficients) {
      if (!fq.isValid()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isZero() {
    for (final Fq fq : coefficients) {
      if (!fq.isZero()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public T add(final T other) {
    final Fq[] result = new Fq[coefficients.length];

    for (int i = 0; i < coefficients.length; ++i) {
      result[i] = coefficients[i].add(other.coefficients[i]);
    }

    return newInstance(result);
  }

  @Override
  public T subtract(final T other) {
    final Fq[] result = new Fq[coefficients.length];

    for (int i = 0; i < coefficients.length; ++i) {
      result[i] = coefficients[i].subtract(other.coefficients[i]);
    }

    return newInstance(result);
  }

  @Override
  public T multiply(final int n) {
    final Fq[] result = new Fq[degree];
    for (int i = 0; i < degree; ++i) {
      result[i] = coefficients[i].multiply(n);
    }
    return newInstance(result);
  }

  @Override
  public T multiply(final T other) {
    final Fq[] b = new Fq[degree * 2 - 1];
    Arrays.fill(b, Fq.zero());
    for (int i = 0; i < degree; ++i) {
      for (int j = 0; j < degree; ++j) {
        b[i + j] = b[i + j].add(coefficients[i].multiply(other.coefficients[j]));
      }
    }

    for (int i = b.length; i > degree; --i) {
      final Fq top = b[i - 1];
      final int exp = i - degree - 1;
      for (int j = 0; j < degree; ++j) {
        b[exp + j] = b[exp + j].subtract(top.multiply(modulusCoefficients[j]));
      }
    }

    return newInstance(Arrays.copyOfRange(b, 0, degree));
  }

  @Override
  public T divide(final T other) {
    final T inverse = newInstance(other.inverse());
    return multiply(inverse);
  }

  @Override
  public T negate() {
    final Fq[] negated = Arrays.stream(coefficients).map(Fq::negate).toArray(Fq[]::new);
    return newInstance(negated);
  }

  private T one() {
    final Fq[] result = new Fq[degree];
    Arrays.fill(result, Fq.zero());
    result[0] = Fq.one();
    return newInstance(result);
  }

  @SuppressWarnings("unchecked")
  @Override
  public T power(final int n) {
    if (n == 0) {
      return one();
    } else if (n == 1) {
      return newInstance(coefficients);
    } else if (n % 2 == 0) {
      return (T) multiply((T) this).power(n / 2);
    } else {
      return (T) multiply((T) this).power(n / 2).multiply(this);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T power(final BigInteger n) {
    if (n.compareTo(BigInteger.ZERO) == 0) {
      return one();
    }
    if (n.compareTo(BigInteger.ONE) == 0) {
      return (T) this;
    } else if (n.mod(BIGINT_2).compareTo(BigInteger.ZERO) == 0) {
      return (T) multiply((T) this).power(n.divide(BIGINT_2));
    } else {
      return (T) multiply((T) this).power(n.divide(BIGINT_2)).multiply(this);
    }
  }

  /**
   * Inverse fq [ ].
   *
   * @return the fq [ ]
   */
  protected Fq[] inverse() {
    Fq[] lm = lm();
    Fq[] hm = hm();
    Fq[] low = low();
    Fq[] high = high();
    while (deg(low) > 0) {
      final Fq[] r = polyRoundedDiv(high, low);
      final Fq[] nm = Arrays.copyOf(hm, hm.length);
      final Fq[] neww = Arrays.copyOf(high, high.length);
      for (int i = 0; i < degree + 1; ++i) {
        for (int j = 0; j < degree + 1 - i; ++j) {
          nm[i + j] = nm[i + j].subtract(lm[i].multiply(r[j]));
          neww[i + j] = neww[i + j].subtract(low[i].multiply(r[j]));
        }
      }

      high = low;
      hm = lm;
      low = neww;
      lm = nm;
    }

    for (int i = 0; i < lm.length; ++i) {
      lm[i] = lm[i].divide(low[0]);
    }

    return Arrays.copyOfRange(lm, 0, degree);
  }

  private static Fq[] polyRoundedDiv(final Fq[] a, final Fq[] b) {
    final int degA = deg(a);
    final int degB = deg(b);
    final Fq[] temp = Arrays.copyOf(a, a.length);
    final Fq[] o = new Fq[a.length];
    Arrays.fill(o, Fq.zero());

    for (int i = degA - degB; i >= 0; --i) {
      o[i] = o[i].add(temp[degB + i].divide(b[degB]));
      for (int j = 0; j <= degB; ++j) {
        temp[i + j] = temp[i + j].subtract(o[j]);
      }
    }
    return o;
  }

  private static int deg(final Fq[] p) {
    int d = p.length - 1;
    while (d >= 0 && p[d].equals(Fq.zero())) {
      --d;
    }
    return d;
  }

  private Fq[] lm() {
    final Fq[] lm = new Fq[degree + 1];
    Arrays.fill(lm, Fq.zero());
    lm[0] = Fq.one();
    return lm;
  }

  private Fq[] hm() {
    final Fq[] hm = new Fq[degree + 1];
    Arrays.fill(hm, Fq.zero());
    return hm;
  }

  private Fq[] low() {
    final Fq[] low = Arrays.copyOfRange(coefficients, 0, coefficients.length + 1);
    low[low.length - 1] = Fq.zero();
    return low;
  }

  private Fq[] high() {
    final Fq[] high = Arrays.copyOfRange(modulusCoefficients, 0, modulusCoefficients.length + 1);
    high[high.length - 1] = Fq.one();
    return high;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AbstractFqp)) {
      return false;
    }

    final AbstractFqp<?> other = (AbstractFqp<?>) obj;
    if (degree != other.degree) return false;
    if (!Arrays.equals(modulusCoefficients, other.modulusCoefficients)) return false;
    return Arrays.equals(coefficients, other.coefficients);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        degree, Arrays.hashCode(modulusCoefficients), Arrays.hashCode(coefficients));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass()).add("coefficients", coefficients).toString();
  }
}

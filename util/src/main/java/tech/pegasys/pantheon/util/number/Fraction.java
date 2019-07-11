/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.util.number;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Double.parseDouble;

import java.util.Objects;

public class Fraction {

  private double value;

  private Fraction(final double value) {
    this.value = value;
  }

  /**
   * Parse a string representing a fraction.
   *
   * @param str A string representing a valid positive number strictly between 0 and 1.
   * @return The parsed double.
   * @throws IllegalArgumentException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not a number, or not a fraction
   */
  public static Fraction fromString(final String str) {
    checkArgument(str != null);
    return fromDouble(parseDouble(str));
  }

  public static Fraction fromDouble(final double val) {
    checkArgument(val > 0.0 && val <= 1.0);
    return new Fraction(val);
  }

  public double getValue() {
    return value;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Fraction)) {
      return false;
    }
    final Fraction that = (Fraction) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}

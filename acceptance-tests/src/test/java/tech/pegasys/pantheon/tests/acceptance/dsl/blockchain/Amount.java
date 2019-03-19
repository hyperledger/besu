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
package tech.pegasys.pantheon.tests.acceptance.dsl.blockchain;

import static org.web3j.utils.Convert.Unit.ETHER;
import static org.web3j.utils.Convert.Unit.WEI;

import java.math.BigInteger;

import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

public class Amount {

  private String value;
  private Unit unit;

  private Amount(final long value, final Unit unit) {
    this.value = String.valueOf(value);
    this.unit = unit;
  }

  private Amount(final BigInteger value, final Unit unit) {
    this.value = value.toString();
    this.unit = unit;
  }

  public String getValue() {
    return value;
  }

  public Unit getUnit() {
    return unit;
  }

  public Amount subtract(final Amount subtracting) {

    final Unit denominator;
    if (unit.getWeiFactor().compareTo(subtracting.unit.getWeiFactor()) < 0) {
      denominator = unit;
    } else {
      denominator = subtracting.unit;
    }

    final BigInteger result =
        Convert.fromWei(
                Convert.toWei(value, unit)
                    .subtract(Convert.toWei(subtracting.value, subtracting.unit)),
                denominator)
            .toBigInteger();

    return new Amount(result, denominator);
  }

  public static Amount ether(final long value) {
    return new Amount(value, ETHER);
  }

  public static Amount wei(final BigInteger value) {
    return new Amount(value, WEI);
  }
}

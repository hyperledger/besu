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
package org.hyperledger.besu.datatypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class WeiTest {

  @Test
  public void toHumanReadableString() {
    assertThat(Wei.ZERO.toHumanReadableString()).isEqualTo("0 wei");
    assertThat(Wei.ONE.toHumanReadableString()).isEqualTo("1 wei");

    assertThat(Wei.of(999).toHumanReadableString()).isEqualTo("999 wei");
    assertThat(Wei.of(1000).toHumanReadableString()).isEqualTo("1.00 kwei");

    assertThat(Wei.of(1009).toHumanReadableString()).isEqualTo("1.01 kwei");
    assertThat(Wei.of(1011).toHumanReadableString()).isEqualTo("1.01 kwei");

    assertThat(Wei.of(new BigInteger("1000000000")).toHumanReadableString()).isEqualTo("1.00 gwei");

    assertThat(Wei.of(new BigInteger("1000000000000000000")).toHumanReadableString())
        .isEqualTo("1.00 ether");

    final char[] manyZeros = new char[32];
    Arrays.fill(manyZeros, '0');
    assertThat(Wei.of(new BigInteger("1" + String.valueOf(manyZeros))).toHumanReadableString())
        .isEqualTo("100.00 tether");
  }

  @Test
  public void toHumanReadablePaddedString() {
    assertThat(Wei.ZERO.toHumanReadablePaddedString()).isEqualTo("     0 wei");
    assertThat(Wei.ONE.toHumanReadablePaddedString()).isEqualTo("     1 wei");

    assertThat(Wei.of(999).toHumanReadablePaddedString()).isEqualTo("   999 wei");
    assertThat(Wei.of(1000).toHumanReadablePaddedString()).isEqualTo("  1.00 kwei");

    assertThat(Wei.of(1009).toHumanReadablePaddedString()).isEqualTo("  1.01 kwei");
    assertThat(Wei.of(1011).toHumanReadablePaddedString()).isEqualTo("  1.01 kwei");

    assertThat(Wei.of(new BigInteger("1000000000")).toHumanReadablePaddedString())
        .isEqualTo("  1.00 gwei");

    assertThat(Wei.of(new BigInteger("1000000000000000000")).toHumanReadablePaddedString())
        .isEqualTo("  1.00 ether");

    final char[] manyZeros = new char[32];
    Arrays.fill(manyZeros, '0');
    assertThat(
            Wei.of(new BigInteger("1" + String.valueOf(manyZeros))).toHumanReadablePaddedString())
        .isEqualTo("100.00 tether");
  }
}

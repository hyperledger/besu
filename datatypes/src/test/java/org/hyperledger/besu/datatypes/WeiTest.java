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
    assertThat(Wei.ZERO.toHumanReadableString()).isEqualTo(String.format("%d wei", 0));
    assertThat(Wei.ONE.toHumanReadableString()).isEqualTo(String.format("%d wei", 1));

    assertThat(Wei.of(999).toHumanReadableString()).isEqualTo(String.format("%d wei", 999));
    assertThat(Wei.of(1000).toHumanReadableString()).isEqualTo(String.format("%.2f kwei", 1.0));

    assertThat(Wei.of(1009).toHumanReadableString()).isEqualTo(String.format("%.2f kwei", 1.01));
    assertThat(Wei.of(1011).toHumanReadableString()).isEqualTo(String.format("%.2f kwei", 1.01));

    assertThat(Wei.of(new BigInteger("1000000000")).toHumanReadableString())
        .isEqualTo(String.format("%.2f gwei", 1.0));

    assertThat(Wei.of(new BigInteger("1000000000000000000")).toHumanReadableString())
        .isEqualTo(String.format("%.2f ether", 1.0));

    final char[] manyZeros = new char[32];
    Arrays.fill(manyZeros, '0');
    assertThat(Wei.of(new BigInteger("1" + String.valueOf(manyZeros))).toHumanReadableString())
        .isEqualTo(String.format("%.2f tether", 100.0));
  }

  @Test
  public void toHumanReadablePaddedString() {
    assertThat(Wei.ZERO.toHumanReadablePaddedString()).isEqualTo(String.format("     %d wei", 0));
    assertThat(Wei.ONE.toHumanReadablePaddedString()).isEqualTo(String.format("     %d wei", 1));

    assertThat(Wei.of(999).toHumanReadablePaddedString())
        .isEqualTo(String.format("   %d wei", 999));
    assertThat(Wei.of(1000).toHumanReadablePaddedString())
        .isEqualTo(String.format("  %.2f kwei", 1.0));

    assertThat(Wei.of(1009).toHumanReadablePaddedString())
        .isEqualTo(String.format("  %.2f kwei", 1.01));
    assertThat(Wei.of(1011).toHumanReadablePaddedString())
        .isEqualTo(String.format("  %.2f kwei", 1.01));

    assertThat(Wei.of(new BigInteger("1000000000")).toHumanReadablePaddedString())
        .isEqualTo(String.format("  %.2f gwei", 1.0));

    assertThat(Wei.of(new BigInteger("1000000000000000000")).toHumanReadablePaddedString())
        .isEqualTo(String.format("  %.2f ether", 1.0));

    final char[] manyZeros = new char[32];
    Arrays.fill(manyZeros, '0');
    assertThat(
            Wei.of(new BigInteger("1" + String.valueOf(manyZeros))).toHumanReadablePaddedString())
        .isEqualTo(String.format("%.2f tether", 100.0));
  }
}

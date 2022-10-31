/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */

package org.hyperledger.besu.evm.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.Constants.ZERO_32;
import static org.hyperledger.besu.evm.internal.Words.unsignedMin;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.Arrays;
import java.util.Collection;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
class WordsTest {

  Collection<Object[]> unsignedMinLongTestVector() {
    return Arrays.asList(
        new Object[][] {
          {10, 10, 10},
          {-10, -10, -10},
          {10, 20, 10},
          {20, 10, 10},
          {10, -10, 10},
          {-10, 10, 10},
          {-20, -10, -20},
          {-10, -20, -20},
        });
  }

  @ParameterizedTest
  @MethodSource("unsignedMinLongTestVector")
  void unsugnedMinLongTest(final long a, final long b, final long min) {
    assertThat(unsignedMin(a, b)).isEqualTo(min);
  }

  @Test
  void constantAddressConversionTests() {
    assertThat(Words.toAddress(Bytes.EMPTY).toShortHexString())
        .isEqualTo(Address.EMPTY.toShortHexString());
    assertThat(Words.toAddress(ZERO_32).toShortHexString())
        .isEqualTo(Address.EMPTY.toShortHexString());
    assertThat(Words.toAddress(Hash.ZERO_HASH).toShortHexString())
        .isEqualTo(Address.EMPTY.toShortHexString());
  }
}

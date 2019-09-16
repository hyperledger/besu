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
package org.hyperledger.besu.util.bytes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Bytes32sSingleLeftShiftTest {

  private final String shiftAmount;
  private final String startValue;
  private final String expectedValue;
  static String[][] testData = {
    {"1", "1", "2"},
    {"1", "2", "4"},
    {"1", "3", "6"},
    {"1", "4", "8"},
    {"1", "5", "a"},
    {"1", "6", "c"},
    {"1", "7", "e"},
    {"1", "8", "10"},
    {"1", "9", "12"},
    {"1", "a", "14"},
    {"1", "b", "16"},
    {"1", "c", "18"},
    {"1", "d", "1a"},
    {"1", "e", "1c"},
    {"1", "f", "1e"},
    {"1", "FF0", "1fe0"},
    {"1", "FFFF", "1fffe"},
    {"1", "8888", "11110"},
    {
      "1",
      "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe"
    },
    {"2", "1", "4"},
    {"3", "1", "8"},
    {"4", "1", "10"},
    {"5", "1", "20"},
    {"255", "1", "0x8000000000000000000000000000000000000000000000000000000000000000"},
    {"256", "1", "0"}
  };

  @Parameterized.Parameters(name = ">>{0}, from {1}, expect {2}")
  public static Iterable<Object[]> data() {
    return Arrays.asList((Object[][]) testData);
  }

  public Bytes32sSingleLeftShiftTest(
      final String shiftAmount, final String startValue, final String expectedValue) {
    this.shiftAmount = shiftAmount;
    this.startValue = startValue;
    this.expectedValue = expectedValue;
  }

  @Test
  public void singleLeftShift() {
    final Bytes32 bytes32 =
        Bytes32s.shiftLeft(Bytes32.fromHexStringLenient(startValue), Integer.valueOf(shiftAmount));
    assertThat(bytes32).isEqualTo(Bytes32.fromHexStringLenient(expectedValue));
  }
}

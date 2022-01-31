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
package org.hyperledger.besu.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import com.google.common.collect.Range;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OptionParserTest {

  @Test
  public void parseLongRange_negative() {
    final String input = "-11..-5";
    final Range<Long> expected = Range.closed(-11L, -5L);
    assertThat(OptionParser.parseLongRange(input)).isEqualTo(expected);
  }

  @Test
  public void parseLongRange_positive() {
    final String input = "11..22";
    final Range<Long> expected = Range.closed(11L, 22L);
    assertThat(OptionParser.parseLongRange(input)).isEqualTo(expected);
  }

  @Test
  public void parseLongRange_spanningZero() {
    final String input = "-11..22";
    final Range<Long> expected = Range.closed(-11L, 22L);
    assertThat(OptionParser.parseLongRange(input)).isEqualTo(expected);
  }

  @Test
  public void parseLongRange_singleElement() {
    final String input = "1..1";
    final Range<Long> expected = Range.closed(1L, 1L);
    assertThat(OptionParser.parseLongRange(input)).isEqualTo(expected);
  }

  @Test
  public void parseLongRange_outOfOrderBounds() {
    final String input = "2..1";
    assertThatThrownBy(() -> OptionParser.parseLongRange(input))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void format_longRange() {
    final Range<Long> input = Range.closed(-11L, -5L);
    final String expected = "-11..-5";
    assertThat(OptionParser.format(input)).isEqualTo(expected);
  }

  @Test
  public void format_uint256() {
    final UInt256 input = UInt256.valueOf(new BigInteger("123456789", 10));
    final String expected = "123456789";
    assertThat(OptionParser.format(input)).isEqualTo(expected);
  }

  @Test
  public void format_positiveInt() {
    final int input = 1233;
    final String expected = "1233";
    assertThat(OptionParser.format(input)).isEqualTo(expected);
  }

  @Test
  public void format_negativeInt() {
    final int input = -1233;
    final String expected = "-1233";
    assertThat(OptionParser.format(input)).isEqualTo(expected);
  }

  @Test
  public void format_positiveLong() {
    final long input = 1233L;
    final String expected = "1233";
    assertThat(OptionParser.format(input)).isEqualTo(expected);
  }

  @Test
  public void format_negativeLong() {
    final long input = -1233L;
    final String expected = "-1233";
    assertThat(OptionParser.format(input)).isEqualTo(expected);
  }
}

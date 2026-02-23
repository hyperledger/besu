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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CountLeadingZerosOperationTest {

  private final GasCalculator gasCalculator = new SpuriousDragonGasCalculator();
  private final CountLeadingZerosOperation operation =
      new CountLeadingZerosOperation(gasCalculator);

  static Stream<Arguments> provideClzTestCases() {
    return Stream.of(
        Arguments.of("0x0000000000000000000000000000000000000000000000000000000000000000", 256),
        Arguments.of("0x8000000000000000000000000000000000000000000000000000000000000000", 0),
        Arguments.of("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 0),
        Arguments.of("0x4000000000000000000000000000000000000000000000000000000000000000", 1),
        Arguments.of("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 1),
        Arguments.of(
            "0x00ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f", 0),
        Arguments.of("0x01", 255),
        Arguments.of("0xff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff", 8));
  }

  @ParameterizedTest
  @MethodSource("provideClzTestCases")
  void testClzOperation(final String value, final int expectedLeadingZeros) {
    Bytes raw = Bytes.fromHexString(value);
    // Truncate to lower 32 bytes if longer (as the EVM stack would)
    byte[] padded =
        raw.size() >= 32
            ? raw.slice(raw.size() - 32, 32).toArrayUnsafe()
            : Bytes32.leftPad(raw).toArrayUnsafe();

    final MessageFrame frame =
        new TestMessageFrameBuilder().pushStackItem(Bytes.wrap(padded)).build();
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0)).isEqualTo(UInt256.fromInt(expectedLeadingZeros));
  }
}

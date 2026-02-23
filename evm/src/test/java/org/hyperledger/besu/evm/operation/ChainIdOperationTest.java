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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChainIdOperationTest {

  static Iterable<Arguments> params() {
    return List.of(
        Arguments.of("0x01", 2),
        Arguments.of("0x03", 2),
        Arguments.of("0x04", 2),
        Arguments.of("0x05", 2));
  }

  @ParameterizedTest
  @MethodSource("params")
  void shouldReturnChainId(final String chainIdString, final int expectedGas) {
    final Bytes32 chainId = Bytes32.fromHexString(chainIdString);
    final ChainIdOperation operation =
        new ChainIdOperation(new ConstantinopleGasCalculator(), chainId);
    final MessageFrame frame = new TestMessageFrameBuilder().build();
    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(expectedGas);
    assertThat(frame.getStackItem(0))
        .isEqualTo(org.hyperledger.besu.evm.UInt256.fromBytesBE(chainId.toArrayUnsafe()));
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}

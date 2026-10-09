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
package org.hyperledger.besu.evm.v2;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

/** Tracers read the v1 stack, so an EVM configured for v2 runs traced execution on v1. */
class TracedExecutionV2Test {

  /** PUSH1 1 PUSH1 2 ADD STOP */
  private static final Code ONE_PLUS_TWO = new Code(Bytes.fromHexString("0x600160020100"));

  private final EVM evm =
      MainnetEVMs.osaka(
          BigInteger.ONE,
          new EvmConfiguration(32_000L, EvmConfiguration.WorldUpdaterMode.STACKED, true, true));

  @Test
  void tracedExecutionRunsOnTheV1Stack() {
    final List<String> traced = new ArrayList<>();
    final OperationTracer tracer =
        new OperationTracer() {
          @Override
          public void tracePostExecution(final MessageFrame frame, final OperationResult result) {
            traced.add(frame.getCurrentOperation().getName() + " " + frame.stackSize());
          }
        };
    final MessageFrame frame = start();

    evm.runToHalt(frame, tracer);

    assertThat(traced).containsExactly("PUSH1 1", "PUSH1 2", "ADD 1", "STOP 1");
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes32.leftPad(Bytes.of(3)));
    assertThat(frame.stackDataV2()).isNull();
  }

  @Test
  void untracedExecutionRunsOnTheV2Stack() {
    final MessageFrame frame = start();

    evm.runToHalt(frame, OperationTracer.NO_TRACING);

    assertThat(frame.stackSize()).isZero();
    assertThat(frame.stackTopV2()).isEqualTo(1);
    assertThat(frame.stackDataV2()[3]).isEqualTo(3L);
  }

  private static MessageFrame start() {
    final MessageFrame frame = new TestMessageFrameBuilder().code(ONE_PLUS_TWO).build();
    frame.setState(MessageFrame.State.CODE_EXECUTING);
    return frame;
  }
}

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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.testutils.FakeBlockValues;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class BlockHashOperationTest {
  private static final long ENOUGH_GAS = 30_000_000L;

  private final BlockHashOperation blockHashOperation =
      new BlockHashOperation(new FrontierGasCalculator());

  @Test
  void shouldReturnZeroWhenArgIsBiggerThanALong() {
    assertBlockHash(
        Bytes32.fromHexString("F".repeat(64)),
        Bytes32.ZERO,
        100,
        (__, ___) -> Hash.EMPTY_LIST_HASH,
        ENOUGH_GAS);
  }

  @Test
  void shouldReturnBlockHashUsingLookupFromFrameWhenItIsWithinTheAllowedRange() {
    final Hash blockHash = Hash.hash(Bytes.fromHexString("0x1293487297"));
    assertBlockHash(
        100,
        blockHash,
        200,
        (__, block) -> block == 100 ? blockHash : Hash.EMPTY_LIST_HASH,
        ENOUGH_GAS);
  }

  @Test
  void shouldFailWithInsufficientGas() {
    assertFailure(
        Bytes32.fromHexString("0x64"),
        ExceptionalHaltReason.INSUFFICIENT_GAS,
        200,
        (__, ___) -> Hash.hash(Bytes.fromHexString("0x1293487297")),
        1);
  }

  private void assertBlockHash(
      final long requestedBlock,
      final Bytes32 expectedOutput,
      final long currentBlockNumber,
      final BlockHashLookup blockHashLookup,
      final long initialGas) {
    assertBlockHash(
        UInt256.valueOf(requestedBlock),
        expectedOutput,
        currentBlockNumber,
        blockHashLookup,
        initialGas);
  }

  private void assertBlockHash(
      final Bytes32 input,
      final Bytes32 expectedOutput,
      final long currentBlockNumber,
      final BlockHashLookup blockHashLookup,
      final long initialGas) {
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .blockHashLookup(blockHashLookup)
            .blockValues(new FakeBlockValues(currentBlockNumber))
            .pushStackItem(UInt256.fromBytes(input))
            .initialGas(initialGas)
            .build();
    blockHashOperation.execute(frame, null);
    final Bytes result = frame.popStackItem();
    assertThat(result).isEqualTo(expectedOutput);
    assertThat(frame.stackSize()).isZero();
  }

  private void assertFailure(
      final Bytes32 input,
      final ExceptionalHaltReason haltReason,
      final long currentBlockNumber,
      final BlockHashLookup blockHashLookup,
      final long initialGas) {
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .blockHashLookup(blockHashLookup)
            .blockValues(new FakeBlockValues(currentBlockNumber))
            .pushStackItem(UInt256.fromBytes(input))
            .initialGas(initialGas)
            .build();
    Operation.OperationResult operationResult = blockHashOperation.execute(frame, null);
    assertThat(operationResult.getHaltReason()).isEqualTo(haltReason);
    assertThat(frame.stackSize()).isOne();
  }
}

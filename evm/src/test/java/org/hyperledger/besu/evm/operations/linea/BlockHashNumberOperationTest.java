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
package org.hyperledger.besu.evm.operations.linea;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.operation.linea.BlockHashNumberOperation;
import org.hyperledger.besu.evm.testutils.FakeBlockValues;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

public class BlockHashNumberOperationTest {

  private static final int MAXIMUM_COMPLETE_BLOCKS_BEHIND = 256;
  private final BlockHashNumberOperation blockHashOperation =
      new BlockHashNumberOperation(new FrontierGasCalculator());

  @Test
  public void shouldReturnCurrentBlockNumberWhenArgIsBiggerThanALong() {
    assertBlockHash(Bytes32.fromHexString("F".repeat(64)), 100, n -> Hash.EMPTY_LIST_HASH);
  }

  @Test
  public void shouldReturnCurrentBlockNumberWhenCurrentBlockIsGenesis() {
    assertBlockHash(Bytes32.ZERO, 0, block -> Hash.EMPTY_LIST_HASH);
  }

  @Test
  public void shouldReturnCurrentBlockNumberWhenRequestedBlockAheadOfCurrent() {
    assertBlockHash(250, 100, block -> Hash.EMPTY_LIST_HASH);
  }

  @Test
  public void shouldReturnCurrentBlockNumberWhenRequestedBlockTooFarBehindCurrent() {
    final int requestedBlock = 10;
    // Our block is the one after the chain head (it's a new block), hence the + 1.
    final int importingBlockNumber = MAXIMUM_COMPLETE_BLOCKS_BEHIND + requestedBlock + 1;
    assertBlockHash(requestedBlock, importingBlockNumber, block -> Hash.EMPTY_LIST_HASH);
  }

  @Test
  public void shouldReturnCurrentBlockNumberWhenRequestedBlockGreaterThanImportingBlock() {
    assertBlockHash(101, 100, block -> Hash.EMPTY_LIST_HASH);
  }

  @Test
  public void shouldReturnCurrentBlockNumberWhenRequestedBlockEqualToImportingBlock() {
    assertBlockHash(100, 100, block -> Hash.EMPTY_LIST_HASH);
  }

  @Test
  public void shouldReturnCurrentBlockNumberUsingLookupFromFrameWhenItIsWithinTheAllowedRange() {
    assertBlockHash(100, 200, block -> Hash.EMPTY_LIST_HASH);
  }

  private void assertBlockHash(
      final long requestedBlock,
      final long currentBlockNumber,
      final Function<Long, Hash> blockHashLookup) {
    assertBlockHash(UInt256.valueOf(requestedBlock), currentBlockNumber, blockHashLookup);
  }

  private void assertBlockHash(
      final Bytes32 input,
      final long currentBlockNumber,
      final Function<Long, Hash> blockHashLookup) {
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .blockHashLookup(blockHashLookup)
            .blockValues(new FakeBlockValues(currentBlockNumber))
            .pushStackItem(UInt256.fromBytes(input))
            .build();
    blockHashOperation.execute(frame, null);
    final Bytes32 result = Bytes32.leftPad(frame.popStackItem());
    assertThat(result).isEqualTo(UInt256.valueOf(currentBlockNumber));
    assertThat(frame.stackSize()).isZero();
  }
}

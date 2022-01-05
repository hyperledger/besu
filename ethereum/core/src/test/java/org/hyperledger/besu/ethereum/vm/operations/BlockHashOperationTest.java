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
package org.hyperledger.besu.ethereum.vm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.After;
import org.junit.Test;

public class BlockHashOperationTest {

  private static final int MAXIMUM_COMPLETE_BLOCKS_BEHIND = 256;
  private final BlockHashLookup blockHashLookup = mock(BlockHashLookup.class);
  private final BlockHashOperation blockHashOperation =
      new BlockHashOperation(new FrontierGasCalculator());

  @After
  public void verifyNoUnexpectedHashLookups() {
    verifyNoMoreInteractions(blockHashLookup);
  }

  @Test
  public void shouldReturnZeroWhenArgIsBiggerThanALong() {
    assertBlockHash(Bytes32.fromHexString("F".repeat(64)), Bytes32.ZERO, 100);
  }

  @Test
  public void shouldReturnZeroWhenCurrentBlockIsGenesis() {
    assertBlockHash(Bytes32.ZERO, Bytes32.ZERO, BlockHeader.GENESIS_BLOCK_NUMBER);
  }

  @Test
  public void shouldReturnZeroWhenRequestedBlockAheadOfCurrent() {
    assertBlockHash(250, Bytes32.ZERO, 100);
  }

  @Test
  public void shouldReturnZeroWhenRequestedBlockTooFarBehindCurrent() {
    final int requestedBlock = 10;
    // Our block is the one after the chain head (it's a new block), hence the + 1.
    final int importingBlockNumber = MAXIMUM_COMPLETE_BLOCKS_BEHIND + requestedBlock + 1;
    assertBlockHash(requestedBlock, Bytes32.ZERO, importingBlockNumber);
  }

  @Test
  public void shouldReturnZeroWhenRequestedBlockGreaterThanImportingBlock() {
    assertBlockHash(101, Bytes32.ZERO, 100);
  }

  @Test
  public void shouldReturnZeroWhenRequestedBlockEqualToImportingBlock() {
    assertBlockHash(100, Bytes32.ZERO, 100);
  }

  @Test
  public void shouldReturnBlockHashUsingLookupFromFrameWhenItIsWithinTheAllowedRange() {
    final Hash blockHash = Hash.hash(Bytes.fromHexString("0x1293487297"));
    when(blockHashLookup.apply(100L)).thenReturn(blockHash);
    assertBlockHash(100, blockHash, 200);
    verify(blockHashLookup).apply(100L);
  }

  private void assertBlockHash(
      final long requestedBlock, final Bytes32 expectedOutput, final long currentBlockNumber) {
    assertBlockHash(UInt256.valueOf(requestedBlock), expectedOutput, currentBlockNumber);
  }

  private void assertBlockHash(
      final Bytes32 input, final Bytes32 expectedOutput, final long currentBlockNumber) {
    final MessageFrame frame =
        new MessageFrameTestFixture()
            .blockHashLookup(blockHashLookup)
            .blockHeader(new BlockHeaderTestFixture().number(currentBlockNumber).buildHeader())
            .pushStackItem(UInt256.fromBytes(input))
            .build();
    blockHashOperation.execute(frame, null);
    final Bytes result = frame.popStackItem();
    assertThat(result).isEqualTo(expectedOutput);
    assertThat(frame.stackSize()).isZero();
  }
}

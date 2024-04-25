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
package org.hyperledger.besu.evm.operations;

import static org.assertj.core.api.Assertions.assertThat;
  import static org.hyperledger.besu.evm.operation.BlockHashOperation.BlockHashRetrievalStrategy;
import static org.hyperledger.besu.evm.operation.BlockHashOperation.BlockHashRetrievalStrategy.STATE_READ;
import static org.hyperledger.besu.evm.operation.BlockHashOperation.HISTORICAL_BLOCKHASH_ADDRESS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.testutils.FakeBlockValues;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BlockHashOperationTest {
  private static final int MAX_RELATIVE_BLOCK = 256;

  private static final int MAXIMUM_COMPLETE_BLOCKS_BEHIND = 256;
  protected WorldUpdater worldUpdater = mock(WorldUpdater.class);

  private BlockHashOperation createBlockHashOperation(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    return new BlockHashOperation(new FrontierGasCalculator(), blockHashRetrievalStrategy);
  }

  @ParameterizedTest
  @EnumSource(BlockHashRetrievalStrategy.class)
  void shouldReturnZeroWhenArgIsBiggerThanALong(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    assertBlockHash(
        Bytes32.fromHexString("F".repeat(64)),
        Bytes32.ZERO,
        100,
        n -> Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(blockHashRetrievalStrategy));
  }

  @ParameterizedTest
  @EnumSource(BlockHashRetrievalStrategy.class)
  void shouldReturnZeroWhenCurrentBlockIsGenesis(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    assertBlockHash(
        Bytes32.ZERO,
        Bytes32.ZERO,
        0,
        block -> Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(blockHashRetrievalStrategy));
  }

  @ParameterizedTest
  @EnumSource(BlockHashRetrievalStrategy.class)
  void shouldReturnZeroWhenRequestedBlockAheadOfCurrent(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    assertBlockHash(
        250,
        Bytes32.ZERO,
        100,
        block -> Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(blockHashRetrievalStrategy));
  }

  @ParameterizedTest
  @EnumSource(BlockHashRetrievalStrategy.class)
  void shouldReturnZeroWhenRequestedBlockTooFarBehindCurrent(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    final int requestedBlock = 10;
    // Our block is the one after the chain head (it's a new block), hence the + 1.
    final int importingBlockNumber = MAXIMUM_COMPLETE_BLOCKS_BEHIND + requestedBlock + 1;
    assertBlockHash(
        requestedBlock,
        Bytes32.ZERO,
        importingBlockNumber,
        block -> Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(blockHashRetrievalStrategy));
  }

  @ParameterizedTest
  @EnumSource(BlockHashRetrievalStrategy.class)
  void shouldReturnZeroWhenRequestedBlockGreaterThanImportingBlock(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    assertBlockHash(
        101,
        Bytes32.ZERO,
        100,
        block -> Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(blockHashRetrievalStrategy));
  }

  @ParameterizedTest
  @EnumSource(BlockHashRetrievalStrategy.class)
  void shouldReturnZeroWhenRequestedBlockEqualToImportingBlock(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    assertBlockHash(
        100,
        Bytes32.ZERO,
        100,
        block -> Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(blockHashRetrievalStrategy));
  }

  @ParameterizedTest
  @EnumSource(BlockHashRetrievalStrategy.class)
  void shouldReturnBlockHashWhenItIsWithinTheAllowedRange(
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    final Hash blockHash = Hash.hash(Bytes.fromHexString("0x1293487297"));
    mockStorageValue(100, blockHash);
    assertBlockHash(
        100,
        blockHash,
        200,
        block -> block == 100 ? blockHash : Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(blockHashRetrievalStrategy));
  }

  private void assertBlockHash(
      final long requestedBlock,
      final Bytes32 expectedOutput,
      final long currentBlockNumber,
      final Function<Long, Hash> blockHashLookup,
      final BlockHashOperation blockHashOperation) {
    assertBlockHash(
        UInt256.valueOf(requestedBlock),
        expectedOutput,
        currentBlockNumber,
        blockHashLookup,
        blockHashOperation);
  }

  protected void assertBlockHash(
      final Bytes32 input,
      final Bytes32 expectedOutput,
      final long currentBlockNumber,
      final Function<Long, Hash> blockHashLookup,
      final BlockHashOperation blockHashOperation) {
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .blockHashLookup(blockHashLookup)
            .worldUpdater(worldUpdater)
            .blockValues(new FakeBlockValues(currentBlockNumber))
            .pushStackItem(UInt256.fromBytes(input))
            .build();
    blockHashOperation.execute(frame, null);
    final Bytes result = frame.popStackItem();
    assertThat(result).isEqualTo(expectedOutput);
    assertThat(frame.stackSize()).isZero();
  }

  @Test
  void shouldReturnZeroWhenBlockHashNotInState() {
    final long requestedBlock = 100;
    final long currentBlockNumber = 200;
    mockStorageValue(requestedBlock, Hash.ZERO);

    assertBlockHash(
        UInt256.valueOf(requestedBlock),
        Hash.ZERO,
        currentBlockNumber,
        block -> Hash.EMPTY_LIST_HASH,
        createBlockHashOperation(STATE_READ));
  }

  private void mockStorageValue(final long blockNumber, final Hash blockHash) {
    Account account = mock(Account.class);
    when(account.getStorageValue(UInt256.valueOf(blockNumber % MAX_RELATIVE_BLOCK)))
        .thenReturn(UInt256.fromBytes(blockHash));
    when(worldUpdater.get(HISTORICAL_BLOCKHASH_ADDRESS)).thenReturn(account);
  }
}

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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.AccessWitness;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.gascalculator.stateless.NoopAccessWitness;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Eip7709BlockHashLookupTest {
  private static final long BLOCKHASH_SERVE_WINDOW = 160;
  private static final Address STORAGE_ADDRESS = Address.fromHexString("0x0");
  private static final long HISTORY_SERVE_WINDOW = 200L;
  private static final int CURRENT_BLOCK_NUMBER =
      Math.toIntExact(HISTORY_SERVE_WINDOW + BLOCKHASH_SERVE_WINDOW / 2);
  private List<BlockHeader> headers;
  private BlockHashLookup lookup;
  private MessageFrame frame;
  private WorldUpdater worldUpdater;

  @BeforeEach
  void setUp() {
    headers = new ArrayList<>();
    worldUpdater = createWorldUpdater(0, CURRENT_BLOCK_NUMBER);
    lookup =
        new Eip7709BlockHashLookup(STORAGE_ADDRESS, HISTORY_SERVE_WINDOW, BLOCKHASH_SERVE_WINDOW);
    frame = spy(createMessageFrame(CURRENT_BLOCK_NUMBER, worldUpdater, Long.MAX_VALUE).build());
  }

  private WorldUpdater createWorldUpdater(final int fromBlockNumber, final int toBlockNumber) {
    WorldUpdater worldUpdaterMock = mock(WorldUpdater.class);
    SimpleAccount contractAccount = spy(new SimpleAccount(STORAGE_ADDRESS, 0, Wei.ZERO));
    when(worldUpdaterMock.get(STORAGE_ADDRESS)).thenReturn(contractAccount);
    BlockHeader parentHeader = null;
    for (int i = fromBlockNumber; i < toBlockNumber; i++) {
      final BlockHeader header = createHeader(i, parentHeader);
      headers.add(header);
      contractAccount.setStorageValue(
          UInt256.valueOf(i % HISTORY_SERVE_WINDOW), UInt256.fromBytes(header.getHash()));
      parentHeader = header;
    }
    return worldUpdaterMock;
  }

  private MessageFrameTestFixture createMessageFrame(
      final long currentBlockNumber, final WorldUpdater worldUpdater, final long remainingGas) {
    final BlockHeader blockHeader =
        new BlockHeaderTestFixture().number(currentBlockNumber).buildHeader();
    final ReferenceTestBlockchain blockchain = new ReferenceTestBlockchain(blockHeader.getNumber());
    return new MessageFrameTestFixture()
        .initialGas(remainingGas)
        .accessWitness(NoopAccessWitness.get())
        .blockHashLookup(lookup)
        .worldUpdater(worldUpdater)
        .executionContextTestFixture(ExecutionContextTestFixture.create())
        .gasPrice(Wei.of(25))
        .blockHeader(blockHeader)
        .blockchain(blockchain);
  }

  @Test
  void shouldGetHashOfImmediateParent() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
  }

  @Test
  void shouldGetHashOfMaxBlocksBehind() {
    assertHashForBlockNumber(Math.toIntExact(CURRENT_BLOCK_NUMBER - BLOCKHASH_SERVE_WINDOW));
  }

  @Test
  void shouldReturnZeroHashWhenSystemContractNotExists() {
    worldUpdater = new SimpleWorld();
    frame = spy(createMessageFrame(CURRENT_BLOCK_NUMBER, worldUpdater, Long.MAX_VALUE).build());
    assertThat(lookup.apply(frame, CURRENT_BLOCK_NUMBER - 1L)).isEqualTo(Hash.ZERO);
  }

  @Test
  @SuppressWarnings("ReturnValueIgnored")
  void shouldDecrementRemainingGasFromFrameWhenOOG() {
    AccessWitness accessWitness = mock(AccessWitness.class);
    when(accessWitness.touchAndChargeStorageLoad(any(), any(), anyLong()))
        .thenReturn(Long.MAX_VALUE);
    frame =
        spy(
            createMessageFrame(CURRENT_BLOCK_NUMBER, worldUpdater, 0)
                .accessWitness(accessWitness)
                .build());
    lookup.apply(frame, CURRENT_BLOCK_NUMBER - 1L);
    verify(frame).decrementRemainingGas(eq(Long.MAX_VALUE));
    verify(frame).getAccessWitness();
    verify(frame, times(2)).getRemainingGas();
    verifyNoMoreInteractions(frame);
  }

  @Test
  @SuppressWarnings("ReturnValueIgnored")
  void shouldDecrementRemainingGasFromFrame() {
    AccessWitness accessWitness = mock(AccessWitness.class);
    when(accessWitness.touchAndChargeStorageLoad(any(), any(), anyLong())).thenReturn(100L);
    frame = spy(createMessageFrame(CURRENT_BLOCK_NUMBER, worldUpdater, 200L).build());
    when(frame.getAccessWitness()).thenReturn(accessWitness);
    lookup.apply(frame, CURRENT_BLOCK_NUMBER - 1L);
    verify(frame).decrementRemainingGas(eq(100L));
    verify(frame).getAccessWitness();
    verify(frame, times(2)).getRemainingGas();
    verify(frame).getWorldUpdater();
    verifyNoMoreInteractions(frame);
  }

  @Test
  void insufficientGasReturnsNullBlockHash() {
    worldUpdater = new SimpleWorld();
    AccessWitness accessWitness = mock(AccessWitness.class);
    when(accessWitness.touchAndChargeStorageLoad(any(), any(), anyLong())).thenReturn(100L);
    frame =
        spy(
            createMessageFrame(CURRENT_BLOCK_NUMBER, worldUpdater, 1L)
                .accessWitness(accessWitness)
                .build());
    final Hash blockHash = lookup.apply(frame, CURRENT_BLOCK_NUMBER - 1L);
    assertThat(blockHash).isNull();
  }

  @Test
  void shouldReturnZeroHashWhenParentBlockNotInContract() {
    worldUpdater = createWorldUpdater(CURRENT_BLOCK_NUMBER - 10, CURRENT_BLOCK_NUMBER);
    frame = spy(createMessageFrame(CURRENT_BLOCK_NUMBER, worldUpdater, Long.MAX_VALUE).build());
    lookup =
        new Eip7709BlockHashLookup(STORAGE_ADDRESS, HISTORY_SERVE_WINDOW, BLOCKHASH_SERVE_WINDOW);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 20, Hash.ZERO);
  }

  @Test
  void shouldCacheBlockHashes() {
    worldUpdater = createWorldUpdater(0, CURRENT_BLOCK_NUMBER);
    frame = spy(createMessageFrame(CURRENT_BLOCK_NUMBER, worldUpdater, Long.MAX_VALUE).build());
    final Account account = worldUpdater.get(STORAGE_ADDRESS);
    clearInvocations(account);

    int blockNumber1 = CURRENT_BLOCK_NUMBER - 1;
    int blockNumber2 = CURRENT_BLOCK_NUMBER - 4;
    int blockNumber3 = CURRENT_BLOCK_NUMBER - 5;
    assertHashForBlockNumber(blockNumber1);
    assertHashForBlockNumber(blockNumber1);
    assertHashForBlockNumber(blockNumber2);
    assertHashForBlockNumber(blockNumber3);
    assertHashForBlockNumber(blockNumber3);
    assertHashForBlockNumber(blockNumber3);

    verify(account, times(1))
        .getStorageValue(eq(UInt256.valueOf(blockNumber1 % HISTORY_SERVE_WINDOW)));
    verify(account, times(1))
        .getStorageValue(eq(UInt256.valueOf(blockNumber2 % HISTORY_SERVE_WINDOW)));
    verify(account, times(1))
        .getStorageValue(eq(UInt256.valueOf(blockNumber3 % HISTORY_SERVE_WINDOW)));
    verifyNoMoreInteractions(account);
  }

  @Test
  void shouldGetHashWhenParentIsGenesis() {
    worldUpdater = createWorldUpdater(0, 1);
    frame = spy(createMessageFrame(1, worldUpdater, Long.MAX_VALUE).build());
    lookup =
        new Eip7709BlockHashLookup(STORAGE_ADDRESS, HISTORY_SERVE_WINDOW, BLOCKHASH_SERVE_WINDOW);
    assertHashForBlockNumber(0);
  }

  @Test
  void shouldReturnZeroWhenRequestedBlockEqualToImportingBlock() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER, Hash.ZERO);
  }

  @Test
  void shouldReturnZeroWhenRequestedBlockAheadOfCurrent() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER + 1, Hash.ZERO);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER + 20, Hash.ZERO);
  }

  @Test
  void shouldReturnZeroWhenRequestedBlockTooFarBehindCurrent() {
    assertHashForBlockNumber(
        Math.toIntExact(CURRENT_BLOCK_NUMBER - BLOCKHASH_SERVE_WINDOW - 1), Hash.ZERO);
    assertHashForBlockNumber(2, Hash.ZERO);
  }

  private void assertHashForBlockNumber(final int blockNumber) {
    assertHashForBlockNumber(blockNumber, headers.get(blockNumber).getHash());
  }

  private void assertHashForBlockNumber(final int blockNumber, final Hash hash) {
    BlockHashOperation op = new BlockHashOperation(new CancunGasCalculator());
    frame.pushStackItem(Bytes.ofUnsignedInt(blockNumber));

    clearInvocations(frame);

    op.execute(frame, null);

    verify(frame).pushStackItem(hash);
  }

  private BlockHeader createHeader(final long blockNumber, final BlockHeader parentHeader) {
    return new BlockHeaderTestFixture()
        .number(blockNumber)
        .parentHash(parentHeader != null ? parentHeader.getHash() : Hash.EMPTY)
        .buildHeader();
  }
}

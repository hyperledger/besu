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
package org.hyperledger.besu.ethereum.transaction;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverride;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.datatypes.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.plugin.services.storage.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.blockhash.PreExecutionProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.exceptions.BlockStateCallException;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.BlockOverrides;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BlockSimulatorTest {

  @Mock private WorldStateArchive worldStateArchive;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private TransactionSimulator transactionSimulator;
  @Mock private MiningConfiguration miningConfiguration;
  @Mock private MutableWorldState mutableWorldState;
  @Mock private Blockchain blockchain;
  @Mock private WorldUpdater updater;
  @Mock private ProtocolSpec protocolSpec;

  private BlockHeader blockHeader;
  private BlockSimulator blockSimulator;

  @BeforeEach
  public void setUp() {
    blockSimulator =
        new BlockSimulator(
            worldStateArchive,
            protocolSchedule,
            transactionSimulator,
            miningConfiguration,
            blockchain,
            0);
    blockHeader = BlockHeaderBuilder.createDefault().buildBlockHeader();
    when(miningConfiguration.getCoinbase())
        .thenReturn(Optional.ofNullable(Address.fromHexString("0x1")));
    when(protocolSchedule.getForNextBlockHeader(any(), anyLong())).thenReturn(protocolSpec);
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    when(protocolSpec.getMiningBeneficiaryCalculator())
        .thenReturn(mock(MiningBeneficiaryCalculator.class));
    GasLimitCalculator gasLimitCalculator = mock(GasLimitCalculator.class);
    when(protocolSpec.getGasLimitCalculator()).thenReturn(gasLimitCalculator);
    when(gasLimitCalculator.nextGasLimit(anyLong(), anyLong(), anyLong())).thenReturn(1L);
    when(protocolSpec.getFeeMarket()).thenReturn(mock(FeeMarket.class));
    when(protocolSpec.getPreExecutionProcessor()).thenReturn(mock(PreExecutionProcessor.class));
    when(protocolSpec.getSlotDuration()).thenReturn(Duration.ofSeconds(12));
    when(gasLimitCalculator.computeExcessBlobGas(anyLong(), anyLong(), anyLong())).thenReturn(0L);
  }

  @Test
  public void shouldProcessWithValidWorldState() {

    when(worldStateArchive.getWorldState(withBlockHeaderAndNoUpdateNodeHead(blockHeader)))
        .thenReturn(Optional.of(mutableWorldState));

    List<BlockSimulationResult> results =
        blockSimulator.process(blockHeader, BlockSimulationParameter.EMPTY);
    assertNotNull(results);
    verify(worldStateArchive).getWorldState(withBlockHeaderAndNoUpdateNodeHead(blockHeader));
  }

  @Test
  public void shouldNotProcessWithInvalidWorldState() {
    when(worldStateArchive.getWorldState(any(WorldStateQueryParams.class)))
        .thenAnswer(invocation -> Optional.empty());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> blockSimulator.process(blockHeader, BlockSimulationParameter.EMPTY));

    assertEquals(
        String.format("Public world state not available for block %s", blockHeader.toLogString()),
        exception.getMessage());
  }

  @Test
  public void shouldStopWhenTransactionSimulationIsInvalid() {

    when(worldStateArchive.getWorldState(withBlockHeaderAndNoUpdateNodeHead(blockHeader)))
        .thenReturn(Optional.of(mutableWorldState));
    when(mutableWorldState.updater()).thenReturn(updater);

    CallParameter callParameter = mock(CallParameter.class);
    BlockStateCall blockStateCall = new BlockStateCall(List.of(callParameter), null, null);

    TransactionSimulatorResult transactionSimulatorResult = mock(TransactionSimulatorResult.class);
    when(transactionSimulatorResult.isInvalid()).thenReturn(true);
    when(transactionSimulatorResult.getInvalidReason())
        .thenReturn(Optional.of("Invalid Transaction"));

    when(transactionSimulator.processWithWorldUpdater(
            any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any(), any(), any(),
            any()))
        .thenReturn(Optional.of(transactionSimulatorResult));

    BlockStateCallException exception =
        assertThrows(
            BlockStateCallException.class,
            () ->
                blockSimulator.process(
                    blockHeader, createSimulationParameter(blockStateCall), mutableWorldState));

    assertEquals("Transaction simulator result is invalid", exception.getMessage());
  }

  @Test
  public void shouldStopWhenTransactionSimulationIsEmpty() {

    when(worldStateArchive.getWorldState(withBlockHeaderAndNoUpdateNodeHead(blockHeader)))
        .thenReturn(Optional.of(mutableWorldState));
    when(mutableWorldState.updater()).thenReturn(updater);

    CallParameter callParameter = mock(CallParameter.class);
    BlockStateCall blockStateCall = new BlockStateCall(List.of(callParameter), null, null);

    when(transactionSimulator.processWithWorldUpdater(
            any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any(), any(), any(),
            any()))
        .thenReturn(Optional.empty());

    BlockStateCallException exception =
        assertThrows(
            BlockStateCallException.class,
            () ->
                blockSimulator.process(
                    blockHeader, createSimulationParameter(blockStateCall), mutableWorldState));

    assertEquals("Transaction simulator result is empty", exception.getMessage());
  }

  @Test
  public void shouldApplyStateOverridesCorrectly() {
    StateOverrideMap stateOverrideMap = new StateOverrideMap();
    Address address = mock(Address.class);
    StateOverride stateOverride =
        new StateOverride.Builder()
            .withBalance(Wei.of(456L))
            .withNonce(new UnsignedLongParameter(123L))
            .withCode("")
            .withStateDiff(Map.of("0x0", "0x1"))
            .build();

    stateOverrideMap.put(address, stateOverride);

    WorldUpdater worldUpdater = mock(WorldUpdater.class);
    when(mutableWorldState.updater()).thenReturn(worldUpdater);

    MutableAccount mutableAccount = mock(MutableAccount.class);
    when(mutableAccount.getAddress()).thenReturn(address);
    when(worldUpdater.getOrCreate(address)).thenReturn(mutableAccount);

    blockSimulator.applyStateOverrides(stateOverrideMap, mutableWorldState);

    verify(mutableAccount).setNonce(anyLong());
    verify(mutableAccount).setBalance(any(Wei.class));
    verify(mutableAccount).setCode(any(Bytes.class));
    verify(mutableAccount).setStorageValue(any(UInt256.class), any(UInt256.class));
  }

  @Test
  public void shouldOverrideBlockHeaderCorrectly() {

    var expectedTimestamp = 1L;
    var expectedBlockNumber = 2L;
    var expectedFeeRecipient = Address.fromHexString("0x1");
    var expectedBaseFeePerGas = Wei.of(7L);
    var expectedGasLimit = 5L;
    var expectedDifficulty = BigInteger.ONE;
    var expectedMixHashOrPrevRandao = Hash.hash(Bytes.fromHexString("0x01"));
    var expectedPrevRandao = Hash.hash(Bytes.fromHexString("0x01"));
    var expectedParentBeaconBlockRoot = Hash.hash(Bytes.fromHexString("0x03"));
    var expectedExtraData = Bytes.fromHexString("0x02");

    BlockOverrides blockOverrides =
        BlockOverrides.builder()
            .timestamp(expectedTimestamp)
            .blockNumber(expectedBlockNumber)
            .feeRecipient(expectedFeeRecipient)
            .baseFeePerGas(expectedBaseFeePerGas)
            .gasLimit(expectedGasLimit)
            .difficulty(expectedDifficulty)
            .mixHashOrPrevRandao(expectedMixHashOrPrevRandao)
            .extraData(expectedExtraData)
            .parentBeaconBlockRoot(expectedParentBeaconBlockRoot)
            .build();

    BlockHeader result =
        blockSimulator.overrideBlockHeader(blockHeader, protocolSpec, blockOverrides, true);

    assertNotNull(result);
    assertEquals(expectedTimestamp, result.getTimestamp());
    assertEquals(expectedBlockNumber, result.getNumber());
    assertEquals(expectedFeeRecipient, result.getCoinbase());
    assertEquals(Optional.of(expectedBaseFeePerGas), result.getBaseFee());
    assertEquals(expectedGasLimit, result.getGasLimit());
    assertThat(result.getDifficulty()).isEqualTo(Difficulty.of(expectedDifficulty));
    assertEquals(expectedMixHashOrPrevRandao, result.getMixHash());
    assertEquals(expectedPrevRandao, result.getPrevRandao().get());
    assertEquals(expectedExtraData, result.getExtraData());
  }

  private BlockSimulationParameter createSimulationParameter(final BlockStateCall blockStateCall) {
    return new BlockSimulationParameter.BlockSimulationParameterBuilder()
        .blockStateCalls(List.of(blockStateCall))
        .build();
  }
}

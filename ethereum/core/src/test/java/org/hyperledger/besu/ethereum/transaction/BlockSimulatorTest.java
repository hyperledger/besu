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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverride;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.BlockOverrides;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
  private BlockHeader blockHeader;

  private BlockSimulator blockSimulator;

  @BeforeEach
  public void setUp() {
    blockSimulator =
        new BlockSimulator(
            worldStateArchive, protocolSchedule, transactionSimulator, miningConfiguration);
    blockHeader = BlockHeaderBuilder.createDefault().buildBlockHeader();
    ProtocolSpec protocolSpec = mock(ProtocolSpec.class);
    when(miningConfiguration.getCoinbase())
        .thenReturn(Optional.ofNullable(Address.fromHexString("0x1")));
    when(protocolSchedule.getForNextBlockHeader(any(), anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getMiningBeneficiaryCalculator())
        .thenReturn(mock(MiningBeneficiaryCalculator.class));
    GasLimitCalculator gasLimitCalculator = mock(GasLimitCalculator.class);
    when(protocolSpec.getGasLimitCalculator()).thenReturn(gasLimitCalculator);
    when(gasLimitCalculator.nextGasLimit(anyLong(), anyLong(), anyLong())).thenReturn(1L);
    when(protocolSpec.getFeeMarket()).thenReturn(mock(FeeMarket.class));
  }

  @Test
  public void shouldProcessWithValidWorldState() {
    when(worldStateArchive.getMutable(any(BlockHeader.class), eq(false)))
        .thenReturn(Optional.of(mutableWorldState));

    List<BlockSimulationResult> results =
        blockSimulator.process(blockHeader, Collections.emptyList());

    assertNotNull(results);
    verify(worldStateArchive).getMutable(any(BlockHeader.class), eq(false));
  }

  @Test
  public void shouldNotProcessWithInvalidWorldState() {
    when(worldStateArchive.getMutable(any(BlockHeader.class), eq(false)))
        .thenReturn(Optional.empty());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> blockSimulator.process(blockHeader, Collections.emptyList()));

    assertEquals(
        String.format("Public world state not available for block %s", blockHeader.toLogString()),
        exception.getMessage());
  }

  @Test
  public void shouldStopWhenTransactionSimulationIsInvalid() {

    CallParameter callParameter = mock(CallParameter.class);
    BlockStateCall blockStateCall = new BlockStateCall(List.of(callParameter), null, null, true);

    TransactionSimulatorResult transactionSimulatorResult = mock(TransactionSimulatorResult.class);
    when(transactionSimulatorResult.isInvalid()).thenReturn(true);
    when(transactionSimulatorResult.getInvalidReason())
        .thenReturn(Optional.of("Invalid Transaction"));

    when(transactionSimulator.processWithWorldUpdater(
            any(), any(), any(), any(), any(), any(), any(MiningBeneficiaryCalculator.class)))
        .thenReturn(Optional.of(transactionSimulatorResult));

    BlockSimulationException exception =
        assertThrows(
            BlockSimulationException.class,
            () -> blockSimulator.process(blockHeader, List.of(blockStateCall), mutableWorldState));

    assertEquals(
        "Transaction simulator result is invalid: Invalid Transaction", exception.getMessage());
  }

  @Test
  public void shouldStopWhenTransactionSimulationIsEmpty() {

    CallParameter callParameter = mock(CallParameter.class);
    BlockStateCall blockStateCall = new BlockStateCall(List.of(callParameter), null, null, true);

    when(transactionSimulator.processWithWorldUpdater(
            any(), any(), any(), any(), any(), any(), any(MiningBeneficiaryCalculator.class)))
        .thenReturn(Optional.empty());

    BlockSimulationException exception =
        assertThrows(
            BlockSimulationException.class,
            () -> blockSimulator.process(blockHeader, List.of(blockStateCall), mutableWorldState));

    assertEquals("Transaction simulator result is empty", exception.getMessage());
  }

  @Test
  public void shouldApplyStateOverridesCorrectly() {
    StateOverrideMap stateOverrideMap = mock(StateOverrideMap.class);
    Address address = mock(Address.class);
    StateOverride stateOverride = mock(StateOverride.class);
    MutableAccount mutableAccount = mock(MutableAccount.class);

    when(stateOverrideMap.keySet()).thenReturn(Set.of(address));
    when(stateOverrideMap.get(address)).thenReturn(stateOverride);

    WorldUpdater worldUpdater = mock(WorldUpdater.class);
    when(mutableWorldState.updater()).thenReturn(worldUpdater);

    when(worldUpdater.getOrCreate(address)).thenReturn(mutableAccount);

    when(stateOverride.getNonce()).thenReturn(Optional.of(123L));
    when(stateOverride.getBalance()).thenReturn(Optional.of(Wei.of(456L)));
    when(stateOverride.getCode()).thenReturn(Optional.of(""));
    when(stateOverride.getStateDiff()).thenReturn(Optional.of(new HashMap<>(Map.of("0x0", "0x1"))));

    blockSimulator.applyStateOverrides(stateOverrideMap, mutableWorldState);

    verify(mutableAccount).setNonce(anyLong());
    verify(mutableAccount).setBalance(any(Wei.class));
    verify(mutableAccount).setCode(any(Bytes.class));
    verify(mutableAccount).setStorageValue(any(UInt256.class), any(UInt256.class));
  }

  @Test
  public void shouldApplyBlockHeaderOverridesCorrectly() {
    ProtocolSpec protocolSpec = mock(ProtocolSpec.class);

    var expectedTimestamp = 1L;
    var expectedBlockNumber = 2L;
    var expectedFeeRecipient = Address.fromHexString("0x1");
    var expectedBaseFeePerGas = Wei.of(7L);
    var expectedGasLimit = 5L;
    var expectedDifficulty = BigInteger.ONE;
    var expectedMixHashOrPrevRandao = Hash.hash(Bytes.fromHexString("0x01"));
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
            .build();

    BlockHeader result =
        blockSimulator.applyBlockHeaderOverrides(blockHeader, protocolSpec, blockOverrides);

    assertNotNull(result);
    assertEquals(expectedTimestamp, result.getTimestamp());
    assertEquals(expectedBlockNumber, result.getNumber());
    assertEquals(expectedFeeRecipient, result.getCoinbase());
    assertEquals(Optional.of(expectedBaseFeePerGas), result.getBaseFee());
    assertEquals(expectedGasLimit, result.getGasLimit());
    assertThat(result.getDifficulty()).isEqualTo(Difficulty.of(expectedDifficulty));
    assertEquals(expectedMixHashOrPrevRandao, result.getMixHash());
    assertEquals(expectedExtraData, result.getExtraData());
  }

  @Test
  public void testBuildTransactionValidationParams() {
    var configWhenValidate =
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.processingBlock())
            .build();

    ImmutableTransactionValidationParams params =
        blockSimulator.buildTransactionValidationParams(true);
    assertThat(params).isEqualTo(configWhenValidate);
    assertThat(params.isAllowExceedingBalance()).isFalse();

    params = blockSimulator.buildTransactionValidationParams(false);
    assertThat(params.isAllowExceedingBalance()).isTrue();
  }
}

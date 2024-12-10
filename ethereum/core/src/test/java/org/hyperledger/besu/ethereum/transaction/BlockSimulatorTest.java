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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.internal.Diff;
import org.hyperledger.besu.datatypes.AccountOverride;
import org.hyperledger.besu.datatypes.AccountOverrideMap;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlockOverrides;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.Arrays;
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

@SuppressWarnings("AlmostJavadoc")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BlockSimulatorTest {

  @Mock private WorldStateArchive worldStateArchive;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private TransactionSimulator transactionSimulator;
  @Mock private MiningConfiguration miningConfiguration;
  @Mock private MutableWorldState mutableWorldState;
  @Mock private BlockHeader blockHeader;

  private BlockSimulator blockSimulator;

  @BeforeEach
  public void setUp() {
    blockSimulator =
        new BlockSimulator(
            worldStateArchive, protocolSchedule, transactionSimulator, miningConfiguration);
    blockHeader = BlockHeaderBuilder.createDefault().buildBlockHeader();
    ProtocolSpec protocolSpec = mock(ProtocolSpec.class);
    when(miningConfiguration.getCoinbase()).thenReturn(Optional.ofNullable(Address.fromHexString("0x1")));
    when(protocolSchedule.getForNextBlockHeader(any(), anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(mock(MiningBeneficiaryCalculator.class));
    GasLimitCalculator gasLimitCalculator = mock(GasLimitCalculator.class);
    when(protocolSpec.getGasLimitCalculator()).thenReturn(gasLimitCalculator);
    when(gasLimitCalculator.nextGasLimit( anyLong(),  anyLong(), anyLong())).thenReturn(1L);
    when(protocolSpec.getFeeMarket()).thenReturn(mock(FeeMarket.class));
  }

  @Test
  public void testProcessWithValidWorldState() {
    when(worldStateArchive.getMutable(any(BlockHeader.class), eq(false)))
        .thenReturn(Optional.of(mutableWorldState));

    List<BlockSimulationResult> results =
        blockSimulator.process(blockHeader, Collections.emptyList());

    assertNotNull(results);
    verify(worldStateArchive).getMutable(any(BlockHeader.class), eq(false));
  }

  @Test
  public void testProcessWithInvalidWorldState() {
    when(worldStateArchive.getMutable(any(BlockHeader.class), eq(false)))
        .thenReturn(Optional.empty());
    when(blockHeader.toLogString()).thenReturn("1");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> blockSimulator.process(blockHeader, Collections.emptyList()));

    assertEquals("Public world state not available for block 1", exception.getMessage());
  }


  @Test
  public void shouldStopBlockSimulationWhenTransactionSimulationIsInvalid() {

    CallParameter callParameter = mock(CallParameter.class);
    BlockStateCall blockStateCall = new BlockStateCall(List.of(callParameter), null, null, true);

    TransactionSimulatorResult transactionSimulatorResult = mock(TransactionSimulatorResult.class);

    TransactionProcessingResult transactionProcessingResult = mock(TransactionProcessingResult.class);
    when(transactionSimulatorResult.result()).thenReturn(transactionProcessingResult);
    when(transactionProcessingResult.isInvalid()).thenReturn(true);
    when(transactionSimulatorResult.getValidationResult()).thenReturn(ValidationResult.invalid());

    when(worldStateArchive.getMutable(any(BlockHeader.class), eq(false)))
        .thenReturn(Optional.of(mutableWorldState));
    when(transactionSimulator.processWithWorldUpdater(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(MiningBeneficiaryCalculator.class)))
      .thenReturn(Optional.of(transactionSimulatorResult));

    when(transactionSimulator.processWithWorldUpdater(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(MiningBeneficiaryCalculator.class)))
      .thenReturn(Optional.of(transactionSimulatorResult));

    BlockSimulationException exception =
      assertThrows(
        BlockSimulationException.class,
        () ->
          blockSimulator.process(blockHeader, List.of(blockStateCall), mutableWorldState)
        );

    assertEquals("Public world state not available for block 1", exception.getMessage());
  }


  @Test
  public void testApplyStateOverrides() {
    AccountOverrideMap accountOverrideMap = mock(AccountOverrideMap.class);
    Address address = mock(Address.class);
    AccountOverride accountOverride = mock(AccountOverride.class);
    MutableAccount mutableAccount = mock(MutableAccount.class);

    when(accountOverrideMap.keySet()).thenReturn(Set.of(address));
    when(accountOverrideMap.get(address)).thenReturn(accountOverride);

    WorldUpdater worldUpdater = mock(WorldUpdater.class);
    when(mutableWorldState.updater()).thenReturn(worldUpdater);

    when(worldUpdater.getOrCreate(address)).thenReturn(mutableAccount);

    when(accountOverride.getNonce()).thenReturn(Optional.of(123L));
    when(accountOverride.getBalance()).thenReturn(Optional.of(Wei.of(456L)));
    when(accountOverride.getCode()).thenReturn(Optional.of(""));
    when(accountOverride.getStateDiff())
        .thenReturn(Optional.of(new HashMap<>(Map.of("0x0", "0x1"))));

    blockSimulator.applyStateOverrides(accountOverrideMap, mutableWorldState);

    verify(mutableAccount).setNonce(anyLong());
    verify(mutableAccount).setBalance(any(Wei.class));
    verify(mutableAccount).setCode(any(Bytes.class));
    verify(mutableAccount).setStorageValue(any(UInt256.class), any(UInt256.class));
  }

  @Test
  public void testApplyBlockHeaderOverrides() {
    BlockOverrides blockOverrides = mock(BlockOverrides.class);
    ProtocolSpec protocolSpec = mock(ProtocolSpec.class);

    var expectedTimestamp = Optional.of(1L);
    var expectedBlockNumber = Optional.of(2L);
    var expectedFeeRecipient = Optional.of(Address.fromHexString("0x1"));
    var expectedBaseFeePerGas = Optional.of(Wei.of(7L));
    var expectedGasLimit = Optional.of(5L);
    var expectedDifficulty = Optional.of(BigInteger.ONE);
    var expectedMixHashOrPrevRandao = Optional.of(Hash.hash( Bytes.fromHexString("0x01")));
    var expectedExtraData = Optional.of(Bytes.fromHexString("0x02"));

    when(blockOverrides.getTimestamp()).thenReturn(expectedTimestamp);
    when(blockOverrides.getBlockNumber()).thenReturn(expectedBlockNumber);
    when(blockOverrides.getFeeRecipient()).thenReturn(expectedFeeRecipient);
    when(blockOverrides.getBaseFeePerGas()).thenReturn(expectedBaseFeePerGas);
    when(blockOverrides.getGasLimit()).thenReturn(expectedGasLimit);
    when(blockOverrides.getDifficulty()).thenReturn(expectedDifficulty);
    when(blockOverrides.getMixHashOrPrevRandao()).thenReturn(expectedMixHashOrPrevRandao);
    when(blockOverrides.getExtraData()).thenReturn(expectedExtraData);

    BlockHeader result =
        blockSimulator.applyBlockHeaderOverrides(blockHeader, protocolSpec, blockOverrides);

    assertNotNull(result);
    assertEquals(expectedTimestamp.get(), result.getTimestamp());
    assertEquals(expectedBlockNumber.get(), result.getNumber());
    assertEquals(expectedFeeRecipient.get(), result.getCoinbase());
    assertEquals(expectedBaseFeePerGas, result.getBaseFee());
    assertEquals(expectedGasLimit.get(), result.getGasLimit());
    assertThat(result.getDifficulty()).isEqualTo(Difficulty.of(expectedDifficulty.get()));
    assertEquals(expectedMixHashOrPrevRandao.get(), result.getMixHash());
    assertEquals(expectedExtraData.get(), result.getExtraData());
  }

  /* @Test
  public void testProcessTransactions() {

    BlockStateCall blockStateCall = mock(BlockStateCall.class);
    CallParameter callParameter = mock(CallParameter.class);
    TransactionSimulatorResult transactionSimulatorResult = mock(TransactionSimulatorResult.class);
    TransactionProcessingResult transactionProcessingResult = mock(TransactionProcessingResult.class);
    MiningBeneficiaryCalculator miningBeneficiaryCalculator = mock(MiningBeneficiaryCalculator.class);
    WorldUpdater worldUpdater = mock(WorldUpdater.class);

    when(blockStateCall.getCalls()).thenReturn(List.of(callParameter));
    when(transactionSimulator.processWithWorldUpdater(
      eq(callParameter),
      eq(Optional.empty()),
      any(TransactionValidationParams.class),
      eq(OperationTracer.NO_TRACING),
      eq(blockHeader),
      any(WorldUpdater.class),
      eq(miningBeneficiaryCalculator)))
      .thenReturn(Optional.of(transactionSimulatorResult));
    when(transactionSimulatorResult.result()).thenReturn(transactionProcessingResult);
    when(transactionProcessingResult.isInvalid()).thenReturn(false);
    when(mutableWorldState.updater()).thenReturn(worldUpdater);

    List<TransactionSimulatorResult> results = blockSimulator.processTransactions(
      blockHeader, blockStateCall, mutableWorldState, miningBeneficiaryCalculator);

    assertNotNull(results);
    assertEquals(1, results.size());
    verify(worldUpdater).commit();
  }*/


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

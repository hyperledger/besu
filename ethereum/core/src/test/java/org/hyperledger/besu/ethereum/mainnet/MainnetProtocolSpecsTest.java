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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.config.BlobScheduleOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MainnetProtocolSpecsTest {

  @Mock(lenient = true)
  private GenesisConfigOptions genesisConfigOptions;

  @Mock(lenient = true)
  private MiningConfiguration miningConfiguration;

  @Mock(lenient = true)
  private BlobSchedule pragueBlobSchedule;

  @Mock(lenient = true)
  private BlobScheduleOptions blobScheduleOptions;

  private final EvmConfiguration evmConfiguration = EvmConfiguration.DEFAULT;
  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final Optional<BigInteger> chainId = Optional.of(BigInteger.ONE);
  private final boolean enableRevertReason = false;
  private final boolean isParallelTxProcessingEnabled = false;
  private final boolean isBlockAccessListEnabled = false;
  private final long londonForkBlockNumber = 0L;

  @BeforeEach
  public void setUp() {
    when(genesisConfigOptions.getLondonBlockNumber())
        .thenReturn(OptionalLong.of(londonForkBlockNumber));
    when(genesisConfigOptions.getBlobScheduleOptions())
        .thenReturn(Optional.of(blobScheduleOptions));
    when(blobScheduleOptions.getPrague()).thenReturn(Optional.of(pragueBlobSchedule));
    when(pragueBlobSchedule.getTarget()).thenReturn(6);
    when(pragueBlobSchedule.getMax()).thenReturn(9);
    when(pragueBlobSchedule.getBaseFeeUpdateFraction()).thenReturn(1234);
  }

  @Test
  public void pragueDefinitionShouldThrowExceptionWhenAllContractAddressesAreMissing() {
    // Given
    when(genesisConfigOptions.getDepositContractAddress()).thenReturn(Optional.empty());
    when(genesisConfigOptions.getConsolidationRequestContractAddress())
        .thenReturn(Optional.empty());
    when(genesisConfigOptions.getWithdrawalRequestContractAddress()).thenReturn(Optional.empty());

    // When/Then
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(
            () ->
                MainnetProtocolSpecs.pragueDefinition(
                    chainId,
                    enableRevertReason,
                    genesisConfigOptions,
                    evmConfiguration,
                    miningConfiguration,
                    isParallelTxProcessingEnabled,
                    isBlockAccessListEnabled,
                    metricsSystem))
        .withMessageContaining("Withdrawal Request Contract Address not found");
  }

  @Test
  public void pragueDefinitionShouldThrowExceptionWhenWithdrawalRequestContractAddressIsMissing() {
    // Given
    when(genesisConfigOptions.getDepositContractAddress()).thenReturn(Optional.of(Address.ZERO));
    when(genesisConfigOptions.getConsolidationRequestContractAddress())
        .thenReturn(Optional.of(Address.ZERO));
    when(genesisConfigOptions.getWithdrawalRequestContractAddress()).thenReturn(Optional.empty());

    // When/Then
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(
            () ->
                MainnetProtocolSpecs.pragueDefinition(
                    chainId,
                    enableRevertReason,
                    genesisConfigOptions,
                    evmConfiguration,
                    miningConfiguration,
                    isParallelTxProcessingEnabled,
                    isBlockAccessListEnabled,
                    metricsSystem))
        .withMessageContaining("Withdrawal Request Contract Address not found");
  }

  @Test
  public void pragueDefinitionShouldThrowExceptionWhenDepositContractAddressIsMissing() {
    // Given
    when(genesisConfigOptions.getDepositContractAddress()).thenReturn(Optional.empty());
    when(genesisConfigOptions.getConsolidationRequestContractAddress())
        .thenReturn(Optional.of(Address.ZERO));
    when(genesisConfigOptions.getWithdrawalRequestContractAddress())
        .thenReturn(Optional.of(Address.ZERO));

    // When/Then
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(
            () ->
                MainnetProtocolSpecs.pragueDefinition(
                    chainId,
                    enableRevertReason,
                    genesisConfigOptions,
                    evmConfiguration,
                    miningConfiguration,
                    isParallelTxProcessingEnabled,
                    isBlockAccessListEnabled,
                    metricsSystem))
        .withMessageContaining("Deposit Contract Address not found");
  }

  @Test
  public void
      pragueDefinitionShouldThrowExceptionWhenConsolidationRequestContractAddressIsMissing() {
    // Given
    when(genesisConfigOptions.getDepositContractAddress()).thenReturn(Optional.of(Address.ZERO));
    when(genesisConfigOptions.getConsolidationRequestContractAddress())
        .thenReturn(Optional.empty());
    when(genesisConfigOptions.getWithdrawalRequestContractAddress())
        .thenReturn(Optional.of(Address.ZERO));

    // When/Then
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(
            () ->
                MainnetProtocolSpecs.pragueDefinition(
                    chainId,
                    enableRevertReason,
                    genesisConfigOptions,
                    evmConfiguration,
                    miningConfiguration,
                    isParallelTxProcessingEnabled,
                    isBlockAccessListEnabled,
                    metricsSystem))
        .withMessageContaining("Consolidation Request Contract Address not found");
  }
}

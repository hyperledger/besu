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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StateOverride;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SimulationTransactionProcessorFactoryTest {

  private SimulationTransactionProcessorFactory factory;
  private final Address originalPrecompileAddress = Address.fromHexString("0x1");
  private final Address newPrecompileAddress = Address.fromHexString("0x2");

  @BeforeEach
  void setUp() {
    ProtocolSpec protocolSpec = mock(ProtocolSpec.class);
    ProtocolSchedule protocolSchedule = mock(ProtocolSchedule.class);
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);

    PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    precompileContractRegistry.put(originalPrecompileAddress, mock(PrecompiledContract.class));

    MainnetTransactionProcessor mainnetTransactionProcessor =
        MainnetTransactionProcessor.builder()
            .messageCallProcessor(
                new MessageCallProcessor(mock(EVM.class), precompileContractRegistry))
            .build();
    when(protocolSpec.getTransactionProcessor()).thenReturn(mainnetTransactionProcessor);

    factory = new SimulationTransactionProcessorFactory(protocolSchedule);
  }

  @Test
  void shouldReturnProcessorWithOriginalPrecompileAddressesIfNoOverrides() {
    MainnetTransactionProcessor simulationTransactionProcessor =
        factory.getTransactionProcessor(null, Optional.empty());
    Set<Address> precompileAddresses =
        simulationTransactionProcessor.getMessageCallProcessor().getPrecompileAddresses();

    assertThat(precompileAddresses).containsExactlyInAnyOrder(originalPrecompileAddress);
  }

  @Test
  void shouldReturnProcessorWithNewPrecompileAddressesWithOverrides() {
    StateOverrideMap stateOverrideMap = new StateOverrideMap();
    stateOverrideMap.put(
        originalPrecompileAddress,
        new StateOverride.Builder().withMovePrecompileToAddress(newPrecompileAddress).build());

    MainnetTransactionProcessor simulationTransactionProcessor =
        factory.getTransactionProcessor(null, Optional.of(stateOverrideMap));
    Set<Address> precompileAddresses =
        simulationTransactionProcessor.getMessageCallProcessor().getPrecompileAddresses();

    assertThat(precompileAddresses).containsExactlyInAnyOrder(newPrecompileAddress);
  }
}

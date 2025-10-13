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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.processor.OverriddenPrecompilesMessageCallProcessor;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SimulationTransactionProcessorFactory {

  private final ProtocolSchedule protocolSchedule;

  /**
   * Creates a factory capable of producing transaction processors.
   *
   * @param protocolSchedule the protocol schedule used for creating processors
   */
  public SimulationTransactionProcessorFactory(final ProtocolSchedule protocolSchedule) {
    this.protocolSchedule = protocolSchedule;
  }

  /**
   * Creates a transaction processor, optionally applying state overrides.
   *
   * @param processableHeader the block header to process transactions against
   * @param maybeStateOverrides optional state overrides for simulation
   * @return a transaction processor, with overrides applied if provided
   */
  public MainnetTransactionProcessor getTransactionProcessor(
      final ProcessableBlockHeader processableHeader,
      final Optional<StateOverrideMap> maybeStateOverrides) {

    MainnetTransactionProcessor baseProcessor =
        protocolSchedule.getByBlockHeader(processableHeader).getTransactionProcessor();

    return maybeStateOverrides
        .flatMap(this::extractPrecompileAddressOverrides)
        .map(
            precompileOverrides -> createProcessorWithOverrides(baseProcessor, precompileOverrides))
        .orElse(baseProcessor);
  }

  private Optional<Map<Address, Address>> extractPrecompileAddressOverrides(
      final StateOverrideMap stateOverrides) {
    Map<Address, Address> addressOverrides =
        stateOverrides.entrySet().stream()
            .filter(entry -> entry.getValue().getMovePrecompileToAddress().isPresent())
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getMovePrecompileToAddress().get()));

    return addressOverrides.isEmpty() ? Optional.empty() : Optional.of(addressOverrides);
  }

  private MainnetTransactionProcessor createProcessorWithOverrides(
      final MainnetTransactionProcessor baseProcessor,
      final Map<Address, Address> precompileAddressOverrides) {
    return MainnetTransactionProcessor.builder()
        .populateFrom(baseProcessor)
        .messageCallProcessor(
            new OverriddenPrecompilesMessageCallProcessor(
                baseProcessor.getMessageCallProcessor(), precompileAddressOverrides))
        .build();
  }
}

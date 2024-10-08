/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.ForkchoiceEvent;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotSelectorFromSafeBlock extends PivotSelectorFromBlock {
  private static final Logger LOG = LoggerFactory.getLogger(PivotSelectorFromSafeBlock.class);

  public PivotSelectorFromSafeBlock(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final GenesisConfigOptions genesisConfig,
      final Supplier<Optional<ForkchoiceEvent>> forkchoiceStateSupplier,
      final Runnable cleanupAction) {
    super(
        protocolContext,
        protocolSchedule,
        ethContext,
        metricsSystem,
        genesisConfig,
        forkchoiceStateSupplier,
        cleanupAction);
  }

  @Override
  protected Optional<Hash> getPivotHash(final ForkchoiceEvent forkchoiceEvent) {
    if (forkchoiceEvent.hasValidSafeBlockHash()) {
      Hash hash = forkchoiceEvent.getSafeBlockHash();
      LOG.debug("Returning safe block hash {} as pivot.", hash);
      return Optional.of(hash);
    } else {
      LOG.debug("No safe block hash found.");
      return Optional.empty();
    }
  }
}

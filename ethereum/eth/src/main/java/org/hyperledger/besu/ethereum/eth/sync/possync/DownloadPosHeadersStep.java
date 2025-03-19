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
package org.hyperledger.besu.ethereum.eth.sync.possync;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.tasks.DownloadHeaderSequenceTask;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadPosHeadersStep
    implements Function<SyncTargetNumberRange, CompletableFuture<List<BlockHeader>>> {
  private static final Logger LOG = LoggerFactory.getLogger(DownloadPosHeadersStep.class);
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final ValidationPolicy validationPolicy;
  private final SynchronizerConfiguration synchronizerConfiguration;
  private final MetricsSystem metricsSystem;

  public DownloadPosHeadersStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final ValidationPolicy validationPolicy,
      final SynchronizerConfiguration synchronizerConfiguration,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.validationPolicy = validationPolicy;
    this.synchronizerConfiguration = synchronizerConfiguration;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final SyncTargetNumberRange syncRange) {
    LOG.debug(
        "Downloading headers for range {} to {}",
        syncRange.lowerBlockNumber(),
        syncRange.upperBlockNumber());
    if (syncRange.getSegmentLengthExclusive() == 0) {
      // There are no extra headers to download.
      return completedFuture(emptyList());
    }
    return DownloadHeaderSequenceTask.endingAtBlockNumber(
            protocolSchedule,
            protocolContext,
            ethContext,
            synchronizerConfiguration,
            syncRange.upperBlockNumber(),
            syncRange.getSegmentLengthExclusive(),
            validationPolicy,
            metricsSystem)
        .run()
        .thenApply(
            headers -> {
              if (!validateHeaderRange(syncRange, headers)) {
                final String errorMessage =
                    String.format(
                        "Invalid range headers.  Headers downloaded between #%d and #%d do not connect at #%d",
                        syncRange.lowerBlockNumber(),
                        syncRange.upperBlockNumber(),
                        syncRange.upperBlockNumber());
                throw InvalidBlockException.create(errorMessage);
              }
              return headers;
            });
  }

  private boolean validateHeaderRange(
      final SyncTargetNumberRange syncRange, final List<BlockHeader> headers) {
    if (!syncRange.firstRange()) {
      final BlockHeader parentHeader = headers.get(0);
      final BlockHeader firstHeader = headers.get(1);
      final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(firstHeader);
      final BlockHeaderValidator validator = protocolSpec.getBlockHeaderValidator();
      return validator.validateHeader(
          firstHeader,
          parentHeader,
          protocolContext,
          validationPolicy.getValidationModeForNextBlock());
    }
    return true;
  }
}

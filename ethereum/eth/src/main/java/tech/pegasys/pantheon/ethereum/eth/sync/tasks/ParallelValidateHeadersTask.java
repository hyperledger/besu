/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPipelinedPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.ValidationPolicy;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelValidateHeadersTask<C>
    extends AbstractPipelinedPeerTask<List<BlockHeader>, List<BlockHeader>> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final ValidationPolicy validationPolicy;

  ParallelValidateHeadersTask(
      final ValidationPolicy validationPolicy,
      final BlockingQueue<List<BlockHeader>> inboundQueue,
      final int outboundBacklogSize,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    super(inboundQueue, outboundBacklogSize, ethContext, ethTasksTimer);

    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.validationPolicy = validationPolicy;
  }

  @Override
  protected Optional<List<BlockHeader>> processStep(
      final List<BlockHeader> headers,
      final Optional<List<BlockHeader>> previousHeaders,
      final EthPeer peer) {
    LOG.debug(
        "Validating Headers {} to {}",
        headers.get(0).getNumber(),
        headers.get(headers.size() - 1).getNumber());

    final BlockHeader parentHeader = headers.get(0);
    final BlockHeader childHeader = headers.get(1);
    final ProtocolSpec<C> protocolSpec = protocolSchedule.getByBlockNumber(childHeader.getNumber());
    final BlockHeaderValidator<C> blockHeaderValidator = protocolSpec.getBlockHeaderValidator();
    if (blockHeaderValidator.validateHeader(
        childHeader,
        parentHeader,
        protocolContext,
        validationPolicy.getValidationModeForNextBlock())) {
      LOG.debug(
          "Validated Headers {} to {}",
          headers.get(0).getNumber(),
          headers.get(headers.size() - 1).getNumber());
      // The first header will be imported by the previous request range.
      return Optional.of(headers.subList(1, headers.size()));
    } else {
      LOG.debug(
          "Could not validate Headers {} to {}",
          headers.get(0).getNumber(),
          headers.get(headers.size() - 1).getNumber());
      // ignore the value, we only want the first exception to be there
      failExceptionally(
          new InvalidBlockException(
              "Provided first header does not connect to last header.",
              parentHeader.getNumber(),
              parentHeader.getHash()));
      return Optional.empty();
    }
  }
}

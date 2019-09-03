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
package tech.pegasys.pantheon.ethereum.eth.sync;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.CompleteBlocksTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DownloadBodiesStep<C>
    implements Function<List<BlockHeader>, CompletableFuture<List<Block>>> {

  private final ProtocolSchedule<C> protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  public DownloadBodiesStep(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<List<Block>> apply(final List<BlockHeader> blockHeaders) {
    return CompleteBlocksTask.forHeaders(protocolSchedule, ethContext, blockHeaders, metricsSystem)
        .run();
  }
}

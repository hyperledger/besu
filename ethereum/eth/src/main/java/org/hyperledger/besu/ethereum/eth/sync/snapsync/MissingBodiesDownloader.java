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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.chain.MissingBlockBodies;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads the bodies and receipts of the blocks a snap sync left for later, while the node
 * already follows the chain.
 *
 * <p>The blockchain records which blocks are still missing, see {@link
 * MutableBlockchain#getMissingBlockBodies()}, so the download continues where it was when the node
 * is restarted. A download that fails, for the lack of peers for instance, is started again after a
 * while with what is still missing.
 */
public class MissingBodiesDownloader {
  private static final Logger LOG = LoggerFactory.getLogger(MissingBodiesDownloader.class);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

  private final SnapSyncChainDownloadPipelineFactory pipelineFactory;
  private final MutableBlockchain blockchain;
  private final EthContext ethContext;

  private boolean running;
  private Pipeline<?> currentPipeline;

  public MissingBodiesDownloader(
      final SnapSyncChainDownloadPipelineFactory pipelineFactory,
      final MutableBlockchain blockchain,
      final EthContext ethContext) {
    this.pipelineFactory = pipelineFactory;
    this.blockchain = blockchain;
    this.ethContext = ethContext;
  }

  /** Starts the download if there are blocks without body and receipts and it is not running. */
  public synchronized void start() {
    final Optional<MissingBlockBodies> missingBlockBodies = blockchain.getMissingBlockBodies();
    if (running || missingBlockBodies.isEmpty()) {
      return;
    }
    running = true;
    LOG.info(
        "Downloading the bodies and receipts of the {} blocks from {} to {} in the background",
        missingBlockBodies.get().size(),
        missingBlockBodies.get().firstBlock(),
        missingBlockBodies.get().lastBlock());
    download();
  }

  /** Stops the download. What is stored is kept, and the download can be started again. */
  public synchronized void stop() {
    running = false;
    if (currentPipeline != null) {
      currentPipeline.abort();
      currentPipeline = null;
    }
  }

  private synchronized void download() {
    if (!running) {
      return;
    }
    final Optional<MissingBlockBodies> missingBlockBodies = blockchain.getMissingBlockBodies();
    if (missingBlockBodies.isEmpty()) {
      running = false;
      return;
    }
    if (ethContext.getEthPeers().peerCount() == 0) {
      retryLater();
      return;
    }
    // the block before the first missing one is the last block of the history that is there
    final Optional<Difficulty> totalDifficulty =
        blockchain
            .getBlockHeader(missingBlockBodies.get().firstBlock() - 1)
            .flatMap(header -> blockchain.getTotalDifficultyByHash(header.getHash()));
    if (totalDifficulty.isEmpty()) {
      LOG.error(
          "Cannot download the bodies and receipts from block {} on: the block before it has no total difficulty",
          missingBlockBodies.get().firstBlock());
      running = false;
      return;
    }

    final Pipeline<?> pipeline =
        pipelineFactory.createMissingBodiesDownloadPipeline(
            missingBlockBodies.get(), totalDifficulty.get());
    currentPipeline = pipeline;
    ethContext
        .getScheduler()
        .startPipeline(pipeline)
        .whenComplete(
            (result, error) -> {
              if (error == null) {
                // nothing is missing any more, which the next round finds out
                download();
              } else {
                LOG.debug("Download of block bodies and receipts failed, trying again", error);
                retryLater();
              }
            });
  }

  private void retryLater() {
    ethContext.getScheduler().scheduleFutureTask(this::download, RETRY_DELAY);
  }
}

/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;

import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckPointBlockImportStep implements Consumer<Optional<BlockWithReceipts>> {

  private static final Logger LOG = LoggerFactory.getLogger(CheckPointBlockImportStep.class);

  private final CheckPointSource checkPointSource;
  private final Checkpoint checkpoint;
  private final MutableBlockchain blockchain;

  public CheckPointBlockImportStep(
      final CheckPointSource checkPointSource,
      final Checkpoint checkpoint,
      final MutableBlockchain blockchain) {
    this.checkPointSource = checkPointSource;
    this.checkpoint = checkpoint;
    this.blockchain = blockchain;
  }

  @Override
  public void accept(final Optional<BlockWithReceipts> maybeBlock) {
    maybeBlock.ifPresent(
        block -> {
          blockchain.unsafeImportBlock(
              block.getBlock(),
              block.getReceipts(),
              block.getHash().equals(checkpoint.blockHash())
                  ? Optional.of(checkpoint.totalDifficulty())
                  : Optional.empty());
          checkPointSource.setLastHeaderDownloaded(Optional.of(block.getHeader()));
          if (!checkPointSource.hasNext()) {
            blockchain.unsafeSetChainHead(
                checkPointSource.getCheckpoint(), checkpoint.totalDifficulty());
            LOG.info(
                "Checkpoint block {} with hash {} downloaded",
                checkPointSource.getCheckpoint().getNumber(),
                checkPointSource.getCheckpoint().getBlockHash());
          }
        });
    checkPointSource.notifyTaskAvailable();
  }
}

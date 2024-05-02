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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class CheckpointSource implements Iterator<Hash> {

  private final SyncState syncState;
  private final BlockHeader checkpoint;
  private final int nbBlocks;
  private Optional<BlockHeader> lastHeaderDownloaded;

  private final AtomicBoolean isDownloading = new AtomicBoolean(false);

  public CheckpointSource(
      final SyncState syncState, final BlockHeader blockHeader, final int nbBlocks) {
    this.syncState = syncState;
    this.checkpoint = blockHeader;
    this.nbBlocks = nbBlocks;
    this.lastHeaderDownloaded = Optional.empty();
  }

  @Override
  public boolean hasNext() {
    return syncState.getLocalChainHeight() == 0
        && lastHeaderDownloaded
            .map(blockHeader -> blockHeader.getNumber() > (checkpoint.getNumber() - nbBlocks))
            .orElse(true);
  }

  @Override
  public synchronized Hash next() {
    isDownloading.getAndSet(true);
    return lastHeaderDownloaded
        .map(ProcessableBlockHeader::getParentHash)
        .orElse(checkpoint.getHash());
  }

  public synchronized void notifyTaskAvailable() {
    isDownloading.getAndSet(false);
    notifyAll();
  }

  public BlockHeader getCheckpoint() {
    return checkpoint;
  }

  public void setLastHeaderDownloaded(final Optional<BlockHeader> lastHeaderDownloaded) {
    this.lastHeaderDownloaded = lastHeaderDownloaded;
  }
}

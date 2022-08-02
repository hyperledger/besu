/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.consensus.merge.ForkchoiceMessageListener;
import org.hyperledger.besu.consensus.merge.MergeStateHandler;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.StatusMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergePeerFilter implements MergeStateHandler, ForkchoiceMessageListener {

  private Optional<Difficulty> powTerminalDifficulty = Optional.of(Difficulty.MAX_VALUE);
  private final StampedLock powTerminalDifficultyLock = new StampedLock();
  private Hash lastFinalized = Hash.ZERO;
  private final AtomicLong numFinalizedSeen = new AtomicLong(0);
  private static final Logger LOG = LoggerFactory.getLogger(MergePeerFilter.class);

  public boolean disconnectIfPoW(final StatusMessage status, final EthPeer peer) {
    long lockStamp = this.powTerminalDifficultyLock.readLock();
    try {
      if (this.powTerminalDifficulty.isPresent()
          && status.totalDifficulty().greaterThan(this.powTerminalDifficulty.get())) {
        LOG.debug(
            "Disconnecting peer with difficulty {}, likely still on PoW chain",
            status.totalDifficulty());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
        return true;
      } else {
        return false;
      }
    } finally {
      this.powTerminalDifficultyLock.unlockRead(lockStamp);
    }
  }

  public boolean disconnectIfGossipingBlocks(final Message message, final EthPeer peer) {
    final int code = message.getData().getCode();
    if (isFinalized() && (code == EthPV62.NEW_BLOCK || code == EthPV62.NEW_BLOCK_HASHES)) {
      LOG.debug("disconnecting peer for sending new blocks after transition to PoS");
      peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      return true;
    } else {
      return false;
    }
  }

  private boolean isFinalized() {
    return this.numFinalizedSeen.get() > 1;
  }

  @Override
  public void onNewForkchoiceMessage(
      final Hash headBlockHash,
      final Optional<Hash> maybeFinalizedBlockHash,
      final Hash safeBlockHash) {
    if (maybeFinalizedBlockHash.isPresent()
        && !maybeFinalizedBlockHash.get().equals(this.lastFinalized)) {
      this.lastFinalized = maybeFinalizedBlockHash.get();
      this.numFinalizedSeen.getAndIncrement();
      LOG.debug("have seen {} finalized blocks", this.numFinalizedSeen);
    }
  }

  @Override
  public void mergeStateChanged(
      final boolean isPoS,
      final Optional<Boolean> oldState,
      final Optional<Difficulty> difficultyStoppedAt) {
    if (isPoS && difficultyStoppedAt.isPresent()) {
      LOG.debug("terminal difficulty set to {}", difficultyStoppedAt.get().getValue());
      long lockStamp = this.powTerminalDifficultyLock.writeLock();
      try {
        this.powTerminalDifficulty = difficultyStoppedAt;
      } finally {
        this.powTerminalDifficultyLock.unlockWrite(lockStamp);
      }
    }
  }
}

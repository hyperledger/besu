/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.uint.UInt256;

import com.google.common.base.MoreObjects;

public class ChainState {
  // The best block by total difficulty that we know about
  private final BestBlock bestBlock = new BestBlock();
  // The highest block that we've seen
  private volatile long estimatedHeight = 0L;
  private volatile boolean estimatedHeightKnown = false;

  private final Subscribers<EstimatedHeightListener> estimatedHeightListeners =
      Subscribers.create();

  public long addEstimatedHeightListener(final EstimatedHeightListener listener) {
    return estimatedHeightListeners.subscribe(listener);
  }

  public void removeEstimatedHeightListener(final long listenerId) {
    estimatedHeightListeners.unsubscribe(listenerId);
  }

  public boolean hasEstimatedHeight() {
    return estimatedHeightKnown;
  }

  public long getEstimatedHeight() {
    return estimatedHeight;
  }

  public UInt256 getEstimatedTotalDifficulty() {
    return bestBlock.getTotalDifficulty();
  }

  public BestBlock getBestBlock() {
    return bestBlock;
  }

  public void statusReceived(final Hash bestBlockHash, final UInt256 bestBlockTotalDifficulty) {
    synchronized (this) {
      bestBlock.totalDifficulty = bestBlockTotalDifficulty;
      bestBlock.hash = bestBlockHash;
    }
  }

  public void update(final Hash blockHash, final long blockNumber) {
    synchronized (this) {
      if (bestBlock.hash.equals(blockHash)) {
        bestBlock.number = blockNumber;
      }
      updateHeightEstimate(blockNumber);
    }
  }

  public void update(final BlockHeader header) {
    synchronized (this) {
      if (header.getHash().equals(bestBlock.hash)) {
        bestBlock.number = header.getNumber();
      }
      updateHeightEstimate(header.getNumber());
    }
  }

  public void updateForAnnouncedBlock(
      final BlockHeader blockHeader, final UInt256 totalDifficulty) {
    synchronized (this) {
      // Blocks are announced before they're imported so their chain head must be the parent
      final UInt256 parentTotalDifficulty = totalDifficulty.minus(blockHeader.getDifficulty());
      final long parentBlockNumber = blockHeader.getNumber() - 1;
      if (parentTotalDifficulty.compareTo(bestBlock.totalDifficulty) >= 0) {
        bestBlock.totalDifficulty = parentTotalDifficulty;
        bestBlock.hash = blockHeader.getParentHash();
        bestBlock.number = parentBlockNumber;
      }
      updateHeightEstimate(parentBlockNumber);
    }
  }

  public void updateHeightEstimate(final long blockNumber) {
    synchronized (this) {
      estimatedHeightKnown = true;
      if (blockNumber > estimatedHeight) {
        estimatedHeight = blockNumber;
        estimatedHeightListeners.forEach(e -> e.onEstimatedHeightChanged(estimatedHeight));
      }
    }
  }

  /**
   * Returns true if this chain state represents a chain that is "better" than the chain represented
   * by the supplied {@link ChainHead}. "Better" currently means that this chain is longer or
   * heavier than the supplied {@code chainToCheck}.
   *
   * @param chainToCheck The chain being compared.
   * @return true if this {@link ChainState} represents a better chain than {@code chainToCheck}.
   */
  public boolean chainIsBetterThan(final ChainHead chainToCheck) {
    return hasHigherDifficultyThan(chainToCheck) || hasLongerChainThan(chainToCheck);
  }

  private boolean hasHigherDifficultyThan(final ChainHead chainToCheck) {
    return bestBlock.getTotalDifficulty().compareTo(chainToCheck.getTotalDifficulty()) > 0;
  }

  private boolean hasLongerChainThan(final ChainHead chainToCheck) {
    return estimatedHeight > chainToCheck.getHeight();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("estimatedHeight", estimatedHeight)
        .add("bestBlock", bestBlock)
        .toString();
  }

  // Represent the best block by totalDifficulty
  public static class BestBlock {
    volatile long number = 0L;
    volatile Hash hash = null;
    volatile UInt256 totalDifficulty = UInt256.ZERO;

    public long getNumber() {
      return number;
    }

    public Hash getHash() {
      return hash;
    }

    public UInt256 getTotalDifficulty() {
      return totalDifficulty;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("totalDifficulty", totalDifficulty)
          .add("blockHash", hash)
          .add("number", number)
          .toString();
    }
  }

  @FunctionalInterface
  public interface EstimatedHeightListener {
    void onEstimatedHeightChanged(long estimatedHeight);
  }
}

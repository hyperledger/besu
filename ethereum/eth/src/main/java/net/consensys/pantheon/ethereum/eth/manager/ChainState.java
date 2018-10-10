package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.util.uint.UInt256;

import com.google.common.base.MoreObjects;

public class ChainState {
  // The best block by total difficulty that we know about
  private final BestBlock bestBlock = new BestBlock();
  // The highest block that we've seen
  private volatile long estimatedHeight = 0L;
  private volatile boolean estimatedHeightKnown = false;

  public boolean hasEstimatedHeight() {
    return estimatedHeightKnown;
  }

  public long getEstimatedHeight() {
    return estimatedHeight;
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
      if (bestBlock.hash.equals(header.getHash())) {
        bestBlock.number = header.getNumber();
      }
      updateHeightEstimate(header.getNumber());
    }
  }

  public void update(final BlockHeader blockHeader, final UInt256 totalDifficulty) {
    synchronized (this) {
      if (totalDifficulty.compareTo(bestBlock.totalDifficulty) >= 0) {
        bestBlock.totalDifficulty = totalDifficulty;
        bestBlock.hash = blockHeader.getHash();
        bestBlock.number = blockHeader.getNumber();
      }
      updateHeightEstimate(blockHeader.getNumber());
    }
  }

  private void updateHeightEstimate(final long blockNumber) {
    estimatedHeightKnown = true;
    if (blockNumber > estimatedHeight) {
      estimatedHeight = blockNumber;
    }
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
}

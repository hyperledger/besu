package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;

public class FlexibleBlockHashTerminalCondition implements FullSyncTerminationCondition {
  private Hash blockHash;
  private final Blockchain blockchain;

  public FlexibleBlockHashTerminalCondition(final Hash blockHash, final Blockchain blockchain) {
    this.blockHash = blockHash;
    this.blockchain = blockchain;
  }

  public synchronized void setBlockHash(final Hash blockHash) {
    this.blockHash = blockHash;
  }

  @Override
  public synchronized boolean getAsBoolean() {
    return blockchain.contains(blockHash);
  }
}

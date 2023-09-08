package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Context which holds information relevant to a bonsai archive storage query. */
public class BonsaiContext {

  private final AtomicReference<BlockHeader> blockHeader;

  public BonsaiContext() {
    blockHeader = new AtomicReference<>();
  }

  public BonsaiContext copy() {
    var newCtx = new BonsaiContext();
    Optional.ofNullable(blockHeader.get()).ifPresent(newCtx::setBlockHeader);
    return newCtx;
  }

  public BonsaiContext setBlockHeader(final BlockHeader blockHeader) {
    this.blockHeader.set(blockHeader);
    return this;
  }

  public Optional<BlockHeader> getBlockHeader() {
    return Optional.ofNullable(blockHeader.get());
  }
}

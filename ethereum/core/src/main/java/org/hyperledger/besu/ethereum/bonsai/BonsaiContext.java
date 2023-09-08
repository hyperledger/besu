package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.function.Supplier;

/** Context which holds information relevant to a bonsai archive storage query. */
public class BonsaiContext {

  private final Supplier<BlockHeader> blockHeaderSupplier;

  public BonsaiContext(Supplier<BlockHeader> blockHeaderSupplier) {
    this.blockHeaderSupplier = blockHeaderSupplier;
  }

  /**
   * returns an empty context for non-archival bonsai queries.
   *
   * @return
   */
  public static BonsaiContext emptyContext() {
    return new BonsaiContext(() -> null);
  }

  public static BonsaiContext forBlockHeader(BlockHeader forBlockHeader) {
    return new BonsaiContext(() -> forBlockHeader);
  }

  BlockHeader getBlockHeader() {
    return blockHeaderSupplier.get();
  }
}

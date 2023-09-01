package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FastAndSnapImportBlocksStep
    implements Function<List<BlockWithReceipts>, List<BlockHeader>> {

  private final MutableBlockchain blockchain;

  public FastAndSnapImportBlocksStep(final MutableBlockchain blockchain) {
    this.blockchain = blockchain;
  }

  @Override
  public List<BlockHeader> apply(final List<BlockWithReceipts> blocksWithReceipts) {

    for (BlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      try {
        blockchain.unsafeImportBlock(
            blockWithReceipts.getBlock(), blockWithReceipts.getReceipts(), Optional.empty());

      } catch (Exception ex) {
        return Collections.emptyList();
      }
    }
    return blocksWithReceipts.stream()
        .map(BlockWithReceipts::getHeader)
        .collect(Collectors.toList());
  }
}

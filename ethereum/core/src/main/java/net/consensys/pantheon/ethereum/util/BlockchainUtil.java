package net.consensys.pantheon.ethereum.util;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.BlockHeader;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

public class BlockchainUtil {

  private BlockchainUtil() {}

  public static OptionalInt findHighestKnownBlockIndex(
      final Blockchain blockchain,
      final List<BlockHeader> headers,
      final boolean ascendingHeaderOrder) {
    int offset = ascendingHeaderOrder ? -1 : 0;
    Comparator<BlockHeader> comparator = knownBlockComparator(blockchain, ascendingHeaderOrder);

    int insertionIndex = -Collections.binarySearch(headers, null, comparator) - 1;
    int ancestorIndex = insertionIndex + offset;
    if (ancestorIndex < 0 || ancestorIndex >= headers.size()) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(ancestorIndex);
  }

  private static Comparator<BlockHeader> knownBlockComparator(
      final Blockchain blockchain, final boolean ascendingHeaderOrder) {
    Comparator<BlockHeader> comparator =
        (final BlockHeader element0, final BlockHeader element1) -> {
          if (element0 == null) {
            return blockchain.contains(element1.getHash()) ? -1 : 1;
          }
          return blockchain.contains(element0.getHash()) ? 1 : -1;
        };
    return ascendingHeaderOrder ? comparator.reversed() : comparator;
  }
}

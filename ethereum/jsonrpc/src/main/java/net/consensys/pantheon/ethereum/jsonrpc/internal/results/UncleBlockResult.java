package net.consensys.pantheon.ethereum.jsonrpc.internal.results;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.Collections;

public class UncleBlockResult {

  /**
   * Returns an uncle block, which doesn't include transactions or ommers.
   *
   * @param header The uncle block header.
   * @return A BlockResult, generated from the header and empty body.
   */
  public static BlockResult build(final BlockHeader header) {
    final BlockBody body = new BlockBody(Collections.emptyList(), Collections.emptyList());
    final int size = new Block(header, body).calculateSize();
    return new BlockResult(
        header, Collections.emptyList(), Collections.emptyList(), UInt256.ZERO, size);
  }
}

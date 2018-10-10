package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.util.bytes.BytesValue;

/** Implements the block hashing algorithm for MainNet as per the yellow paper. */
public class MainnetBlockHashFunction {

  public static Hash createHash(final BlockHeader header) {
    final BytesValue rlp = RLP.encode(header::writeTo);
    return Hash.hash(rlp);
  }
}

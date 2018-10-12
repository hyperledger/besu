package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.util.bytes.BytesValue;

/** Implements the block hashing algorithm for MainNet as per the yellow paper. */
public class MainnetBlockHashFunction {

  public static Hash createHash(final BlockHeader header) {
    final BytesValue rlp = RLP.encode(header::writeTo);
    return Hash.hash(rlp);
  }
}

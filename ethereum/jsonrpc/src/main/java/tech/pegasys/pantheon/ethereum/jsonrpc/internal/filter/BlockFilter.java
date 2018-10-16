package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.ArrayList;
import java.util.List;

/** Tracks new blocks being added to the blockchain. */
class BlockFilter extends Filter {

  private final List<Hash> blockHashes = new ArrayList<>();

  BlockFilter(final String id) {
    super(id);
  }

  void addBlockHash(final Hash hash) {
    blockHashes.add(hash);
  }

  List<Hash> blockHashes() {
    return blockHashes;
  }

  void clearBlockHashes() {
    blockHashes.clear();
  }
}

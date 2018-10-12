package net.consensys.pantheon.ethereum.db;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.WorldState;
import net.consensys.pantheon.ethereum.trie.MerklePatriciaTrie;
import net.consensys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import net.consensys.pantheon.ethereum.worldstate.WorldStateStorage;

public class WorldStateArchive {
  private final WorldStateStorage storage;
  private static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_ROOT_HASH);

  public WorldStateArchive(final WorldStateStorage storage) {
    this.storage = storage;
  }

  public WorldState get(final Hash rootHash) {
    return getMutable(rootHash);
  }

  public MutableWorldState getMutable(final Hash rootHash) {
    return new DefaultMutableWorldState(rootHash, storage);
  }

  public WorldState get() {
    return get(EMPTY_ROOT_HASH);
  }

  public MutableWorldState getMutable() {
    return getMutable(EMPTY_ROOT_HASH);
  }
}

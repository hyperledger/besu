package tech.pegasys.pantheon.ethereum.db;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;

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

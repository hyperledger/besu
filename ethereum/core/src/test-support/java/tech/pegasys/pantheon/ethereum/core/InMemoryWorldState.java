package net.consensys.pantheon.ethereum.core;

import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;

public class InMemoryWorldState {

  public static WorldStateArchive createInMemoryWorldStateArchive() {
    return new WorldStateArchive(
        new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage()));
  }
}

package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;

public class InMemoryWorldState {

  public static WorldStateArchive createInMemoryWorldStateArchive() {
    return new WorldStateArchive(
        new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage()));
  }
}

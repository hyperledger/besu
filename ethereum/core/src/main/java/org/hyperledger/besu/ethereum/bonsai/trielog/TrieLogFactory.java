package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;

/** Interface for serializing and deserializing {@link TrieLogLayer} objects. */
public interface TrieLogFactory<T extends TrieLogLayer> {
  T create(BonsaiWorldStateUpdateAccumulator accumulator, final Hash blockHash);

  T deserialize(final byte[] bytes);

  byte[] serialize(final T layer);
}

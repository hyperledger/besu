package org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;

import org.immutables.value.Value;

@Value.Immutable
public interface Checkpoint {

  long blockNumber();

  Hash blockHash();

  Difficulty totalDifficulty();
}

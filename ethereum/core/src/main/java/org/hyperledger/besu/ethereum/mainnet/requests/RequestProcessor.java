package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

public interface RequestProcessor {
  Optional<List<Request>> process(final MutableWorldState mutableWorldState);
}

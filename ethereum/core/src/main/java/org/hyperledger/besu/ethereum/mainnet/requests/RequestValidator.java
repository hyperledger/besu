package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

/** Interface for request validation logic. */
public interface RequestValidator {
  boolean validate(final Block block, final List<Request> request);

  boolean validateParameter(final Optional<List<Request>> request);
}

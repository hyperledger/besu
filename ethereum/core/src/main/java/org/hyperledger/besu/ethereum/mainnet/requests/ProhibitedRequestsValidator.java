package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates that a block does not contain any prohibited requests. */
public class ProhibitedRequestsValidator implements RequestValidator {
  private static final Logger LOG = LoggerFactory.getLogger(ProhibitedRequestsValidator.class);

  /**
   * Validates that the given block does not contain requests or a requests_root, indicating that
   * requests are prohibited.
   *
   * @param block The block to validate.
   * @param request The list of requests to validate, which should be empty or null.
   * @return true if the block does not contain requests or a requests_root, false otherwise.
   */
  @Override
  public boolean validate(final Block block, final List<Request> request) {
    boolean hasRequests = block.getBody().getRequests().isPresent();
    boolean hasRequestsRoot = block.getHeader().getRequestsRoot().isPresent();

    if (hasRequests) {
      LOG.warn("Block {} contains requests but requests are prohibited", block.getHash());
    }

    if (hasRequestsRoot) {
      LOG.warn(
          "Block {} header contains requests_root but requests are prohibited", block.getHash());
    }

    return !(hasRequests || hasRequestsRoot);
  }

  @Override
  public boolean validateParameter(final Optional<List<Request>> request) {
    return request.isEmpty();
  }
}

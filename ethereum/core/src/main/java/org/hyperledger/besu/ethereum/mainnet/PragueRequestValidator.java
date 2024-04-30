/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PragueRequestValidator {

  private static final Logger LOG = LoggerFactory.getLogger(PragueRequestValidator.class);

  public boolean allowRequests() {
    return true;
  }

  public boolean validateRequestParameter(final Optional<List<Request>> requests) {
    return requests.isPresent();
  }

  public boolean validateRequestsInBlock(final Block block, final List<Request> requests) {
    final Hash blockHash = block.getHash();

    if (block.getHeader().getRequestsRoot().isEmpty()) {
      LOG.warn("Block {} must contain requests root", blockHash);
      return false;
    }

    if (block.getBody().getRequests().isEmpty()) {
      LOG.warn("Block {} must contain requests (even if empty list)", blockHash);
      return false;
    }

    // Validate exits_root
    final Hash expectedRequestRoot = BodyValidation.requestsRoot(requests);
    if (!expectedRequestRoot.equals(block.getHeader().getRequestsRoot().get())) {
      LOG.warn(
          "Block {} requests root does not match expected hash root for requests in block",
          blockHash);
      return false;
    }
    return true;
  }
}

/*
 * Copyright ConsenSys AG.
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

package org.hyperledger.besu.ethereum.goquorum;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.logging.log4j.Logger;

public class GoQuorumPrivateStateUtil {
  private static final Logger LOG = getLogger();

  public static MutableWorldState getPrivateWorldState(
      final Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters,
      final BlockHeader header) {
    final GoQuorumPrivateStorage goQuorumPrivateStorage =
        goQuorumPrivacyParameters.orElseThrow().privateStorage();
    final WorldStateArchive goQuorumWorldStateArchive =
        goQuorumPrivacyParameters.orElseThrow().worldStateArchive();
    return getPrivateWorldState(goQuorumPrivateStorage, goQuorumWorldStateArchive, header);
  }

  public static MutableWorldState getPrivateWorldState(
      final GoQuorumPrivateStorage goQuorumPrivateStorage,
      final WorldStateArchive goQuorumWorldStateArchive,
      final BlockHeader header) {
    final Hash worldStateRootHash = header.getStateRoot();
    final Hash publicBlockHash = header.getHash();
    final Hash privateStateRootHash =
        goQuorumPrivateStorage
            .getPrivateStateRootHash(worldStateRootHash)
            .orElse(Hash.EMPTY_TRIE_HASH);

    final Optional<MutableWorldState> maybePrivateWorldState =
        goQuorumWorldStateArchive.getMutable(privateStateRootHash, publicBlockHash);
    if (maybePrivateWorldState.isEmpty()) {
      LOG.debug(
          "Private world state not available for public world state root hash {}, public block hash {}",
          worldStateRootHash,
          publicBlockHash);

      /*
       This should never happen because privateStateRootResolver will either return a matching
       private world state root hash, or the hash for an empty world state (first private tx ever).
      */
      throw new IllegalStateException(
          "Private world state not available for public world state root hash " + publicBlockHash);
    }
    return maybePrivateWorldState.get();
  }
}

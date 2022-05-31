/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Arrays;
import java.util.Optional;

public interface FinalBlockConfirmation {
  boolean finalHeaderReached(final BlockHeader firstHeader);

  public static FinalBlockConfirmation genesisConfirmation(final MutableBlockchain blockchain) {
    final BlockHeader genesisBlockHeader = blockchain.getBlockHeader(0).orElseThrow();
    return firstHeader -> {
      if (firstHeader.getNumber() > 0) {
        return false;
      }

      if (!genesisBlockHeader.getHash().equals(firstHeader.getHash())) {
        throw new BackwardSyncException(
            "Should have reached header "
                + genesisBlockHeader.getHash().toHexString()
                + " but reached "
                + firstHeader.getHash().toHexString());
      }
      return true;
    };
  }

  public static FinalBlockConfirmation finalizedConfirmation(final MutableBlockchain blockchain) {
    return firstHeader -> {
      final Optional<Block> finalized =
          blockchain.getFinalized().flatMap(blockchain::getBlockByHash);
      if (finalized.isPresent()
          && (finalized.get().getHeader().getNumber() >= firstHeader.getNumber())) {
        throw new BackwardSyncException("Cannot continue below finalized...");
      }
      if (blockchain.contains(firstHeader.getParentHash())) {
        return true;
      }
      return false;
    };
  }

  public static FinalBlockConfirmation confirmationChain(
      final FinalBlockConfirmation... confirmations) {
    return firstHeader -> {
      return Arrays.stream(confirmations)
          .reduce(
              false,
              (aBoolean, confirmation2) ->
                  aBoolean || confirmation2.finalHeaderReached(firstHeader),
              (aBoolean, aBoolean2) -> aBoolean || aBoolean2);
    };
  }
}

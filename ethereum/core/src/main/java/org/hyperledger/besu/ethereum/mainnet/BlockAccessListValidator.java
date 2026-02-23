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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.util.Optional;

/** Validates block access lists according to protocol rules. */
public interface BlockAccessListValidator {

  /**
   * Rejects any block that includes a BAL (returns false when present). Used for forks before
   * Amsterdam, where blocks must not contain a block access list.
   */
  BlockAccessListValidator ALWAYS_REJECT_BAL =
      (blockAccessList, header, nbTransactions) ->
          blockAccessList.isEmpty() && header.getBalHash().isEmpty();

  /**
   * Validates a block access list against protocol constraints.
   *
   * @param blockAccessList the optional block access list to validate (empty if block has no BAL)
   * @param blockHeader the block header containing gas limit and other context
   * @param nbTransactions number of transactions in the block (must be &ge; 0)
   * @return true if the block access list is valid or absent, false otherwise
   */
  boolean validate(
      Optional<BlockAccessList> blockAccessList, BlockHeader blockHeader, int nbTransactions);
}

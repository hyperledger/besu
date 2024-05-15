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
package org.hyperledger.besu.consensus.clique.headervalidationrules;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The No empty block validation rule. */
public class CliqueNoEmptyBlockValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(CliqueNoEmptyBlockValidationRule.class);

  /** Default constructor. */
  public CliqueNoEmptyBlockValidationRule() {}

  /**
   * Responsible for ensuring there are no empty transactions. This is used when createEmptyBlocks
   * is false, to ensure that no empty blocks are created.
   *
   * @param header the block header to validate
   * @param parent the block header corresponding to the parent of the header being validated.
   * @return true if the transactionsRoot in the header is not the empty trie hash.
   */
  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final boolean hasTransactions = !header.getTransactionsRoot().equals(Hash.EMPTY_TRIE_HASH);
    if (!hasTransactions) {
      LOG.info(
          "Invalid block header: {} has no transactions but create empty blocks is not enabled",
          header.toLogString());
    }
    return hasTransactions;
  }
}

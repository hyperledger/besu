/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.Optional;

/** A service that plugins can use to query blocks by number */
@Unstable
public interface BlockchainService extends BesuService {
  /**
   * Gets block by number
   *
   * @param number the block number
   * @return the BlockContext
   */
  Optional<BlockContext> getBlockByNumber(final long number);

  /**
   * Get the hash of the chain head
   *
   * @return chain head hash
   */
  Hash getChainHeadHash();

  /**
   * Get the block header of the chain head
   *
   * @return chain head block header
   */
  BlockHeader getChainHeadHeader();

  /**
   * Return the base fee for the next block
   *
   * @return base fee of the next block or empty if the fee market does not support base fee
   */
  Optional<Wei> getNextBlockBaseFee();
}

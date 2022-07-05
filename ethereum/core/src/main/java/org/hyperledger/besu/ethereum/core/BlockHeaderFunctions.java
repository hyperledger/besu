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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Hash;

/**
 * An interface for calculating pars of a {@link BlockHeader} which vary based on the consensus
 * mechanism.
 */
public interface BlockHeaderFunctions {

  /**
   * Create the hash for a given BlockHeader.
   *
   * @param header the header to create the block hash from
   * @return a {@link Hash} containing the block hash.
   */
  Hash hash(BlockHeader header);

  /**
   * Parse the extra data value from the header. The actual type returned from this method is
   * consensus mechanism specific and may be cast to the specific type when used.
   *
   * @param header the header to parse the extra data from
   * @return an instance {@link ParsedExtraData} that provides access to consensus specific
   *     information parsed from the extra data field. <code>null</code> may be returned if the
   *     consensus mechanism does not include parseable information in the extra data field.
   */
  ParsedExtraData parseExtraData(BlockHeader header);

  /**
   * Depending on the consensus, several block headers must be downloaded before the checkpoint in
   * order to validate the following blocks. This method returns the necessary number of block
   * headers to download to be able to start the checkpoint sync
   *
   * @param blockHeader of the checkpoint
   * @return number of headers to download
   */
  default int getCheckPointWindowSize(final BlockHeader blockHeader) {
    return 1;
  }
}

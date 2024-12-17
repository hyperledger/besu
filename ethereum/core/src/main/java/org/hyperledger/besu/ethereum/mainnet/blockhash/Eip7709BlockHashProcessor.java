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
package org.hyperledger.besu.ethereum.mainnet.blockhash;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.vm.Eip7709BlockHashLookup;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;

/**
 * Provides a way to create a BlockHashLookup that fetches hashes from system contract storage, in
 * accordance with EIP-7709. It is not used yet since the fork that this EIP should go in has not
 * been decided yet.
 */
public class Eip7709BlockHashProcessor extends PragueBlockHashProcessor {

  @Override
  public BlockHashLookup createBlockHashLookup(
      final Blockchain blockchain, final ProcessableBlockHeader blockHeader) {
    return new Eip7709BlockHashLookup(historyStorageAddress, historyServeWindow);
  }
}

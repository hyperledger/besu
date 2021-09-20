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
package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.ethereum.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;
import java.util.Optional;

public class MergeCoordinator extends NoopMiningCoordinator {

  public MergeCoordinator(final MiningParameters miningParameters) {
    super(miningParameters);
  }

  @Override
  public Optional<Block> createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    // TODO: implement createBlock for BlockProposal flow
    //      https://github.com/ConsenSys/protocol-misc/issues/479
    //      https://github.com/ConsenSys/protocol-misc/issues/480
    return Optional.empty();
  }
}

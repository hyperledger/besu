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
package org.hyperledger.besu.consensus.ibft.queries;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.PoAMetricServiceImpl;
import org.hyperledger.besu.consensus.ibft.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.services.metrics.IbftQueries;

import java.util.Collection;
import java.util.Collections;

public class IbftQueriesImpl extends PoAMetricServiceImpl implements IbftQueries {

  public IbftQueriesImpl(final BlockInterface blockInterface, final Blockchain blockchain) {
    super(blockInterface, blockchain);
  }

  @Override
  public int getRoundNumberFromCanonicalHead() {
    final BlockHeader canonicalHeader = blockchain.getChainHeadHeader();

    final IbftExtraData extraData = IbftExtraData.decode(canonicalHeader);

    return extraData.getRound();
  }

  @Override
  public Collection<Address> getSignersFromCanonicalHead() {
    final BlockHeader canonicalHeader = blockchain.getChainHeadHeader();
    final IbftExtraData extraData = IbftExtraData.decode(canonicalHeader);

    return Collections.unmodifiableList(
        IbftBlockHashing.recoverCommitterAddresses(canonicalHeader, extraData));
  }
}

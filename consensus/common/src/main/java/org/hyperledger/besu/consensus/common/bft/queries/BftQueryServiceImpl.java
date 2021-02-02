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
package org.hyperledger.besu.consensus.common.bft.queries;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.PoaQueryServiceImpl;
import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.services.query.BftQueryService;

import java.util.Collection;
import java.util.Collections;

import org.apache.tuweni.bytes.Bytes32;

public class BftQueryServiceImpl extends PoaQueryServiceImpl implements BftQueryService {

  private final String consensusMechanismName;

  public BftQueryServiceImpl(
      final BlockInterface blockInterface,
      final Blockchain blockchain,
      final NodeKey nodeKey,
      final String consensusMechanismName) {
    super(blockInterface, blockchain, nodeKey);
    this.consensusMechanismName = consensusMechanismName;
  }

  @Override
  public int getRoundNumberFrom(final org.hyperledger.besu.plugin.data.BlockHeader header) {
    final BlockHeader headerFromChain = getHeaderFromChain(header);
    final BftExtraData extraData = BftExtraData.decode(headerFromChain);
    return extraData.getRound();
  }

  @Override
  public Collection<Address> getSignersFrom(
      final org.hyperledger.besu.plugin.data.BlockHeader header) {
    final BlockHeader headerFromChain = getHeaderFromChain(header);
    final BftExtraData extraData = BftExtraData.decode(headerFromChain);

    return Collections.unmodifiableList(
        BftBlockHashing.recoverCommitterAddresses(headerFromChain, extraData));
  }

  @Override
  public String getConsensusMechanismName() {
    return consensusMechanismName;
  }

  private BlockHeader getHeaderFromChain(
      final org.hyperledger.besu.plugin.data.BlockHeader header) {
    if (header instanceof BlockHeader) {
      return (BlockHeader) header;
    }

    final Hash blockHash = Hash.wrap(Bytes32.wrap(header.getBlockHash().toArray()));
    return getBlockchain().getBlockHeader(blockHash).orElseThrow();
  }
}

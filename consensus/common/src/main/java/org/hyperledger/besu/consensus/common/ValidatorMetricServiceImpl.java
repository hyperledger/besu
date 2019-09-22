/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.consensus.common;

import static org.hyperledger.besu.ethereum.core.Hash.fromHexString;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.metrics.ValidatorMetricsService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class ValidatorMetricServiceImpl implements ValidatorMetricsService {

  private final BlockInterface blockInterface;
  private final Blockchain blockchain;

  public ValidatorMetricServiceImpl(
      final BlockInterface blockInterface, final Blockchain blockchain) {
    this.blockInterface = blockInterface;
    this.blockchain = blockchain;
  }

  @Override
  public Collection<Address> getValidators() {
    return new ArrayList<>(blockInterface.validatorsInBlock(blockchain.getChainHeadHeader()));
  }

  @Override
  public Address getProposerOfBlock(final BlockHeader header) {
    Optional<Block> blockByHash =
        blockchain.getBlockByHash(fromHexString(header.getBlockHash().getHexString()));
    Optional<org.hyperledger.besu.ethereum.core.Address> address =
        blockByHash.map(block -> this.blockInterface.getProposerOfBlock(block.getHeader()));
    return address.get();
  }
}

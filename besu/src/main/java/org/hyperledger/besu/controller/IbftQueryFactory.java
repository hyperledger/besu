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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.ibft.IbftBlockInterface;
import org.hyperledger.besu.consensus.ibft.queries.IbftQueryServiceImpl;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.plugin.services.metrics.IbftQueryService;
import org.hyperledger.besu.services.BesuPluginContextImpl;

public class IbftQueryFactory implements PluginServiceFactory {

  final Blockchain blockchain;

  public IbftQueryFactory(final Blockchain blockchain) {
    this.blockchain = blockchain;
  }

  @Override
  public void appendQueries(final BesuPluginContextImpl besuContext) {
    final BlockInterface blockInterface = new IbftBlockInterface();

    besuContext.addService(
        IbftQueryService.class, new IbftQueryServiceImpl(blockInterface, blockchain));
  }
}

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
package org.hyperledger.besu.consensus.clique.jsonrpc;

import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueGetSignerMetrics;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueGetSigners;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueGetSignersAtHash;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueProposals;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.Discard;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.Propose;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.ApiGroupJsonRpcMethods;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Map;

public class CliqueJsonRpcMethods extends ApiGroupJsonRpcMethods {
  private final ProtocolContext context;

  public CliqueJsonRpcMethods(final ProtocolContext context) {
    this.context = context;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.CLIQUE.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final MutableBlockchain blockchain = context.getBlockchain();
    final WorldStateArchive worldStateArchive = context.getWorldStateArchive();
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(blockchain, worldStateArchive);
    final ValidatorProvider validatorProvider =
        context.getConsensusContext(CliqueContext.class).getValidatorProvider();

    // Must create our own voteTallyCache as using this would pollute the main voteTallyCache
    final ValidatorProvider readOnlyValidatorProvider =
        createValidatorProvider(context, blockchain);

    return mapOf(
        new CliqueGetSigners(blockchainQueries, readOnlyValidatorProvider),
        new CliqueGetSignersAtHash(blockchainQueries, readOnlyValidatorProvider),
        new Propose(validatorProvider),
        new Discard(validatorProvider),
        new CliqueProposals(validatorProvider),
        new CliqueGetSignerMetrics(
            readOnlyValidatorProvider, new CliqueBlockInterface(), blockchainQueries));
  }

  private ValidatorProvider createValidatorProvider(
      final ProtocolContext context, final MutableBlockchain blockchain) {
    final EpochManager epochManager =
        context.getConsensusContext(CliqueContext.class).getEpochManager();
    final CliqueBlockInterface cliqueBlockInterface = new CliqueBlockInterface();
    return BlockValidatorProvider.nonForkingValidatorProvider(
        blockchain, epochManager, cliqueBlockInterface);
  }
}

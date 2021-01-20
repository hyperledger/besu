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
package org.hyperledger.besu.consensus.common.jsonrpc.methods;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.common.VoteTallyUpdater;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.jsonrpc.methods.bft.BftDiscardValidatorVote;
import org.hyperledger.besu.consensus.common.jsonrpc.methods.bft.BftGetPendingVotes;
import org.hyperledger.besu.consensus.common.jsonrpc.methods.bft.BftGetSignerMetrics;
import org.hyperledger.besu.consensus.common.jsonrpc.methods.bft.BftGetValidatorsByBlockHash;
import org.hyperledger.besu.consensus.common.jsonrpc.methods.bft.BftGetValidatorsByBlockNumber;
import org.hyperledger.besu.consensus.common.jsonrpc.methods.bft.BftProposeValidatorVote;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.ApiGroupJsonRpcMethods;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BftJsonRpcMethods extends ApiGroupJsonRpcMethods {
  private final ProtocolContext context;
  private final RpcApi bftRpcApi;
  private final boolean addLegacyRpcMethods;

  public BftJsonRpcMethods(final ProtocolContext context, final RpcApi bftRpcApi) {
    this(context, bftRpcApi, false);
  }

  public BftJsonRpcMethods(
      final ProtocolContext context, final RpcApi bftRpcApi, boolean addLegacyRpcMethods) {
    this.context = context;
    this.bftRpcApi = bftRpcApi;
    this.addLegacyRpcMethods = addLegacyRpcMethods;
  }

  @Override
  protected RpcApi getApiGroup() {
    return bftRpcApi;
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final List<JsonRpcMethod> rpcMethodsList = new ArrayList<>(createRpcMethods(false));

    if (addLegacyRpcMethods) {
      rpcMethodsList.addAll(createRpcMethods(true));
    }

    return rpcMethodsList.stream()
        .collect(Collectors.toMap(JsonRpcMethod::getName, Function.identity()));
  }

  private List<JsonRpcMethod> createRpcMethods(final boolean useLegacyRpcMethods) {
    final MutableBlockchain mutableBlockchain = context.getBlockchain();
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(context.getBlockchain(), context.getWorldStateArchive());
    final VoteProposer voteProposer = context.getConsensusState(BftContext.class).getVoteProposer();
    final BlockInterface blockInterface = new BftBlockInterface();

    final VoteTallyCache voteTallyCache = createVoteTallyCache(context, mutableBlockchain);
    final ArrayList<JsonRpcMethod> rpcMethodsList = new ArrayList<>();

    rpcMethodsList.add(new BftProposeValidatorVote(voteProposer, useLegacyRpcMethods));
    rpcMethodsList.add(
        new BftGetValidatorsByBlockNumber(blockchainQueries, blockInterface, useLegacyRpcMethods));
    rpcMethodsList.add(new BftDiscardValidatorVote(voteProposer, useLegacyRpcMethods));
    rpcMethodsList.add(
        new BftGetValidatorsByBlockHash(
            context.getBlockchain(), blockInterface, useLegacyRpcMethods));
    rpcMethodsList.add(
        new BftGetSignerMetrics(
            voteTallyCache, blockInterface, blockchainQueries, useLegacyRpcMethods));
    rpcMethodsList.add(new BftGetPendingVotes(voteProposer, useLegacyRpcMethods));

    return rpcMethodsList;
  }

  private VoteTallyCache createVoteTallyCache(
      final ProtocolContext context, final MutableBlockchain blockchain) {
    final EpochManager epochManager = context.getConsensusState(BftContext.class).getEpochManager();
    final BftBlockInterface bftBlockInterface = new BftBlockInterface();
    final VoteTallyUpdater voteTallyUpdater = new VoteTallyUpdater(epochManager, bftBlockInterface);
    return new VoteTallyCache(blockchain, voteTallyUpdater, epochManager, bftBlockInterface);
  }
}

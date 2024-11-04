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
package org.hyperledger.besu.consensus.qbft.jsonrpc;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.qbft.jsonrpc.methods.QbftDiscardValidatorVote;
import org.hyperledger.besu.consensus.qbft.jsonrpc.methods.QbftGetPendingVotes;
import org.hyperledger.besu.consensus.qbft.jsonrpc.methods.QbftGetRequestTimeoutSeconds;
import org.hyperledger.besu.consensus.qbft.jsonrpc.methods.QbftGetSignerMetrics;
import org.hyperledger.besu.consensus.qbft.jsonrpc.methods.QbftGetValidatorsByBlockHash;
import org.hyperledger.besu.consensus.qbft.jsonrpc.methods.QbftGetValidatorsByBlockNumber;
import org.hyperledger.besu.consensus.qbft.jsonrpc.methods.QbftProposeValidatorVote;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.ApiGroupJsonRpcMethods;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Map;

/** The Qbft json rpc methods. */
public class QbftJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final ProtocolContext context;
  private final ValidatorProvider readOnlyValidatorProvider;
  private final ProtocolSchedule protocolSchedule;
  private final MiningConfiguration miningConfiguration;
  private final BftConfigOptions bftConfig;

  /**
   * Instantiates a new Qbft json rpc methods.
   *
   * @param context the protocol context
   * @param protocolSchedule the protocol schedule
   * @param miningConfiguration the mining parameters
   * @param readOnlyValidatorProvider the read only validator provider
   * @param bftConfig the BFT config options, containing QBFT-specific settings
   */
  public QbftJsonRpcMethods(
      final ProtocolContext context,
      final ProtocolSchedule protocolSchedule,
      final MiningConfiguration miningConfiguration,
      final ValidatorProvider readOnlyValidatorProvider,
      final BftConfigOptions bftConfig) {
    this.context = context;
    this.readOnlyValidatorProvider = readOnlyValidatorProvider;
    this.protocolSchedule = protocolSchedule;
    this.miningConfiguration = miningConfiguration;
    this.bftConfig = bftConfig;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.QBFT.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            protocolSchedule,
            context.getBlockchain(),
            context.getWorldStateArchive(),
            miningConfiguration);
    final BftContext bftContext = context.getConsensusContext(BftContext.class);
    final BlockInterface blockInterface = bftContext.getBlockInterface();
    final ValidatorProvider validatorProvider = bftContext.getValidatorProvider();

    Map<String, JsonRpcMethod> methods =
        mapOf(
            new QbftProposeValidatorVote(validatorProvider),
            new QbftGetValidatorsByBlockNumber(blockchainQueries, readOnlyValidatorProvider),
            new QbftDiscardValidatorVote(validatorProvider),
            new QbftGetValidatorsByBlockHash(context.getBlockchain(), readOnlyValidatorProvider),
            new QbftGetSignerMetrics(readOnlyValidatorProvider, blockInterface, blockchainQueries),
            new QbftGetPendingVotes(validatorProvider),
            new QbftGetRequestTimeoutSeconds(bftConfig));

    return methods;
  }
}

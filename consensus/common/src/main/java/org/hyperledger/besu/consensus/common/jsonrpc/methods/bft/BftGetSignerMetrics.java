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
package org.hyperledger.besu.consensus.common.jsonrpc.methods.bft;

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.BFT_GET_SIGNER_METRICS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.IBFT_GET_SIGNER_METRICS;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.common.jsonrpc.AbstractGetSignerMetricsMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

public class BftGetSignerMetrics extends AbstractGetSignerMetricsMethod implements JsonRpcMethod {
  private final boolean legacyRpcMethodName;

  public BftGetSignerMetrics(
      final VoteTallyCache voteTallyCache,
      final BlockInterface blockInterface,
      final BlockchainQueries blockchainQueries) {
    this(voteTallyCache, blockInterface, blockchainQueries, false);
  }

  public BftGetSignerMetrics(
      final VoteTallyCache voteTallyCache,
      final BlockInterface blockInterface,
      final BlockchainQueries blockchainQueries,
      final boolean legacyRpcMethodName) {
    super(voteTallyCache, blockInterface, blockchainQueries);
    this.legacyRpcMethodName = legacyRpcMethodName;
  }

  @Override
  public String getName() {
    return legacyRpcMethodName
        ? IBFT_GET_SIGNER_METRICS.getMethodName()
        : BFT_GET_SIGNER_METRICS.getMethodName();
  }
}

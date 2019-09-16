/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.consensus.clique.jsonrpc.methods;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.jsonrpc.AbstractGetSignerMetricsMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;

public class CliqueGetSignerMetrics extends AbstractGetSignerMetricsMethod
    implements JsonRpcMethod {

  public CliqueGetSignerMetrics(
      final BlockInterface blockInterface,
      final BlockchainQueries blockchainQueries,
      final JsonRpcParameter parameter) {
    super(blockInterface, blockchainQueries, parameter);
  }

  @Override
  public String getName() {
    return RpcMethod.CLIQUE_GET_SIGNER_METRICS.getMethodName();
  }
}

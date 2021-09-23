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

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.MinerDataResult;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

public class EthGetMinerDataByBlockNumber extends AbstractBlockParameterMethod {
  private final ProtocolSchedule protocolSchedule;

  public EthGetMinerDataByBlockNumber(
      final BlockchainQueries blockchain, final ProtocolSchedule protocolSchedule) {
    super(blockchain);
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_MINER_DATA_BY_BLOCK_NUMBER.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    BlockWithMetadata<TransactionWithMetadata, Hash> block =
        getBlockchainQueries().blockByNumber(blockNumber).orElse(null);

    MinerDataResult minerDataResult = null;
    if (block != null) {
      minerDataResult =
          EthGetMinerDataByBlockHash.createMinerDataResult(
              block, protocolSchedule, getBlockchainQueries());
    }

    return minerDataResult;
  }
}

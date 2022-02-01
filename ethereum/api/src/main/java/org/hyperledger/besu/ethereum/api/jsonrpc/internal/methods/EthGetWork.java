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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.DirectAcyclicGraphSeed;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.Optional;

import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthGetWork implements JsonRpcMethod {

  private final MiningCoordinator miner;
  private static final Logger LOG = LoggerFactory.getLogger(EthGetWork.class);
  private final EpochCalculator epochCalculator;

  public EthGetWork(final MiningCoordinator miner) {
    this.miner = miner;
    if (miner instanceof PoWMiningCoordinator) {
      this.epochCalculator = ((PoWMiningCoordinator) miner).getEpochCalculator();
    } else {
      this.epochCalculator = new EpochCalculator.DefaultEpochCalculator();
    }
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_WORK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Optional<PoWSolverInputs> solver = miner.getWorkDefinition();
    if (solver.isPresent()) {
      final PoWSolverInputs rawResult = solver.get();
      final byte[] dagSeed =
          DirectAcyclicGraphSeed.dagSeed(rawResult.getBlockNumber(), epochCalculator);
      final String[] result = {
        rawResult.getPrePowHash().toHexString(),
        "0x" + BaseEncoding.base16().lowerCase().encode(dagSeed),
        rawResult.getTarget().toHexString(),
        Quantity.create(rawResult.getBlockNumber())
      };
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
    } else {
      LOG.trace("Mining is not operational, eth_getWork request cannot be processed");
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.NO_MINING_WORK_FOUND);
    }
  }
}

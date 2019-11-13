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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolution;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

import org.apache.logging.log4j.Logger;

public class EthSubmitWork implements JsonRpcMethod {

  private final MiningCoordinator miner;
  private final JsonRpcParameter parameters;
  private static final Logger LOG = getLogger();

  public EthSubmitWork(final MiningCoordinator miner, final JsonRpcParameter parameters) {
    this.miner = miner;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SUBMIT_WORK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    final Optional<EthHashSolverInputs> solver = miner.getWorkDefinition();
    if (solver.isPresent()) {
      final EthHashSolution solution =
          new EthHashSolution(
              BytesValue.fromHexString(parameters.required(req.getParams(), 0, String.class))
                  .getLong(0),
              parameters.required(req.getParams(), 2, Hash.class),
              BytesValue.fromHexString(parameters.required(req.getParams(), 1, String.class))
                  .getArrayUnsafe());
      final boolean result = miner.submitWork(solution);
      return new JsonRpcSuccessResponse(req.getId(), result);
    } else {
      LOG.trace("Mining is not operational, eth_submitWork request cannot be processed");
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.NO_MINING_WORK_FOUND);
    }
  }
}

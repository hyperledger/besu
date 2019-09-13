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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.mainnet.DirectAcyclicGraphSeed;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolverInputs;

import java.util.Optional;

import com.google.common.io.BaseEncoding;
import org.apache.logging.log4j.Logger;

public class EthGetWork implements JsonRpcMethod {

  private final MiningCoordinator miner;
  private static final Logger LOG = getLogger();

  public EthGetWork(final MiningCoordinator miner) {
    this.miner = miner;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_WORK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    final Optional<EthHashSolverInputs> solver = miner.getWorkDefinition();
    if (solver.isPresent()) {
      final EthHashSolverInputs rawResult = solver.get();
      final byte[] dagSeed = DirectAcyclicGraphSeed.dagSeed(rawResult.getBlockNumber());
      final String[] result = {
        "0x" + BaseEncoding.base16().lowerCase().encode(rawResult.getPrePowHash()),
        "0x" + BaseEncoding.base16().lowerCase().encode(dagSeed),
        rawResult.getTarget().toHexString()
      };
      return new JsonRpcSuccessResponse(req.getId(), result);
    } else {
      LOG.trace("Mining is not operational, eth_getWork request cannot be processed");
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.NO_MINING_WORK_FOUND);
    }
  }
}

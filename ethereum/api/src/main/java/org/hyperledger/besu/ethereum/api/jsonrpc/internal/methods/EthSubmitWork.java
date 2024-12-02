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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(since = "24.12.0")
public class EthSubmitWork implements JsonRpcMethod {

  private final MiningCoordinator miner;
  private static final Logger LOG = LoggerFactory.getLogger(EthSubmitWork.class);

  public EthSubmitWork(final MiningCoordinator miner) {
    this.miner = miner;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SUBMIT_WORK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Optional<PoWSolverInputs> solver = miner.getWorkDefinition();
    if (solver.isPresent()) {
      long nonce;
      try {
        nonce =
            Bytes.fromHexString(requestContext.getRequiredParameter(0, String.class)).getLong(0);
      } catch (JsonRpcParameterException e) {
        throw new InvalidJsonRpcParameters(
            "Invalid nonce parameter (index 0)", RpcErrorType.INVALID_NONCE_PARAMS, e);
      }
      Hash mixHash;
      try {
        mixHash = requestContext.getRequiredParameter(2, Hash.class);
      } catch (JsonRpcParameterException e) {
        throw new InvalidJsonRpcParameters(
            "Invalid mix hash parameter (index 2)", RpcErrorType.INVALID_MIX_HASH_PARAMS, e);
      }
      Bytes powHash;
      try {
        powHash = Bytes.fromHexString(requestContext.getRequiredParameter(1, String.class));
      } catch (JsonRpcParameterException e) {
        throw new InvalidJsonRpcParameters(
            "Invalid PoW hash parameter (index 1)", RpcErrorType.INVALID_POW_HASH_PARAMS, e);
      }
      final PoWSolution solution = new PoWSolution(nonce, mixHash, null, powHash);
      final boolean result = miner.submitWork(solution);
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
    } else {
      LOG.trace("Mining is not operational, eth_submitWork request cannot be processed");
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.NO_MINING_WORK_FOUND);
    }
  }
}

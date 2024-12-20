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
package org.hyperledger.besu.ethereum.stratum;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;

/**
 * Stratum protocol handler.
 *
 * <p>Manages the lifecycle of a TCP connection according to a particular variant of the Stratum
 * protocol.
 */
@Deprecated(since = "24.12.0")
public interface StratumProtocol {

  /**
   * Checks if the protocol can handle a TCP connection, based on the initial message. If the
   * protocol can handle the message, it will consume it and handle it.
   *
   * @param initialMessage the initial message sent over the TCP connection.
   * @param conn the connection itself
   * @param sender the callback to use to send messages back to the client
   * @return true if the protocol can handle this connection
   */
  boolean maybeHandle(Buffer initialMessage, StratumConnection conn, Consumer<String> sender);

  /**
   * Callback when a stratum connection is closed.
   *
   * @param conn the connection that just closed
   */
  void onClose(StratumConnection conn);

  /**
   * Handle a message over an established Stratum connection
   *
   * @param conn the Stratum connection
   * @param message the message to handle
   * @param sender the callback to use to send messages back to the client
   */
  void handle(StratumConnection conn, Buffer message, Consumer<String> sender);

  /**
   * Sets the current proof-of-work job.
   *
   * @param input the new proof-of-work job to send to miners
   */
  void setCurrentWorkTask(PoWSolverInputs input);

  void setSubmitCallback(Function<PoWSolution, Boolean> submitSolutionCallback);

  default void handleHashrateSubmit(
      final JsonMapper mapper,
      final MiningCoordinator miningCoordinator,
      final StratumConnection conn,
      final JsonRpcRequest message,
      final Consumer<String> sender) {
    final String hashRate;
    try {
      hashRate = message.getRequiredParameter(0, String.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid hash rate parameter (index 0)", RpcErrorType.INVALID_HASH_RATE_PARAMS, e);
    }
    final String id;
    try {
      id = message.getRequiredParameter(1, String.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid sealer ID parameter (index 1)", RpcErrorType.INVALID_SEALER_ID_PARAMS, e);
    }
    String response;
    try {
      response =
          mapper.writeValueAsString(
              new JsonRpcSuccessResponse(
                  message.getId(),
                  miningCoordinator.submitHashRate(
                      id, Bytes.fromHexString(hashRate).toBigInteger().longValue())));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    sender.accept(response);
  }
}

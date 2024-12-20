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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetWork;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSubmitHashRate;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSubmitWork;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the stratum1+tcp protocol.
 *
 * <p>This protocol allows miners to submit EthHash solutions over a persistent TCP connection.
 */
@Deprecated(since = "24.12.0")
public class Stratum1EthProxyProtocol implements StratumProtocol {
  private static final Logger LOG = LoggerFactory.getLogger(Stratum1EthProxyProtocol.class);
  private static final JsonMapper mapper = new JsonMapper();
  private final EthGetWork ethGetWork;
  private final EthSubmitWork ethSubmitWork;
  private final EthSubmitHashRate ethSubmitHashRate;
  private final Collection<StratumConnection> activeConnections = new ArrayList<>();

  public Stratum1EthProxyProtocol(final PoWMiningCoordinator miningCoordinator) {
    ethGetWork = new EthGetWork(miningCoordinator);
    ethSubmitWork = new EthSubmitWork(miningCoordinator);
    ethSubmitHashRate = new EthSubmitHashRate(miningCoordinator);
  }

  @Override
  public boolean maybeHandle(
      final Buffer initialMessage, final StratumConnection conn, final Consumer<String> sender) {
    JsonRpcRequest req;
    try {
      req = new JsonObject(initialMessage).mapTo(JsonRpcRequest.class);
    } catch (DecodeException | IllegalArgumentException e) {
      LOG.debug(e.getMessage(), e);
      return false;
    }
    if (!"eth_submitLogin".equals(req.getMethod())) {
      LOG.debug("Invalid first message method: {}", req.getMethod());
      return false;
    }

    try {
      String response = mapper.writeValueAsString(new JsonRpcSuccessResponse(req.getId(), true));
      sender.accept(response);
      activeConnections.add(conn);
    } catch (JsonProcessingException e) {
      LOG.debug(e.getMessage(), e);
      conn.close();
    }

    return true;
  }

  @Override
  public void onClose(final StratumConnection conn) {
    activeConnections.remove(conn);
  }

  @Override
  public void handle(
      final StratumConnection conn, final Buffer message, final Consumer<String> sender) {
    final JsonRpcRequest req = new JsonObject(message).mapTo(JsonRpcRequest.class);
    final JsonRpcRequestContext reqContext = new JsonRpcRequestContext(req);
    final JsonRpcResponse rpcResponse;
    switch (req.getMethod()) {
      case "eth_getWork" -> rpcResponse = ethGetWork.response(reqContext);
      case "eth_submitWork" -> rpcResponse = ethSubmitWork.response(reqContext);
      case "eth_submitHashrate" -> rpcResponse = ethSubmitHashRate.response(reqContext);
      default -> {
        LOG.debug("Invalid method: {}", req.getMethod());
        throw new UnsupportedOperationException("Invalid method: " + req.getMethod());
      }
    }
    try {
      sender.accept(mapper.writeValueAsString(rpcResponse));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void setCurrentWorkTask(final PoWSolverInputs input) {
    activeConnections.forEach(
        conn -> {
          try {
            conn.notificationSender()
                .accept(
                    mapper.writeValueAsString(
                        new JsonRpcSuccessResponse(0, ethGetWork.rawResponse(input))));
          } catch (JsonProcessingException e) {
            LOG.error("Failed to announce new work", e);
          }
        });
  }

  @Override
  public void setSubmitCallback(final Function<PoWSolution, Boolean> submitSolutionCallback) {}
}

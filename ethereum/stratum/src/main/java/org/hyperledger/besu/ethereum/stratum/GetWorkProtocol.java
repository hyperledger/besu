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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.mainnet.DirectAcyclicGraphSeed;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Protocol using JSON-RPC HTTP methods to provide getWork/submitWork methods. */
@Deprecated(since = "24.12.0")
public class GetWorkProtocol implements StratumProtocol {
  private static final Logger LOG = LoggerFactory.getLogger(GetWorkProtocol.class);
  private static final ObjectMapper mapper = new ObjectMapper();

  private final EpochCalculator epochCalculator;
  private volatile PoWSolverInputs currentInput;
  private Function<PoWSolution, Boolean> submitCallback;
  private String[] getWorkResult;

  public GetWorkProtocol(final EpochCalculator epochCalculator) {
    this.epochCalculator = epochCalculator;
  }

  @Override
  public boolean maybeHandle(
      final Buffer initialMessage, final StratumConnection conn, final Consumer<String> sender) {
    JsonObject message;
    try {
      message = initialMessage.toJsonObject();
    } catch (DecodeException e) {
      return false;
    }
    if (message == null) {
      return false;
    }
    String method = message.getString("method");
    if (method != null) {
      if ("eth_getWork".equals(method) || "eth_submitWork".equals(method)) {
        boolean canHandle;
        try {
          Integer idNode = message.getInteger("id");
          canHandle = idNode != null;
        } catch (ClassCastException e) {
          canHandle = false;
        }
        try {
          handle(conn, initialMessage, sender);
        } catch (Exception e) {
          LOG.warn("Error handling message", e);
        }
        return canHandle;
      }
    }
    return false;
  }

  @Override
  public void onClose(final StratumConnection conn) {}

  @Override
  public void handle(
      final StratumConnection conn, final Buffer message, final Consumer<String> sender) {
    JsonObject jsonrpcMessage = message.toJsonObject();
    if (jsonrpcMessage == null) {
      LOG.warn("Invalid message {}", message);
      conn.close();
      return;
    }
    String method = jsonrpcMessage.getString("method");
    Integer id;
    try {
      id = jsonrpcMessage.getInteger("id");
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(e);
    }
    if (method == null || id == null) {
      throw new IllegalArgumentException("Invalid JSON-RPC message");
    }
    if ("eth_getWork".equals(method)) {
      JsonRpcSuccessResponse response = new JsonRpcSuccessResponse(id, getWorkResult);
      try {
        String responseMessage = mapper.writeValueAsString(response);
        sender.accept(responseMessage);
      } catch (JsonProcessingException e) {
        LOG.warn("Error sending work", e);
        conn.close();
      }
    } else if ("eth_submitWork".equals(method)) {
      JsonArray paramsNode;
      try {
        paramsNode = jsonrpcMessage.getJsonArray("params");
      } catch (ClassCastException e) {
        throw new IllegalArgumentException("Invalid eth_submitWork params");
      }
      if (paramsNode == null || paramsNode.size() != 3) {
        throw new IllegalArgumentException("Invalid eth_submitWork params");
      }
      final PoWSolution solution =
          new PoWSolution(
              Bytes.fromHexString(paramsNode.getString(0)).getLong(0),
              Hash.fromHexString(paramsNode.getString(2)),
              null,
              Bytes.fromHexString(paramsNode.getString(1)));
      final boolean result = submitCallback.apply(solution);
      try {
        String resultMessage = mapper.writeValueAsString(new JsonRpcSuccessResponse(id, result));
        sender.accept(resultMessage);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Error accepting solution work", e);
      }
    } else {
      throw new UnsupportedOperationException("Unsupported method " + method);
    }
  }

  @Override
  public void setCurrentWorkTask(final PoWSolverInputs input) {
    LOG.atDebug().setMessage("setting current stratum work task {}").addArgument(input).log();
    currentInput = input;
    final byte[] dagSeed =
        DirectAcyclicGraphSeed.dagSeed(currentInput.getBlockNumber(), epochCalculator);
    getWorkResult =
        new String[] {
          currentInput.getPrePowHash().toHexString(),
          Bytes.wrap(dagSeed).toHexString(),
          currentInput.getTarget().toHexString(),
          Quantity.create(currentInput.getBlockNumber())
        };
  }

  @Override
  public void setSubmitCallback(final Function<PoWSolution, Boolean> submitSolutionCallback) {
    this.submitCallback = submitSolutionCallback;
  }
}

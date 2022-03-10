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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.DirectAcyclicGraphSeed;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Protocol using JSON-RPC HTTP methods to provide getWork/submitWork methods. */
public class GetWorkProtocol implements StratumProtocol {
  private static final Logger LOG = LoggerFactory.getLogger(GetWorkProtocol.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String CRLF = "\r\n";

  private final EpochCalculator epochCalculator;
  private volatile PoWSolverInputs currentInput;
  private Function<PoWSolution, Boolean> submitCallback;
  private String[] getWorkResult;

  public GetWorkProtocol(final MiningCoordinator miningCoordinator) {
    if (miningCoordinator instanceof PoWMiningCoordinator) {
      this.epochCalculator = ((PoWMiningCoordinator) miningCoordinator).getEpochCalculator();
    } else {
      this.epochCalculator = new EpochCalculator.DefaultEpochCalculator();
    }
  }

  private JsonNode readMessage(final String message) {
    int bodyIndex = message.indexOf(CRLF + CRLF);
    if (bodyIndex == -1) {
      return null;
    }
    if (!message.startsWith("POST / HTTP")) {
      return null;
    }
    String body = message.substring(bodyIndex);
    try {
      return mapper.readTree(body);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  @Override
  public boolean maybeHandle(final String initialMessage, final StratumConnection conn) {
    JsonNode message = readMessage(initialMessage);
    if (message == null) {
      return false;
    }
    JsonNode methodNode = message.get("method");
    if (methodNode != null) {
      String method = methodNode.textValue();
      if ("eth_getWork".equals(method) || "eth_submitWork".equals(method)) {
        JsonNode idNode = message.get("id");
        boolean canHandle = idNode != null && idNode.isInt();
        handle(conn, initialMessage);
        return canHandle;
      }
    }
    return false;
  }

  @Override
  public void onClose(final StratumConnection conn) {}

  @Override
  public void handle(final StratumConnection conn, final String message) {
    JsonNode jsonrpcMessage = readMessage(message);
    if (jsonrpcMessage == null) {
      LOG.warn("Invalid message {}", message);
      conn.close();
      return;
    }
    JsonNode methodNode = jsonrpcMessage.get("method");
    JsonNode idNode = jsonrpcMessage.get("id");
    if (methodNode == null || idNode == null) {
      LOG.warn("Invalid message {}", message);
      conn.close();
      return;
    }
    String method = methodNode.textValue();
    if ("eth_getWork".equals(method)) {
      JsonRpcSuccessResponse response =
          new JsonRpcSuccessResponse(idNode.intValue(), getWorkResult);
      try {
        String responseMessage = mapper.writeValueAsString(response);
        conn.send(
            "HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nKeep-Alive: timeout=5, max=1000\r\nContent-Length: "
                + responseMessage.length()
                + CRLF
                + CRLF
                + responseMessage);
      } catch (JsonProcessingException e) {
        LOG.warn("Error sending work", e);
        conn.close();
      }
    } else if ("eth_submitWork".equals(method)) {
      JsonNode paramsNode = jsonrpcMessage.get("params");
      if (paramsNode == null || paramsNode.size() != 3) {
        LOG.warn("Invalid eth_submitWork params {}", message);
        conn.close();
        return;
      }
      final PoWSolution solution =
          new PoWSolution(
              Bytes.fromHexString(paramsNode.get(0).textValue()).getLong(0),
              Hash.fromHexString(paramsNode.get(2).textValue()),
              null,
              Bytes.fromHexString(paramsNode.get(1).textValue()));
      final boolean result = submitCallback.apply(solution);
      try {
        String resultMessage =
            mapper.writeValueAsString(new JsonRpcSuccessResponse(idNode.intValue(), result));
        conn.send(
            "HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nKeep-Alive: timeout=5, max=1000\r\nContent-Length: "
                + resultMessage.length()
                + CRLF
                + CRLF
                + resultMessage);
      } catch (JsonProcessingException e) {
        LOG.warn("Error accepting solution work", e);
        conn.close();
      }
    } else {
      LOG.warn("Unknown method {}", method);
      conn.close();
    }
  }

  @Override
  public void setCurrentWorkTask(final PoWSolverInputs input) {
    debugLambda(LOG, "setting current stratum work task {}", input::toString);
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

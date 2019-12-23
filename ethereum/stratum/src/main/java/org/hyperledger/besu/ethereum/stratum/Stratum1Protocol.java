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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.DirectAcyclicGraphSeed;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolution;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Implementation of the stratum+tcp protocol.
 *
 * <p>This protocol allows miners to submit EthHash solutions over a persistent TCP connection.
 */
public class Stratum1Protocol implements StratumProtocol {
  private static final Logger LOG = getLogger();
  private static final JsonMapper mapper = new JsonMapper();
  private static final String STRATUM_1 = "EthereumStratum/1.0.0";

  private static String createSubscriptionID() {
    byte[] subscriptionBytes = new byte[16];
    new Random().nextBytes(subscriptionBytes);
    return Bytes.wrap(subscriptionBytes).toShortHexString();
  }

  private final String extranonce;
  private EthHashSolverInputs currentInput;
  private Function<EthHashSolution, Boolean> submitCallback;
  private final Supplier<String> jobIdSupplier;
  private final Supplier<String> subscriptionIdCreator;
  private final List<StratumConnection> activeConnections = new ArrayList<>();

  public Stratum1Protocol(final String extranonce) {
    this(
        extranonce,
        () -> {
          Bytes timeValue = Bytes.minimalBytes(Instant.now().toEpochMilli());
          return timeValue.slice(timeValue.size() - 4, 4).toShortHexString();
        },
        Stratum1Protocol::createSubscriptionID);
  }

  Stratum1Protocol(
      final String extranonce,
      final Supplier<String> jobIdSupplier,
      final Supplier<String> subscriptionIdCreator) {
    this.extranonce = extranonce;
    this.jobIdSupplier = jobIdSupplier;
    this.subscriptionIdCreator = subscriptionIdCreator;
  }

  @Override
  public boolean canHandle(final String initialMessage, final StratumConnection conn) {
    final JsonRpcRequest requestBody;
    try {
      requestBody = new JsonObject(initialMessage).mapTo(JsonRpcRequest.class);
    } catch (IllegalArgumentException e) {
      LOG.debug(e.getMessage(), e);
      return false;
    }
    if (!"mining.subscribe".equals(requestBody.getMethod())) {
      LOG.debug("Invalid first message method: {}", requestBody.getMethod());
      return false;
    }
    try {
      String notify =
          mapper.writeValueAsString(
              new JsonRpcSuccessResponse(
                  requestBody.getId(),
                  new Object[] {
                    new String[] {
                      "mining.notify",
                      subscriptionIdCreator.get(), // subscription ID, never reused.
                      STRATUM_1
                    },
                    extranonce
                  }));
      conn.send(notify + "\n");
    } catch (JsonProcessingException e) {
      LOG.debug(e.getMessage(), e);
      conn.close(null);
    }
    return true;
  }

  private void registerConnection(final StratumConnection conn) {
    activeConnections.add(conn);
    if (currentInput != null) {
      sendNewWork(conn);
    }
  }

  private void sendNewWork(final StratumConnection conn) {
    byte[] dagSeed = DirectAcyclicGraphSeed.dagSeed(currentInput.getBlockNumber());
    Object[] params =
        new Object[] {
          jobIdSupplier.get(),
          Bytes.wrap(currentInput.getPrePowHash()).toHexString(),
          Bytes.wrap(dagSeed).toHexString(),
          currentInput.getTarget().toBytes().toHexString(),
          true
        };
    JsonRpcRequest req = new JsonRpcRequest("2.0", "mining.notify", params);
    try {
      conn.send(mapper.writeValueAsString(req) + "\n");
    } catch (JsonProcessingException e) {
      LOG.debug(e.getMessage(), e);
    }
  }

  @Override
  public void onClose(final StratumConnection conn) {
    activeConnections.remove(conn);
  }

  @Override
  public void handle(final StratumConnection conn, final String message) {
    try {
      JsonRpcRequest req = new JsonObject(message).mapTo(JsonRpcRequest.class);
      if ("mining.authorize".equals(req.getMethod())) {
        handleMiningAuthorize(conn, req);
      } else if ("mining.submit".equals(req.getMethod())) {
        handleMiningSubmit(conn, req);
      }
    } catch (IllegalArgumentException | IOException e) {
      LOG.debug(e.getMessage(), e);
      conn.close(null);
    }
  }

  private void handleMiningSubmit(final StratumConnection conn, final JsonRpcRequest message)
      throws IOException {
    LOG.debug("Miner submitted solution {}", message);
    boolean result = false;
    final EthHashSolution solution =
        new EthHashSolution(
            Bytes.fromHexString(message.getRequiredParameter(2, String.class)).getLong(0),
            Hash.fromHexString(message.getRequiredParameter(4, String.class)),
            Bytes.fromHexString(message.getRequiredParameter(3, String.class)).toArrayUnsafe());
    if (Arrays.equals(currentInput.getPrePowHash(), solution.getPowHash())) {
      result = submitCallback.apply(solution);
    }

    String response =
        mapper.writeValueAsString(new JsonRpcSuccessResponse(message.getId(), result));
    conn.send(response + "\n");
  }

  private void handleMiningAuthorize(final StratumConnection conn, final JsonRpcRequest message)
      throws IOException {
    // discard message contents as we don't care for username/password.
    // send confirmation
    String confirm = mapper.writeValueAsString(new JsonRpcSuccessResponse(message.getId(), true));
    conn.send(confirm + "\n");
    // ready for work.
    registerConnection(conn);
  }

  @Override
  public void setCurrentWorkTask(final EthHashSolverInputs input) {
    this.currentInput = input;
    LOG.debug("Sending new work to miners: {}", input);
    for (StratumConnection conn : activeConnections) {
      sendNewWork(conn);
    }
  }

  @Override
  public void setSubmitCallback(final Function<EthHashSolution, Boolean> submitSolutionCallback) {
    this.submitCallback = submitSolutionCallback;
  }
}

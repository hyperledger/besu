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
package org.hyperledger.besu.ethereum.blockcreation.stratum;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.mainnet.DirectAcyclicGraphSeed;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of the stratum+tcp protocol.
 *
 * <p>This protocol allows miners to submit nonces over a persistent TCP connection.
 */
public class Stratum1Protocol implements StratumProtocol {

  private EthHashSolverInputs currentInput;
  private Function<Long, Boolean> submitCallback;
  private Supplier<String> jobIdSupplier =
      () -> {
        BytesValue timeValue = BytesValues.toMinimalBytes(Instant.now().toEpochMilli());
        return timeValue.slice(timeValue.size() - 4, 4).toUnprefixedString();
      };

  @JsonIgnoreProperties("jsonrpc")
  private static final class MinerMessage {

    private int id;
    private String method;
    private String[] params;

    @JsonCreator
    public MinerMessage(
        final @JsonProperty("id") int id,
        final @JsonProperty("method") String method,
        final @JsonProperty("params") String[] params) {
      this.id = id;
      this.method = method;
      this.params = params;
    }

    @JsonProperty("id")
    public int getId() {
      return id;
    }

    @JsonProperty("method")
    public String getMethod() {
      return method;
    }

    @JsonProperty("params")
    public String[] getParams() {
      return params;
    }
  }

  @JsonPropertyOrder({"id", "jsonrpc", "result", "error"})
  private static final class MinerNotifyResponse {

    private final int id;
    private final Object result;
    private final Object[] error;

    public MinerNotifyResponse(final int id, final Object result, final Object[] error) {
      this.id = id;
      this.result = result;
      this.error = error;
    }

    @JsonProperty("id")
    public int getId() {
      return id;
    }

    @JsonProperty("result")
    public Object getResult() {
      return result;
    }

    @JsonProperty("error")
    public Object[] getError() {
      return error;
    }

    @JsonProperty("jsonrpc")
    public String getJsonrpc() {
      return "2.0";
    }
  }

  @JsonPropertyOrder({"id", "method", "jsonrpc", "params"})
  private static final class MinerNewWork {

    private final String jobId;
    private final EthHashSolverInputs input;

    public MinerNewWork(final String jobId, final EthHashSolverInputs input) {
      this.jobId = jobId;
      this.input = input;
    }

    @JsonProperty("id")
    public String getId() {
      return null;
    }

    @JsonProperty("method")
    public String getMethod() {
      return "mining.notify";
    }

    @JsonProperty("params")
    public Object[] getParams() {
      final byte[] dagSeed = DirectAcyclicGraphSeed.dagSeed(input.getBlockNumber());
      return new Object[] {
        jobId,
        BytesValue.wrap(input.getPrePowHash()).getHexString(),
        BytesValue.wrap(dagSeed).getHexString(),
        input.getTarget().toHexString(),
        true
      };
    }

    @JsonProperty("jsonrpc")
    public String getJsonrpc() {
      return "2.0";
    }
  }

  private static final Logger logger = getLogger();
  private static final JsonMapper mapper = new JsonMapper();

  private final List<StratumConnection> activeConnections = new ArrayList<>();

  public Stratum1Protocol() {}

  Stratum1Protocol(final Supplier<String> jobIdSupplier) {
    this.jobIdSupplier = jobIdSupplier;
  }

  @Override
  public boolean register(final byte[] initialMessage, final StratumConnection conn) {
    try {
      MinerMessage message = mapper.readValue(initialMessage, MinerMessage.class);
      if (!"mining.subscribe".equals(message.getMethod())
          || message.getParams().length < 2
          || !message.getParams()[1].equals("EthereumStratum/1.0.0")) {
        logger.debug("Invalid first message method: {}", message.getMethod());
        return false;
      }
      try {
        String notify =
            mapper.writeValueAsString(
                new MinerNotifyResponse(
                    message.getId(),
                    new Object[] {
                      new String[] {
                        "mining.notify",
                        "ae6812eb4cd7735a302a8a9dd95cf71f", // subscription ID, never reused.
                        "EthereumStratum/1.0.0"
                      },
                      "080c" // TODO. For now we use a fixed extranounce.
                    },
                    null));
        conn.send(notify + "\n");
      } catch (JsonProcessingException e) {
        logger.debug(e.getMessage(), e);
        conn.close(null);
      }
      return true;
    } catch (IOException e) {
      logger.debug(e.getMessage(), e);
      return false;
    }
  }

  private void registerConnection(final StratumConnection conn) {
    activeConnections.add(conn);
    if (currentInput != null) {
      sendNewWork(conn);
    }
  }

  private void sendNewWork(final StratumConnection conn) {
    MinerNewWork newWork = new MinerNewWork(jobIdSupplier.get(), currentInput);
    try {
      conn.send(mapper.writeValueAsString(newWork));
    } catch (JsonProcessingException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  @Override
  public void onClose(final StratumConnection conn) {
    activeConnections.remove(conn);
  }

  @Override
  public void handle(final StratumConnection conn, final byte[] bytes) {
    try {
      MinerMessage message = mapper.readValue(bytes, MinerMessage.class);
      if ("mining.authorize".equals(message.getMethod())) {
        // discard message contents as we don't care for username/password.
        // send confirmation
        String confirm =
            mapper.writeValueAsString(new MinerNotifyResponse(message.getId(), true, null));
        conn.send(confirm + "\n");
        // ready for work.
        registerConnection(conn);
      } else if ("mining.submit".equals(message.getMethod())) {
        long nonce = BytesValue.fromHexString(message.getParams()[2]).getLong(0);
        if (submitCallback.apply(nonce)) {
          String accept =
              mapper.writeValueAsString(new MinerNotifyResponse(message.getId(), true, null));
          conn.send(accept + "\n");
          return;
        }
      }
      String reject =
          mapper.writeValueAsString(new MinerNotifyResponse(message.getId(), false, null));
      conn.send(reject + "\n");
    } catch (IOException e) {
      logger.debug(e.getMessage(), e);
      conn.close(null);
    }
  }

  @Override
  public void solveFor(final EthHashSolverInputs input) {
    this.currentInput = input;
    logger.debug("Sending new work to miners: {}", input);
    for (StratumConnection conn : activeConnections) {
      sendNewWork(conn);
    }
  }

  @Override
  public void setSubmitCallback(final Function<Long, Boolean> submitSolutionCallback) {
    this.submitCallback = submitSolutionCallback;
  }
}

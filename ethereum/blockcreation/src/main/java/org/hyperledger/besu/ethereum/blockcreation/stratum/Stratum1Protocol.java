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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolution;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;
import org.hyperledger.besu.ethereum.mainnet.EthHasher;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.uint.UInt256;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.Logger;

public class Stratum1Protocol implements StratumProtocol {

  private String currentJobId;
  private EthHashSolverInputs currentInput;
  private Function<EthHashSolution, Boolean> submitCallback;
  private Supplier<String> jobIdSupplier =
      () -> {
        BytesValue timeValue = BytesValues.toMinimalBytes(Instant.now().toEpochMilli());
        return timeValue.slice(timeValue.size() - 4, 4).toUnprefixedString();
      };

  /**
   * { "id": 1, "method": "mining.subscribe", "params": [ "MinerName/1.0.0", "EthereumStratum/1.0.0"
   * ] }\n
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class MinerMessage {

    private int id;

    private String method;

    private String[] params;

    public int getId() {
      return id;
    }

    public void setId(final int id) {
      this.id = id;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(final String method) {
      this.method = method;
    }

    public String[] getParams() {
      return params;
    }

    public void setParams(final String[] params) {
      this.params = params;
    }
  }

  /**
   * { "id": 1, "result": [ [ "mining.notify", "ae6812eb4cd7735a302a8a9dd95cf71f",
   * "EthereumStratum/1.0.0" ], "080c" ], "error": null }\n
   */
  private static final class MinerNotifyResponse {

    private final int id;
    private final Object result;
    private final Object[] error;

    public MinerNotifyResponse(final int id, final Object result, final Object[] error) {
      this.id = id;
      this.result = result;
      this.error = error;
    }

    public int getId() {
      return id;
    }

    public Object getResult() {
      return result;
    }

    public Object[] getError() {
      return error;
    }

    public String getJsonrpc() {
      return "2.0";
    }
  }

  /**
   * { "id": null, "method": "mining.notify", "params": [ "bf0488aa",
   * "abad8f99f3918bf903c6a909d9bbc0fdfa5a2f4b9cb1196175ec825c6610126c",
   * "fc12eb20c58158071c956316cdcd12a22dd8bf126ac4aee559f0ffe4df11f279", true ] }\n
   */
  private static final class MinerNewWork {

    private final String jobId;
    private final EthHashSolverInputs input;

    public MinerNewWork(final String jobId, final EthHashSolverInputs input) {
      this.jobId = jobId;
      this.input = input;
    }

    public String getId() {
      return null;
    }

    public String getMethod() {
      return "mining.notify";
    }

    /**
     * First parameter of params array is job ID (must be HEX number of any size). Second parameter
     * is seedhash. Seedhash is sent with every job to support possible multipools, which may switch
     * between coins quickly. Third parameter is headerhash. Last parameter is boolean cleanjobs. If
     * set to true, then miner needs to clear queue of jobs and immediatelly start working on new
     * provided job, because all old jobs shares will result with stale share error.
     */
    public Object[] getParams() {
      return new Object[] {
        jobId,
        "abad8f99f3918bf903c6a909d9bbc0fdfa5a2f4b9cb1196175ec825c6610126c", // hardcode seed hash.
        BytesValue.wrap(input.getPrePowHash()).toUnprefixedString()
      };
    }

    public String getJsonrpc() {
      return "2.0";
    }
  }

  private static final Logger logger = getLogger();
  private static final JsonMapper mapper = new JsonMapper();
  private static final EthHasher ethHasher = new EthHasher.Light();

  private final List<StratumConnection> activeConnections = new ArrayList<>();

  public Stratum1Protocol() {}

  Stratum1Protocol(final Supplier<String> jobIdSupplier) {
    this.jobIdSupplier = jobIdSupplier;
  }

  @Override
  public boolean register(final byte[] initialMessage, final StratumConnection conn) {
    try {
      MinerMessage message = mapper.readValue(initialMessage, MinerMessage.class);
      if (!"mining.subscribe".equals(message.getMethod())) {
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
    MinerNewWork newWork = new MinerNewWork(currentJobId, currentInput);
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
        String jobId = message.getParams()[1];
        long nonce = BytesValue.fromHexString(message.getParams()[2]).getLong(0);
        if (currentJobId != null && currentJobId.equals(jobId)) {
          byte[] hashBuffer = new byte[64];
          ethHasher.hash(
              hashBuffer, nonce, currentInput.getBlockNumber(), currentInput.getPrePowHash());
          final UInt256 x = UInt256.wrap(Bytes32.wrap(hashBuffer, 32));
          if (x.compareTo(currentInput.getTarget()) <= 0) {
            final Hash mixedHash =
                Hash.wrap(Bytes32.leftPad(BytesValue.wrap(hashBuffer).slice(0, Bytes32.SIZE)));
            EthHashSolution solution =
                new EthHashSolution(nonce, mixedHash, currentInput.getPrePowHash());
            if (submitCallback.apply(solution)) {
              String accept =
                  mapper.writeValueAsString(new MinerNotifyResponse(message.getId(), true, null));
              conn.send(accept + "\n");
              return;
            }
          }
        }
        String accept =
            mapper.writeValueAsString(new MinerNotifyResponse(message.getId(), false, null));
        conn.send(accept + "\n");
      }
    } catch (IOException e) {
      logger.debug(e.getMessage(), e);
      conn.close(null);
    }
  }

  @Override
  public void solveFor(final EthHashSolverInputs input) {
    this.currentJobId = jobIdSupplier.get();
    this.currentInput = input;
    logger.debug("Sending new work to miners: {}", input);
    for (StratumConnection conn : activeConnections) {
      sendNewWork(conn);
    }
  }

  @Override
  public void setSubmitCallback(final Function<EthHashSolution, Boolean> submitSolutionCallback) {
    this.submitCallback = submitSolutionCallback;
  }
}

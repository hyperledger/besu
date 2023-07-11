/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.evmtool;

import static org.hyperledger.besu.evmtool.T8nExecutor.extractTransactions;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestEnv;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evmtool.T8nExecutor.RejectedTransaction;
import org.hyperledger.besu.util.LogConfigurator;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(
    name = "t8n-server",
    description = "Run Ethereum State Test server",
    versionProvider = VersionProvider.class)
public class T8nServerSubCommand implements Runnable {

  @CommandLine.Option(
      names = {"--host"},
      description = "Host to bind to")
  private String host = "localhost";

  @CommandLine.Option(
      names = {"--port"},
      description = "Port to bind to")
  private int port = 3000;

  @Override
  public void run() {
    LogConfigurator.setLevel("", "OFF");
    Vertx.vertx()
        .createHttpServer()
        .requestHandler(
            req ->
                req.bodyHandler(
                    body -> {
                      ObjectMapper objectMapper = JsonUtils.createObjectMapper();
                      JsonObject t8nRequest = body.toJsonObject();
                      JsonObject state = t8nRequest.getJsonObject("state");
                      String fork = state.getString("fork");
                      Long chainId = Long.valueOf(state.getString("chainid"));
                      String reward = state.getString("reward");

                      JsonObject input = t8nRequest.getJsonObject("input");
                      ReferenceTestEnv referenceTestEnv =
                          input.getJsonObject("env").mapTo(ReferenceTestEnv.class);
                      ReferenceTestWorldState initialWorldState =
                          input.getJsonObject("alloc").mapTo(ReferenceTestWorldState.class);
                      initialWorldState.persist(null);
                      List<Transaction> transactions = new ArrayList<>();
                      List<RejectedTransaction> rejections = new ArrayList<>();
                      Object txs = input.getValue("txs");
                      if (txs != null) {
                        if (txs instanceof JsonArray txsArray) {
                          extractTransactions(
                              new PrintWriter(System.err, true, StandardCharsets.UTF_8),
                              txsArray.stream().map(s -> (JsonNode) s).iterator(),
                              transactions,
                              rejections);
                        } else if (txs instanceof String tx) {
                          transactions =
                              extractTransactions(
                                  new PrintWriter(System.err, true, StandardCharsets.UTF_8),
                                  List.<JsonNode>of(new TextNode(removeSurrounding("\"", tx)))
                                      .iterator(),
                                  transactions,
                                  rejections);
                        }
                      }

                      final T8nExecutor.T8nResult result =
                          T8nExecutor.runTest(
                              chainId,
                              fork,
                              reward,
                              objectMapper,
                              referenceTestEnv,
                              initialWorldState,
                              transactions,
                              rejections,
                              new T8nExecutor.TracerManager() {
                                @Override
                                public OperationTracer getManagedTracer(
                                    final int txIndex, final Hash txHash) {
                                  return OperationTracer.NO_TRACING;
                                }

                                @Override
                                public void disposeTracer(final OperationTracer tracer) {
                                  // No output streams to dispose of
                                }
                              });

                      ObjectNode outputObject = objectMapper.createObjectNode();
                      outputObject.set("alloc", result.allocObject());
                      outputObject.set("body", result.bodyBytes());
                      outputObject.set("result", result.resultObject());

                      try {
                        req.response()
                            .putHeader("Content-Type", "application/json")
                            .end(
                                objectMapper
                                    .writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(outputObject));
                      } catch (JsonProcessingException e) {
                        req.response().setStatusCode(500).end(e.getMessage());
                      }
                    }))
        .listen(port, host)
        .onSuccess(
            server -> System.out.println("Transition server listening on " + server.actualPort()))
        .onFailure(
            err -> System.err.println("Failed to start transition server: " + err.getMessage()));
  }

  private static String removeSurrounding(final String delimiter, final String message) {
    if (message.startsWith(delimiter) && message.endsWith(delimiter)) {
      return message.substring(delimiter.length(), message.length() - delimiter.length());
    }
    return message;
  }
}

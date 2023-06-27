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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestEnv;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.util.LogConfigurator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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
                      List<Transaction> transactions = Collections.emptyList();
                      Object txs = input.getValue("txs");
                      if (txs != null) {
                        if (txs instanceof JsonArray txsArray) {
                          transactions = extractTransactions(txsArray);
                        } else if (txs instanceof String tx) {
                          transactions =
                              extractTransactions(new JsonArray().add(removeSurrounding("\"", tx)));
                        }
                      }

                      ObjectMapper objectMapper = JsonUtils.createObjectMapper();
                      final T8nExecutor.T8nResult result =
                          T8nExecutor.runTest(
                              chainId,
                              fork,
                              reward,
                              objectMapper,
                              referenceTestEnv,
                              initialWorldState,
                              transactions,
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

  private List<Transaction> extractTransactions(final JsonArray jsonArray) {
    List<Transaction> transactions = new ArrayList<>();
    for (int i = 0; i < jsonArray.size(); i++) {
      Object rawTx = jsonArray.getValue(i);
      if (rawTx instanceof String txNode) {
        BytesValueRLPInput rlpInput = new BytesValueRLPInput(Bytes.fromHexString(txNode), false);
        rlpInput.enterList();
        while (!rlpInput.isEndOfCurrentList()) {
          Transaction tx = Transaction.readFrom(rlpInput);
          transactions.add(tx);
        }
      } else if (rawTx instanceof JsonObject txNode) {
        if (txNode.containsKey("txBytes")) {
          JsonObject txBytesNode = txNode.getJsonObject("txBytes");
          Transaction tx =
              Transaction.readFrom(Bytes.fromHexString(txBytesNode.getString("txbytes")));
          transactions.add(tx);
        } else {
          Transaction.Builder builder = Transaction.builder();
          int type = Bytes.fromHexStringLenient(txNode.getString("type")).toInt();
          TransactionType transactionType = TransactionType.of(type == 0 ? 0xf8 : type);
          builder.type(transactionType);
          builder.nonce(Bytes.fromHexStringLenient(txNode.getString("nonce")).toLong());
          builder.gasPrice(Wei.fromHexString(txNode.getString("gasPrice")));
          builder.gasLimit(Bytes.fromHexStringLenient(txNode.getString("gas")).toLong());
          builder.value(Wei.fromHexString(txNode.getString("value")));
          builder.payload(Bytes.fromHexString(txNode.getString("input")));
          if (txNode.containsKey("to")) {
            builder.to(Address.fromHexString(txNode.getString("to")));
          }

          if (transactionType.requiresChainId()
              || !txNode.containsKey("protected")
              || txNode.getBoolean("protected")) {
            // chainid if protected
            builder.chainId(
                new BigInteger(
                    1, Bytes.fromHexStringLenient(txNode.getString("chainId")).toArrayUnsafe()));
          }

          if (txNode.containsKey("secretKey")) {
            SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
            KeyPair keys =
                signatureAlgorithm.createKeyPair(
                    signatureAlgorithm.createPrivateKey(
                        Bytes32.fromHexString(txNode.getString("secretKey"))));

            transactions.add(builder.signAndBuild(keys));
          } else {
            builder.signature(
                SignatureAlgorithmFactory.getInstance()
                    .createSignature(
                        Bytes.fromHexString(txNode.getString("r")).toUnsignedBigInteger(),
                        Bytes.fromHexString(txNode.getString("s")).toUnsignedBigInteger(),
                        Bytes.fromHexString(txNode.getString("v"))
                            .toUnsignedBigInteger()
                            .subtract(Transaction.REPLAY_UNPROTECTED_V_BASE)
                            .byteValueExact()));
            transactions.add(builder.build());
          }
        }
      }
    }
    return transactions;
  }

  private static String removeSurrounding(final String delimiter, final String message) {
    if (message.startsWith(delimiter) && message.endsWith(delimiter)) {
      return message.substring(delimiter.length(), message.length() - delimiter.length());
    }
    return message;
  }
}

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

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class ExecutionEngineJsonRpcMethod implements JsonRpcMethod {
    public enum ExecutionStatus {
        VALID,
        INVALID,
        KNOWN;
    }

    public enum ConsensusStatus {
        VALID,
        INVALID;

        public boolean equalsIgnoreCase(final String status) {
            return name().equalsIgnoreCase(status);
        }
    }
    
  private final Vertx syncVertx;
  private static final Logger LOG = LogManager.getLogger();
  protected final MergeContext mergeContext;
  protected final ProtocolContext protocolContext;

  protected ExecutionEngineJsonRpcMethod(final Vertx vertx, final ProtocolContext protocolContext) {
    this.syncVertx = vertx;
    this.protocolContext = protocolContext;
    this.mergeContext = protocolContext.getConsensusContext(MergeContext.class);
  }

  @Override
  public final JsonRpcResponse response(final JsonRpcRequestContext request) {

    final CompletableFuture<JsonRpcResponse> cf = new CompletableFuture<>();

    syncVertx.<JsonRpcResponse>executeBlocking(
        z -> {
          LOG.info("consensus JSON-RPC request {}", this.getName());
          z.tryComplete(syncResponse(request));
        },
        true,
        resp ->
            cf.complete(
                resp.otherwise(
                        t -> {
                          LOG.error("failed to exec consensus method " + this.getName(), t);
                          return new JsonRpcErrorResponse(
                              request.getRequest().getId(), JsonRpcError.INVALID_REQUEST);
                        })
                    .result()));

    try {
      return cf.get();
    } catch (InterruptedException e) {
      return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.TIMEOUT_ERROR);
    } catch (ExecutionException e) {
      return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INTERNAL_ERROR);
      // e.printStackTrace();
    }
  }

  public abstract JsonRpcResponse syncResponse(final JsonRpcRequestContext request);
}

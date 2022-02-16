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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ExecutionEngineJsonRpcMethod implements JsonRpcMethod {
  public enum ExecutionStatus {
    VALID,
    INVALID,
    SYNCING,
    INVALID_BLOCK_HASH,
    INVALID_TERMINAL_BLOCK;
  }

  public enum ForkChoiceStatus {
    VALID,
    INVALID,
    SYNCING,
    INVALID_TERMINAL_BLOCK;

    public boolean equalsIgnoreCase(final String status) {
      return name().equalsIgnoreCase(status);
    }
  }

  private final Vertx syncVertx;
  private static final Logger LOG = LoggerFactory.getLogger(ExecutionEngineJsonRpcMethod.class);
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
          LOG.debug(
              "execution engine JSON-RPC request {} {}",
              this.getName(),
              request.getRequest().getParams());
          z.tryComplete(syncResponse(request));
        },
        true,
        resp ->
            cf.complete(
                resp.otherwise(
                        t -> {
                          LOG.debug("failed to exec consensus method {}", t, this.getName());
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
    }
  }

  public abstract JsonRpcResponse syncResponse(final JsonRpcRequestContext request);
}

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
package org.hyperledger.besu.enclave;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

public class VertxRequestTransmitter implements RequestTransmitter {

  private final HttpClient client;
  private static final long REQUEST_TIMEOUT_MS = 1000L;

  public VertxRequestTransmitter(final HttpClient httpClient) {
    this.client = httpClient;
  }

  @Override
  public <T> T post(
      final String mediaType,
      final String content,
      final String endpoint,
      final ResponseBodyHandler<T> responseHandler) {
    return sendRequest(HttpMethod.POST, mediaType, content, endpoint, responseHandler);
  }

  @Override
  public <T> T get(
      final String mediaType,
      final String content,
      final String endpoint,
      final ResponseBodyHandler<T> responseHandler) {
    return sendRequest(HttpMethod.GET, mediaType, content, endpoint, responseHandler);
  }

  protected <T> T sendRequest(
      final HttpMethod method,
      final String mediaType,
      final String content,
      final String endpoint,
      final ResponseBodyHandler<T> responseHandler) {
    try {
      final CompletableFuture<T> result = new CompletableFuture<>();
      client
          .request(method, endpoint)
          .handler(response -> handleResponse(response, responseHandler, result))
          .putHeader(HttpHeaders.CONTENT_TYPE, mediaType)
          .setTimeout(REQUEST_TIMEOUT_MS)
          .exceptionHandler(result::completeExceptionally)
          .setChunked(false)
          .end(content);
      return result.get();
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof EnclaveException) {
        throw (EnclaveException) e.getCause();
      }
      throw new EnclaveException("Failure during reception", e);
    } catch (final InterruptedException e) {
      throw new EnclaveException("Task interrupted while waiting for Enclave response.", e);
    } catch (final Exception e) {
      throw new EnclaveException("Other error", e);
    }
  }

  private <T> void handleResponse(
      final HttpClientResponse response,
      final ResponseBodyHandler<T> responseHandler,
      final CompletableFuture<T> future) {
    response.bodyHandler(
        responseBody -> {
          try {
            future.complete(
                responseHandler.convertResponse(response.statusCode(), responseBody.getBytes()));
          } catch (final Exception e) {
            future.completeExceptionally(e);
          }
        });
  }
}

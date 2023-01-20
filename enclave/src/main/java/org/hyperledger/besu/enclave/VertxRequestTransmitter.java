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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

/** The Vertx request transmitter. */
public class VertxRequestTransmitter implements RequestTransmitter {

  private static final String APPLICATION_JSON = "application/json";
  private final HttpClient client;
  private static final long REQUEST_TIMEOUT_MS = 5000L;

  /**
   * Instantiates a new Vertx request transmitter.
   *
   * @param httpClient the http client
   */
  public VertxRequestTransmitter(final HttpClient httpClient) {
    this.client = httpClient;
  }

  @Override
  public <T> T post(
      final String contentType,
      final String content,
      final String endpoint,
      final ResponseBodyHandler<T> responseHandler) {
    return sendRequest(
        HttpMethod.POST,
        Optional.of(contentType),
        Optional.of(content),
        endpoint,
        responseHandler,
        false);
  }

  @Override
  public <T> T get(
      final String contentType,
      final String content,
      final String endpoint,
      final ResponseBodyHandler<T> responseHandler,
      final boolean withAcceptJsonHeader) {
    return sendRequest(
        HttpMethod.GET,
        Optional.ofNullable(contentType),
        Optional.ofNullable(content),
        endpoint,
        responseHandler,
        withAcceptJsonHeader);
  }

  /**
   * Send request operation.
   *
   * @param <T> the type parameter
   * @param method the method
   * @param contentType the content type
   * @param content the content
   * @param endpoint the endpoint
   * @param responseHandler the response handler
   * @param withAcceptJsonHeader the with accept json header
   * @return the t
   */
  protected <T> T sendRequest(
      final HttpMethod method,
      final Optional<String> contentType,
      final Optional<String> content,
      final String endpoint,
      final ResponseBodyHandler<T> responseHandler,
      final boolean withAcceptJsonHeader) {
    try {
      final CompletableFuture<T> result = new CompletableFuture<>();
      RequestOptions options = new RequestOptions();
      options.setTimeout(REQUEST_TIMEOUT_MS);
      options.setMethod(method);
      options.setURI(endpoint);
      if (withAcceptJsonHeader) {
        // this is needed when using Tessera GET /transaction/{hash} to choose the right RPC
        options.putHeader(HttpHeaderNames.ACCEPT, APPLICATION_JSON);
      }
      contentType.ifPresent(ct -> options.putHeader(HttpHeaders.CONTENT_TYPE, ct));

      final Future<HttpClientRequest> request = client.request(options);
      request
          .onComplete(
              newRequest -> {
                if (content.isPresent()) {
                  request.result().end(content.get());
                } else {
                  request.result().end();
                }
                request
                    .result()
                    .send(
                        h -> {
                          if (h.succeeded()) {
                            handleResponse(
                                newRequest.result().response().result(), responseHandler, result);
                          } else {
                            result.completeExceptionally(h.cause());
                          }
                        });
              })
          .onFailure(result::completeExceptionally);

      return result.get();
    } catch (final ExecutionException | InterruptedException e) {
      if (e.getCause() instanceof EnclaveClientException) {
        throw (EnclaveClientException) e.getCause();
      } else if (e.getCause() instanceof EnclaveServerException) {
        throw (EnclaveServerException) e.getCause();
      }
      throw new EnclaveIOException("Enclave Communication Failed", e);
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

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

import org.hyperledger.besu.enclave.types.ErrorResponse;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

public class VertxRequestTransmitter implements RequestTransmitter {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final HttpClient client;
  private static final long REQUEST_TIMEOUT_MS = 500L;

  public VertxRequestTransmitter(final HttpClient httpClient) {
    this.client = httpClient;
  }

  @Override
  public <T> T postRequest(
      final String mediaType,
      final Object content,
      final String endpoint,
      final Class<T> responseType) {
    return sendRequest(HttpMethod.POST, mediaType, content, endpoint, responseType);
  }

  @Override
  public <T> T getRequest(
      final String mediaType,
      final Object content,
      final String endpoint,
      final Class<T> responseType) {
    return sendRequest(HttpMethod.GET, mediaType, content, endpoint, responseType);
  }

  protected <T> T sendRequest(
      final HttpMethod method,
      final String mediaType,
      final Object content,
      final String endpoint,
      final Class<T> responseType) {
    try {
      final CompletableFuture<T> result = new CompletableFuture<>();
      client
          .request(method, endpoint)
          .handler(response -> handleResponse(response, responseType, result))
          .putHeader(HttpHeaders.CONTENT_TYPE, mediaType)
          .setTimeout(REQUEST_TIMEOUT_MS)
          .exceptionHandler(result::completeExceptionally)
          .setChunked(false)
          .end(objectMapper.writeValueAsString(content));
      return result.get();
    } catch (final JsonProcessingException e) {
      throw new EnclaveException("Failed to serialise request to json body.");
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
      final Class<T> responseType,
      final CompletableFuture<T> future) {
    response.bodyHandler(
        responseBody -> {
          try {
            if (response.statusCode() == 200) {
              final T bodyType = objectMapper.readValue(responseBody.getBytes(), responseType);
              future.complete(bodyType);
            } else {
              final ErrorResponse errorResponse =
                  objectMapper.readValue(responseBody.getBytes(), ErrorResponse.class);
              future.completeExceptionally(new EnclaveException(errorResponse.getError()));
            }
          } catch (final JsonParseException | JsonMappingException e) {
            future.completeExceptionally(
                new EnclaveException("Failed to deserialise received json", e));
          } catch (final IOException e) {
            future.completeExceptionally(new EnclaveException("Decoding Json stream failed", e));
          }
        });
  }
}

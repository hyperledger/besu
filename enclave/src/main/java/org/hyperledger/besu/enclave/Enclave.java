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

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;

import org.hyperledger.besu.enclave.types.CreatePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.DeletePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.ErrorResponse;
import org.hyperledger.besu.enclave.types.FindPrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.AsciiString;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

public class Enclave {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final AsciiString JSON = APPLICATION_JSON;
  private static final AsciiString ORION = AsciiString.of("application/vnd.orion.v1+json");

  private final Vertx vertx;
  private final HttpClient client;

  public Enclave(final URI enclaveUri) {
    final HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setDefaultHost(enclaveUri.getHost());
    if (enclaveUri.getPort() != -1) {
      clientOptions.setDefaultPort(enclaveUri.getPort());
    }

    vertx = Vertx.vertx();
    this.client = vertx.createHttpClient(clientOptions);
  }

  public boolean upCheck() {
    final CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();
    final HttpClientRequest request = client.request(HttpMethod.GET, "/upcheck").setTimeout(100);
    request.exceptionHandler(responseFuture::completeExceptionally);
    request.handler(
        response ->
            response.bodyHandler(
                body -> {
                  if (response.statusCode() == 200) {
                    final String bodyContent = body.toString(StandardCharsets.UTF_8);
                    responseFuture.complete(bodyContent.equals("I'm up!"));
                  }
                }));
    request.end();

    try {
      return responseFuture.get();
    } catch (final Exception e) {
      return false;
    }
  }

  public SendResponse send(final SendRequest content) {
    return postRequest(JSON, content, "/send", SendResponse.class);
  }

  public ReceiveResponse receive(final ReceiveRequest content) {
    return postRequest(ORION, content, "/receive", ReceiveResponse.class);
  }

  public PrivacyGroup createPrivacyGroup(final CreatePrivacyGroupRequest content) {
    return postRequest(JSON, content, "/createPrivacyGroup", PrivacyGroup.class);
  }

  public String deletePrivacyGroup(final DeletePrivacyGroupRequest content) {
    return postRequest(JSON, content, "/deletePrivacyGroup", String.class);
  }

  public PrivacyGroup[] findPrivacyGroup(final FindPrivacyGroupRequest content) {
    return postRequest(JSON, content, "/findPrivacyGroup", PrivacyGroup[].class);
  }

  private <T> T postRequest(
      final AsciiString mediaType,
      final Object content,
      final String endpoint,
      final Class<T> responseType) {

    try {
      final CompletableFuture<T> result = new CompletableFuture<>();
      final HttpClientRequest request = client.request(HttpMethod.POST, endpoint);
      request.handler(response -> handleResponse(response, responseType, result));
      request.putHeader(HttpHeaders.CONTENT_TYPE, mediaType);
      request.exceptionHandler(result::completeExceptionally);
      request.setChunked(false);
      request.end(objectMapper.writeValueAsString(content));
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

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

import org.hyperledger.besu.enclave.RequestTransmitter.ResponseBodyHandler;
import org.hyperledger.besu.enclave.types.GoQuorumReceiveResponse;
import org.hyperledger.besu.enclave.types.GoQuorumSendRequest;
import org.hyperledger.besu.enclave.types.GoQuorumSendSignedRequest;
import org.hyperledger.besu.enclave.types.GoQuorumStoreRawRequest;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.enclave.types.StoreRawResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GoQuorumEnclave {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String JSON = "application/json";

  private final RequestTransmitter requestTransmitter;

  public GoQuorumEnclave(final RequestTransmitter requestTransmitter) {
    this.requestTransmitter = requestTransmitter;
  }

  public boolean upCheck() {
    try {
      final String upcheckResponse =
          requestTransmitter.get(null, null, "/upcheck", this::handleRawResponse, false);
      return upcheckResponse.equals("I'm up!");
    } catch (final Exception e) {
      return false;
    }
  }

  public SendResponse send(
      final byte[] payload, final String privateFrom, final List<String> privateFor) {
    final GoQuorumSendRequest request = new GoQuorumSendRequest(payload, privateFrom, privateFor);
    return post(
        JSON,
        request,
        "/send",
        (statusCode, body) -> handleJsonResponse(statusCode, body, SendResponse.class, 201));
  }

  public SendResponse sendSignedTransaction(
      final byte[] txLookupId, final List<String> privateFor) {
    final GoQuorumSendSignedRequest request = new GoQuorumSendSignedRequest(txLookupId, privateFor);
    return post(
        JSON,
        request,
        "/sendsignedtx",
        (statusCode, body) -> handleJsonResponse(statusCode, body, SendResponse.class, 201));
  }

  public StoreRawResponse storeRaw(final String payload) {
    final GoQuorumStoreRawRequest request = new GoQuorumStoreRawRequest(payload);
    return post(
        JSON,
        request,
        "/storeraw",
        (statusCode, body) -> handleJsonResponse(statusCode, body, StoreRawResponse.class, 200));
  }

  public GoQuorumReceiveResponse receive(final String payloadKey) {
    return get(
        JSON,
        "/transaction/" + URLEncoder.encode(payloadKey, StandardCharsets.UTF_8),
        (statusCode, body) ->
            handleJsonResponse(statusCode, body, GoQuorumReceiveResponse.class, 200));
  }

  private <T> T post(
      final String mediaType,
      final Object content,
      final String endpoint,
      final ResponseBodyHandler<T> responseBodyHandler) {
    final String bodyText;
    try {
      bodyText = objectMapper.writeValueAsString(content);
    } catch (final JsonProcessingException e) {
      throw new EnclaveClientException(400, "Unable to serialize request.");
    }
    return requestTransmitter.post(mediaType, bodyText, endpoint, responseBodyHandler);
  }

  private <T> T get(
      final String mediaType,
      final String endpoint,
      final ResponseBodyHandler<T> responseBodyHandler) {
    final T t = requestTransmitter.get(mediaType, null, endpoint, responseBodyHandler, true);
    return t;
  }

  private <T> T handleJsonResponse(
      final int statusCode,
      final byte[] body,
      final Class<T> responseType,
      final int expectedStatusCode) {

    if (isSuccess(statusCode, expectedStatusCode)) {
      return parseResponse(statusCode, body, responseType);
    } else if (clientError(statusCode)) {
      final String utf8EncodedBody = new String(body, StandardCharsets.UTF_8);
      throw new EnclaveClientException(statusCode, utf8EncodedBody);
    } else {
      final String utf8EncodedBody = new String(body, StandardCharsets.UTF_8);
      throw new EnclaveServerException(statusCode, utf8EncodedBody);
    }
  }

  private <T> T parseResponse(
      final int statusCode, final byte[] body, final Class<T> responseType) {
    try {
      return objectMapper.readValue(body, responseType);
    } catch (final IOException e) {
      final String utf8EncodedBody = new String(body, StandardCharsets.UTF_8);
      throw new EnclaveClientException(statusCode, utf8EncodedBody);
    }
  }

  private boolean clientError(final int statusCode) {
    return statusCode >= 400 && statusCode < 500;
  }

  private boolean isSuccess(final int statusCode, final int expectedStatusCode) {
    return statusCode == expectedStatusCode;
  }

  private String handleRawResponse(final int statusCode, final byte[] body) {
    final String bodyText = new String(body, StandardCharsets.UTF_8);
    if (isSuccess(statusCode, 200)) {
      return bodyText;
    }
    throw new EnclaveClientException(
        statusCode, String.format("Request failed with %d; body={%s}", statusCode, bodyText));
  }
}

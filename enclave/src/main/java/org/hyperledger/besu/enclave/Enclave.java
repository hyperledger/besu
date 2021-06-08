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
import org.hyperledger.besu.enclave.types.CreatePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.DeletePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.ErrorResponse;
import org.hyperledger.besu.enclave.types.FindPrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.RetrievePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.SendRequestBesu;
import org.hyperledger.besu.enclave.types.SendRequestLegacy;
import org.hyperledger.besu.enclave.types.SendResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Enclave {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String ORION = "application/vnd.orion.v1+json";
  private static final String JSON = "application/json";

  private final RequestTransmitter requestTransmitter;

  public Enclave(final RequestTransmitter requestTransmitter) {
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
      final String payload, final String privateFrom, final List<String> privateFor) {
    final SendRequestLegacy request = new SendRequestLegacy(payload, privateFrom, privateFor);
    return post(
        JSON,
        request,
        "/send",
        (statusCode, body) -> handleJsonResponse(statusCode, body, SendResponse.class));
  }

  public SendResponse send(
      final String payload, final String privateFrom, final String privacyGroupId) {
    final SendRequestBesu request = new SendRequestBesu(payload, privateFrom, privacyGroupId);
    return post(
        JSON,
        request,
        "/send",
        (statusCode, body) -> handleJsonResponse(statusCode, body, SendResponse.class));
  }

  public ReceiveResponse receive(final String payloadKey) {
    final ReceiveRequest request = new ReceiveRequest(payloadKey);
    return post(
        ORION,
        request,
        "/receive",
        (statusCode, body) -> handleJsonResponse(statusCode, body, ReceiveResponse.class));
  }

  public ReceiveResponse receive(final String payloadKey, final String to) {
    final ReceiveRequest request = new ReceiveRequest(payloadKey, to);
    return post(
        ORION,
        request,
        "/receive",
        (statusCode, body) -> handleJsonResponse(statusCode, body, ReceiveResponse.class));
  }

  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String from,
      final String name,
      final String description) {
    final CreatePrivacyGroupRequest request =
        new CreatePrivacyGroupRequest(addresses, from, name, description);
    return post(
        JSON,
        request,
        "/createPrivacyGroup",
        (statusCode, body) -> handleJsonResponse(statusCode, body, PrivacyGroup.class));
  }

  public String deletePrivacyGroup(final String privacyGroupId, final String from) {
    final DeletePrivacyGroupRequest request = new DeletePrivacyGroupRequest(privacyGroupId, from);
    return post(
        JSON,
        request,
        "/deletePrivacyGroup",
        (statusCode, body) -> handleJsonResponse(statusCode, body, String.class));
  }

  public PrivacyGroup[] findPrivacyGroup(final List<String> addresses) {
    final FindPrivacyGroupRequest request = new FindPrivacyGroupRequest(addresses);
    return post(
        JSON,
        request,
        "/findPrivacyGroup",
        (statusCode, body) -> handleJsonResponse(statusCode, body, PrivacyGroup[].class));
  }

  public PrivacyGroup retrievePrivacyGroup(final String privacyGroupId) {
    final RetrievePrivacyGroupRequest request = new RetrievePrivacyGroupRequest(privacyGroupId);
    return post(
        JSON,
        request,
        "/retrievePrivacyGroup",
        (statusCode, body) -> handleJsonResponse(statusCode, body, PrivacyGroup.class));
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

  private <T> T handleJsonResponse(
      final int statusCode, final byte[] body, final Class<T> responseType) {

    if (isSuccess(statusCode)) {
      return parseResponse(statusCode, body, responseType);
    } else if (clientError(statusCode)) {
      final ErrorResponse errorResponse = parseResponse(statusCode, body, ErrorResponse.class);
      throw new EnclaveClientException(statusCode, errorResponse.getError());
    } else {
      final ErrorResponse errorResponse = parseResponse(statusCode, body, ErrorResponse.class);
      throw new EnclaveServerException(statusCode, errorResponse.getError());
    }
  }

  private <T> T parseResponse(
      final int statusCode, final byte[] body, final Class<T> responseType) {
    try {
      return objectMapper.readValue(body, responseType);
    } catch (final IOException e) {
      final String utf8EncodedBody = new String(body, StandardCharsets.UTF_8);
      // Check if it's a Tessera error message
      try {
        return objectMapper.readValue(
            processTesseraError(utf8EncodedBody, responseType), responseType);
      } catch (final IOException ex) {
        throw new EnclaveClientException(statusCode, utf8EncodedBody);
      }
    }
  }

  private <T> byte[] processTesseraError(final String errorMsg, final Class<T> responseType) {
    if (responseType == SendResponse.class) {
      final String base64Key =
          errorMsg.substring(errorMsg.substring(0, errorMsg.indexOf('=')).lastIndexOf(' '));
      return jsonByteArrayFromString("key", base64Key);
    } else if (responseType == ErrorResponse.class) {
      // Remove dynamic values
      return jsonByteArrayFromString("error", removeBase64(errorMsg));
    } else {
      throw new RuntimeException("Unhandled response type.");
    }
  }

  private String removeBase64(final String input) {
    if (input.contains("=")) {
      final String startInclBase64 = input.substring(0, input.indexOf('='));
      final String startTrimmed = startInclBase64.substring(0, startInclBase64.lastIndexOf(" "));
      final String end = input.substring(input.indexOf("="));
      if (end.length() > 1) {
        // Base64 in middle
        return startTrimmed + end.substring(1);
      } else {
        // Base64 at end
        return startTrimmed;
      }
    } else {
      return input;
    }
  }

  private byte[] jsonByteArrayFromString(final String key, final String value) {
    String format = String.format("{\"%s\":\"%s\"}", key, value);
    return format.getBytes(StandardCharsets.UTF_8);
  }

  private boolean clientError(final int statusCode) {
    return statusCode >= 400 && statusCode < 500;
  }

  private boolean isSuccess(final int statusCode) {
    return statusCode == 200;
  }

  private String handleRawResponse(final int statusCode, final byte[] body) {
    final String bodyText = new String(body, StandardCharsets.UTF_8);
    if (isSuccess(statusCode)) {
      return bodyText;
    }
    throw new EnclaveClientException(
        statusCode, String.format("Request failed with %d; body={%s}", statusCode, bodyText));
  }
}

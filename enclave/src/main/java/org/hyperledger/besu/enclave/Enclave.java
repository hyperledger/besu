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
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
      final String upcheckResponse = requestTransmitter.get("/upcheck", this::handleRawResponse);
      return upcheckResponse.equals("I'm up!");
    } catch (final Exception e) {
      return false;
    }
  }

  public SendResponse send(final SendRequest content) {
    return post(
        JSON,
        content,
        "/send",
        (statusCode, body) -> handleJsonResponse(statusCode, body, SendResponse.class));
  }

  public ReceiveResponse receive(final ReceiveRequest content) {
    return post(
        ORION,
        content,
        "/receive",
        (statusCode, body) -> handleJsonResponse(statusCode, body, ReceiveResponse.class));
  }

  public PrivacyGroup createPrivacyGroup(final CreatePrivacyGroupRequest content) {
    return post(
        JSON,
        content,
        "/createPrivacyGroup",
        (statusCode, body) -> handleJsonResponse(statusCode, body, PrivacyGroup.class));
  }

  public String deletePrivacyGroup(final DeletePrivacyGroupRequest content) {
    return post(
        JSON,
        content,
        "/deletePrivacyGroup",
        (statusCode, body) -> handleJsonResponse(statusCode, body, String.class));
  }

  public PrivacyGroup[] findPrivacyGroup(final FindPrivacyGroupRequest content) {
    return post(
        JSON,
        content,
        "/findPrivacyGroup",
        (statusCode, body) -> handleJsonResponse(statusCode, body, PrivacyGroup[].class));
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
      throw new EnclaveException("Unable to serialise object to json representation.");
    }

    return requestTransmitter.post(mediaType, bodyText, endpoint, responseBodyHandler);
  }

  private <T> T handleJsonResponse(
      final int statusCode, final byte[] body, final Class<T> responseType) {
    try {
      if (statusCode == 200) {
        return objectMapper.readValue(body, responseType);
      } else {
        final ErrorResponse errorResponse = objectMapper.readValue(body, ErrorResponse.class);
        throw new EnclaveException(errorResponse.getError());
      }
    } catch (final JsonParseException | JsonMappingException e) {
      throw new EnclaveException("Failed to deserialise received json", e);
    } catch (final IOException e) {
      throw new EnclaveException("Decoding Json stream failed", e);
    }
  }

  private String handleRawResponse(final int statusCode, final byte[] body) {
    final String bodyText = new String(body, StandardCharsets.UTF_8);
    if (statusCode == 200) {
      return bodyText;
    }
    throw new EnclaveException(
        String.format("Request failed with %d; body={%s}", statusCode, bodyText));
  }
}

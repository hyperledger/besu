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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.types.GoQuorumReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.enclave.types.StoreRawResponse;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

public class GoQuorumEnclaveTest {

  private static final byte[] PAYLOAD = Base64.getDecoder().decode("EAAAAAAA");
  private static final String MOCK_KEY = "iOCzoGo5kwtZU0J41Z9xnGXHN6ZNukIa9MspvHtu3Jk=";
  private static final String KEY =
      "tQEmN0d/xXJZs5OMgl8QVBIyYxu1XubAKehsSYbcOjbxai+QJQpEOs6ghrYAZizLtnM4EJdMyVeVrxO3cA9JJA==";
  private static GoQuorumEnclave enclave;

  private RequestTransmitter vertxTransmitter;

  @BeforeEach
  public void setUp() {
    enclave = createGoQuorumEnclaveWithMockRequestTransmitter();
  }

  @Test
  public void upCheck() {
    when(vertxTransmitter.get(
            any(), any(), ArgumentMatchers.contains("/upcheck"), any(), anyBoolean()))
        .thenReturn("I'm up!");

    assertThat(enclave.upCheck()).isTrue();
  }

  @Test
  public void receiveThrowsWhenPayloadDoesNotExist() {
    when(vertxTransmitter.get(
            any(), any(), ArgumentMatchers.contains("/transaction"), any(), anyBoolean()))
        .thenThrow(
            new EnclaveClientException(404, "Message with hash " + MOCK_KEY + " was not found"));

    assertThatThrownBy(() -> enclave.receive(MOCK_KEY))
        .isInstanceOf(EnclaveClientException.class)
        .hasMessageContaining("Message with hash " + MOCK_KEY + " was not found");
  }

  @Test
  public void sendAndReceive() {
    when(vertxTransmitter.post(any(), any(), any(), any())).thenReturn(new SendResponse(KEY));
    when(vertxTransmitter.get(
            any(), any(), ArgumentMatchers.contains("/transaction"), any(), anyBoolean()))
        .thenReturn(new GoQuorumReceiveResponse(PAYLOAD, 0, null, null));

    final List<String> publicKeys = Arrays.asList("/+UuD63zItL1EbjxkKUljMgG8Z1w0AJ8pNOR4iq2yQc=");

    final SendResponse sr = enclave.send(PAYLOAD, publicKeys.get(0), publicKeys);
    assertThat(sr.getKey()).isEqualTo(KEY);

    final GoQuorumReceiveResponse rr = enclave.receive(sr.getKey());
    assertThat(rr).isNotNull();
    assertThat(rr.getPayload()).isEqualTo(PAYLOAD);
  }

  @Test
  public void sendSignedTransaction() {
    when(vertxTransmitter.post(any(), any(), ArgumentMatchers.contains("/sendsignedtx"), any()))
        .thenReturn(new SendResponse(KEY));

    final List<String> publicKeys = Arrays.asList("/+UuD63zItL1EbjxkKUljMgG8Z1w0AJ8pNOR4iq2yQc=");

    final SendResponse sr = enclave.sendSignedTransaction(PAYLOAD, publicKeys);
    assertThat(sr.getKey()).isEqualTo(KEY);
  }

  @Test
  public void storeRawTransaction() {
    when(vertxTransmitter.post(any(), any(), ArgumentMatchers.contains("/storeraw"), any()))
        .thenReturn(new StoreRawResponse(KEY));

    final StoreRawResponse sr =
        enclave.storeRaw(
            "tQEmN0d/xXJZs5OMgl8QVBIyYxu1XubAKehsSYbcOjbxai+QJQpEOs6ghrYAZizLtnM4EJdMyVeVrxO3cA9JJA==");
    assertThat(sr.getKey()).isEqualTo(KEY);
  }

  @Test
  public void upcheckReturnsFalseIfNoResponseReceived() throws URISyntaxException {
    final Vertx vertx = Vertx.vertx();
    final EnclaveFactory factory = new EnclaveFactory(vertx);
    assertThat(factory.createGoQuorumEnclave(new URI("http://8.8.8.8:65535")).upCheck()).isFalse();
  }

  private GoQuorumEnclave createGoQuorumEnclaveWithMockRequestTransmitter() {
    vertxTransmitter = mock(RequestTransmitter.class);

    return new GoQuorumEnclave(vertxTransmitter);
  }
}

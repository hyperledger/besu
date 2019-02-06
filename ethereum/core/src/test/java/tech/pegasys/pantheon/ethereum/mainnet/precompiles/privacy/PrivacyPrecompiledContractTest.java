/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet.precompiles.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.ethereum.mainnet.SpuriousDragonGasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class PrivacyPrecompiledContractTest {
  private final String actual = "Test String";
  private final String publicKey = "public key";
  private final BytesValue key = BytesValue.wrap(actual.getBytes(UTF_8));
  private PrivacyPrecompiledContract privacyPrecompiledContract;
  private PrivacyPrecompiledContract brokenPrivateTransactionHandler;

  Enclave mockEnclave() throws IOException {
    Enclave mockEnclave = mock(Enclave.class);
    ReceiveResponse response = new ReceiveResponse(actual.getBytes(UTF_8));
    when(mockEnclave.receive(any(ReceiveRequest.class))).thenReturn(response);
    return mockEnclave;
  }

  Enclave brokenMockEnclave() throws IOException {
    Enclave mockEnclave = mock(Enclave.class);
    when(mockEnclave.receive(any(ReceiveRequest.class))).thenThrow(IOException.class);
    return mockEnclave;
  }

  @Before
  public void setUp() throws IOException {
    privacyPrecompiledContract =
        new PrivacyPrecompiledContract(new SpuriousDragonGasCalculator(), publicKey, mockEnclave());
    brokenPrivateTransactionHandler =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(), publicKey, brokenMockEnclave());
  }

  @Test
  public void testPrivacyPrecompiledContract() {

    final BytesValue expected = privacyPrecompiledContract.compute(key);

    String exp = new String(expected.extractArray(), UTF_8);
    assertThat(exp).isEqualTo(actual);
  }

  @Test
  public void enclaveIsDownWhileHandling() {
    final BytesValue expected = brokenPrivateTransactionHandler.compute(key);

    assertThat(expected).isEqualTo(BytesValue.EMPTY);
  }
}

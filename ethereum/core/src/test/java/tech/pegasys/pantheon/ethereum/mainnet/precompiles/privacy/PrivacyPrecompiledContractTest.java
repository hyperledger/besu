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

import tech.pegasys.pantheon.ethereum.mainnet.SpuriousDragonGasCalculator;
import tech.pegasys.pantheon.orion.Orion;
import tech.pegasys.pantheon.orion.types.ReceiveRequest;
import tech.pegasys.pantheon.orion.types.ReceiveResponse;
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

  Orion mockOrion() throws IOException {
    Orion mockOrion = mock(Orion.class);
    ReceiveResponse response = new ReceiveResponse(actual.getBytes(UTF_8));
    when(mockOrion.receive(any(ReceiveRequest.class))).thenReturn(response);
    return mockOrion;
  }

  Orion brokenMockOrion() throws IOException {
    Orion mockOrion = mock(Orion.class);
    when(mockOrion.receive(any(ReceiveRequest.class))).thenThrow(IOException.class);
    return mockOrion;
  }

  @Before
  public void setUp() throws IOException {
    privacyPrecompiledContract =
        new PrivacyPrecompiledContract(new SpuriousDragonGasCalculator(), publicKey, mockOrion());
    brokenPrivateTransactionHandler =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(), publicKey, brokenMockOrion());
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

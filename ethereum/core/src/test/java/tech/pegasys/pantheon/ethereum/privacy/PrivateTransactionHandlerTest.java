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
package tech.pegasys.pantheon.ethereum.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendResponse;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.math.BigInteger;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivateTransactionHandlerTest {

  private static final String TRANSACTION_KEY = "My Transaction Key";
  private static final KeyPair KEY_PAIR =
      KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  PrivateTransactionHandler privateTransactionHandler;
  PrivateTransactionHandler brokenPrivateTransactionHandler;

  private static final PrivateTransaction VALID_PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
          .value(Wei.ZERO)
          .payload(BytesValue.fromHexString("0x"))
          .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
          .chainId(2018)
          .privateFrom(
              BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)))
          .privateFor(
              Lists.newArrayList(
                  BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)),
                  BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8))))
          .restriction(BytesValue.wrap("restricted".getBytes(UTF_8)))
          .signAndBuild(KEY_PAIR);

  private static final Transaction PUBLIC_TRANSACTION =
      Transaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
          .value(Wei.ZERO)
          .payload(BytesValue.wrap(TRANSACTION_KEY.getBytes(Charsets.UTF_8)))
          .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
          .chainId(BigInteger.valueOf(2018))
          .signAndBuild(KEY_PAIR);

  Enclave mockEnclave() throws IOException {
    Enclave mockEnclave = mock(Enclave.class);
    SendResponse response = new SendResponse(TRANSACTION_KEY);
    when(mockEnclave.send(any(SendRequest.class))).thenReturn(response);
    return mockEnclave;
  }

  Enclave brokenMockEnclave() throws IOException {
    Enclave mockEnclave = mock(Enclave.class);
    when(mockEnclave.send(any(SendRequest.class))).thenThrow(IOException.class);
    return mockEnclave;
  }

  @Before
  public void setUp() throws IOException {
    privateTransactionHandler =
        new PrivateTransactionHandler(mockEnclave(), Address.DEFAULT_PRIVACY, KEY_PAIR);
    brokenPrivateTransactionHandler =
        new PrivateTransactionHandler(brokenMockEnclave(), Address.DEFAULT_PRIVACY, KEY_PAIR);
  }

  @Test
  public void validTransactionThroughHandler() throws IOException {
    final Transaction transactionResponse =
        privateTransactionHandler.handle(VALID_PRIVATE_TRANSACTION, () -> 0L);

    assertThat(transactionResponse.contractAddress())
        .isEqualTo(PUBLIC_TRANSACTION.contractAddress());
    assertThat(transactionResponse.getPayload()).isEqualTo(PUBLIC_TRANSACTION.getPayload());
    assertThat(transactionResponse.getNonce()).isEqualTo(PUBLIC_TRANSACTION.getNonce());
    assertThat(transactionResponse.getSender()).isEqualTo(PUBLIC_TRANSACTION.getSender());
    assertThat(transactionResponse.getValue()).isEqualTo(PUBLIC_TRANSACTION.getValue());
  }

  @Test(expected = IOException.class)
  public void enclaveIsDownWhileHandling() throws IOException {
    brokenPrivateTransactionHandler.handle(VALID_PRIVATE_TRANSACTION, () -> 0L);
  }
}

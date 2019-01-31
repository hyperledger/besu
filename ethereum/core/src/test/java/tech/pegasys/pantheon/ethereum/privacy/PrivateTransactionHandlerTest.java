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
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.orion.Orion;
import tech.pegasys.pantheon.orion.types.SendRequest;
import tech.pegasys.pantheon.orion.types.SendResponse;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivateTransactionHandlerTest {

  private static final String TRANSACTION_KEY = "My Transaction Key";
  private static final String TRANSACTION_KEY_HEX = "0x4d79205472616e73616374696f6e204b6579";

  PrivateTransactionHandler privateTransactionHandler;
  PrivateTransactionHandler brokenPrivateTransactionHandler;

  private static final PrivateTransaction VALID_PRIVATE_TRANSACTION =
      new PrivateTransaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(BytesValue.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.of(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129639935")),
          SECP256K1.Signature.create(
              new BigInteger(
                  "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
              new BigInteger(
                  "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
              Byte.valueOf("0")),
          BytesValue.fromHexString("0x"),
          Address.wrap(BytesValue.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          1,
          BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)),
          Lists.newArrayList(
              BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)),
              BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8))),
          BytesValue.wrap("restricted".getBytes(UTF_8)));

  private static final Transaction PUBLIC_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(Address.DEFAULT_PRIVACY),
          Wei.of(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129639935")),
          SECP256K1.Signature.create(
              new BigInteger(
                  "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
              new BigInteger(
                  "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
              Byte.valueOf("0")),
          BytesValue.fromHexString(TRANSACTION_KEY_HEX),
          Address.wrap(BytesValue.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          1);

  Orion mockOrion() throws IOException {
    Orion mockOrion = mock(Orion.class);
    SendResponse response = new SendResponse();
    response.setKey(TRANSACTION_KEY);
    when(mockOrion.send(any(SendRequest.class))).thenReturn(response);
    return mockOrion;
  }

  Orion brokenMockOrion() throws IOException {
    Orion mockOrion = mock(Orion.class);
    when(mockOrion.send(any(SendRequest.class))).thenThrow(IOException.class);
    return mockOrion;
  }

  @Before
  public void setUp() throws IOException {
    privateTransactionHandler = new PrivateTransactionHandler(mockOrion(), Address.DEFAULT_PRIVACY);
    brokenPrivateTransactionHandler =
        new PrivateTransactionHandler(brokenMockOrion(), Address.DEFAULT_PRIVACY);
  }

  @Test
  public void validTransactionThroughHandler() throws IOException {
    final Transaction transactionRespose =
        privateTransactionHandler.handle(VALID_PRIVATE_TRANSACTION);

    assertThat(transactionRespose).isEqualToComparingFieldByField(PUBLIC_TRANSACTION);
  }

  @Test(expected = IOException.class)
  public void enclaveIsDownWhileHandling() throws IOException {
    brokenPrivateTransactionHandler.handle(VALID_PRIVATE_TRANSACTION);
  }
}

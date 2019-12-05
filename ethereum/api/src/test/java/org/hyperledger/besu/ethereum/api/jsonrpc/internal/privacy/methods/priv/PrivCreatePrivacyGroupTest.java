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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.ethereum.privacy.privatetransaction.RandomSigningGroupCreationTransactionFactory;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class PrivCreatePrivacyGroupTest {

  private static final String TRANSACTION_KEY = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";
  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
  private static final String FROM = "P5D4MUAsRwnJV3a3YOtPpFOm7ISkrhRBADcxbDAVEKw=";
  private static final String NAME = "testName";
  private static final String DESCRIPTION = "testDesc";
  private static final String[] ADDRESSES =
      new String[] {FROM, "1mY8srzYiV7TO8dmOx9w7q9XkGVZbisBx8ZPFvVDTjg="};
  private static final String PRIVACY_GROUP_ID = "pjsupmFSHZdZF3iYoGWmn5gzv5Ew8i3AAbH3iWtfvKI=";
  private static final Transaction PUBLIC_TRANSACTION =
      Transaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
          .value(Wei.ZERO)
          .payload(BytesValues.fromBase64(TRANSACTION_KEY))
          .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
          .chainId(BigInteger.valueOf(2018))
          .signAndBuild(KEY_PAIR);

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private PrivateTransactionHandler privateTransactionHandler =
      mock(PrivateTransactionHandler.class);
  private TransactionPool transactionPool = mock(TransactionPool.class);

  @Before
  public void setUp() {
    when(privacyParameters.getEnclavePublicKey()).thenReturn(FROM);
    when(privacyParameters.getSigningKeyPair()).thenReturn(Optional.of(KEY_PAIR));
    when(privateTransactionHandler.sendToOrion(any(PrivateTransaction.class)))
        .thenReturn(TRANSACTION_KEY);
    when(privateTransactionHandler.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    when(privacyParameters.isEnabled()).thenReturn(true);
  }

  @Test
  public void verifyCreatePrivacyGroup() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            privateTransactionHandler,
            transactionPool);

    final CreatePrivacyGroupParameter param =
        new CreatePrivacyGroupParameter(ADDRESSES, NAME, DESCRIPTION);

    final Object[] params = new Object[] {param};

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();
    assertThat(result).isEqualTo(PRIVACY_GROUP_ID);
  }

  @Test
  public void verifyCreatePrivacyGroupWithoutDescription() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            privateTransactionHandler,
            transactionPool);

    final Object[] params =
        new Object[] {
          new Object() {
            public String[] getAddresses() {
              return ADDRESSES;
            }

            public String getName() {
              return NAME;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();
    assertThat(result).isEqualTo(PRIVACY_GROUP_ID);
  }

  @Test
  public void verifyCreatePrivacyGroupWithoutName() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            privateTransactionHandler,
            transactionPool);

    final Object[] params =
        new Object[] {
          new Object() {
            public String[] getAddresses() {
              return ADDRESSES;
            }

            public String getDescription() {
              return DESCRIPTION;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();
    assertThat(result).isEqualTo(PRIVACY_GROUP_ID);
  }

  @Test
  public void verifyCreatePrivacyGroupWithoutOptionalParams() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            privateTransactionHandler,
            transactionPool);

    final Object[] params =
        new Object[] {
          new Object() {
            public String[] getAddresses() {
              return ADDRESSES;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();
    assertThat(result).isEqualTo(PRIVACY_GROUP_ID);
  }

  @Test
  public void returnsCorrectExceptionInvalidParam() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            privateTransactionHandler,
            transactionPool);

    final Object[] params =
        new Object[] {
          new Object() {
            public String getName() {
              return NAME;
            }

            public String getDescription() {
              return DESCRIPTION;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final Throwable response =
        catchThrowableOfType(
            () -> privCreatePrivacyGroup.response(request), InvalidJsonRpcParameters.class);

    assertThat(response.getMessage()).isEqualTo("Invalid json rpc parameter at index 0");
  }

  @Test
  public void returnsCorrectExceptionMissingParam() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            privateTransactionHandler,
            transactionPool);

    final Object[] params = new Object[] {};

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final Throwable response =
        catchThrowableOfType(
            () -> privCreatePrivacyGroup.response(request), InvalidJsonRpcParameters.class);

    assertThat(response.getMessage()).isEqualTo("Missing required json rpc parameter at index 0");
  }

  @Test
  public void returnsCorrectErrorEnclaveError() {
    final PrivateTransactionHandler failingTransactionHandler =
        mock(PrivateTransactionHandler.class);
    when(failingTransactionHandler.sendToOrion(any(PrivateTransaction.class)))
        .thenThrow(new EnclaveException(""));

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            failingTransactionHandler,
            transactionPool);

    final CreatePrivacyGroupParameter param =
        new CreatePrivacyGroupParameter(ADDRESSES, NAME, DESCRIPTION);

    final Object[] params = new Object[] {param};

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privCreatePrivacyGroup.response(request);

    final JsonRpcError result = response.getError();

    assertThat(result).isEqualTo(JsonRpcError.ENCLAVE_ERROR);
  }

  @Test
  public void returnPrivacyDisabledErrorWhenPrivacyIsDisabled() {
    final PrivateTransactionHandler failingTransactionHandler =
        mock(PrivateTransactionHandler.class);
    when(failingTransactionHandler.sendToOrion(any(PrivateTransaction.class)))
        .thenThrow(new EnclaveException(""));

    when(privacyParameters.isEnabled()).thenReturn(false);
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(
            privacyParameters,
            new RandomSigningGroupCreationTransactionFactory(),
            failingTransactionHandler,
            transactionPool);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_createPrivacyGroup", new Object[] {}));
    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privCreatePrivacyGroup.response(request);

    assertThat(response.getError()).isEqualTo(JsonRpcError.PRIVACY_NOT_ENABLED);
  }
}

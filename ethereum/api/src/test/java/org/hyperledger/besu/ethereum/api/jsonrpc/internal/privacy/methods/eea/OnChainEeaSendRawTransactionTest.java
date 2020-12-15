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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OnChainEeaSendRawTransactionTest extends EeaSendRawTransactionTest {

  @Test
  public void validOnChainTransactionPrivacyGroupIsSentToTransactionPool() {
    method =
        new OnChainEeaSendRawTransaction(
            transactionPool, privacyController, enclavePublicKeyProvider);

    when(privacyController.sendTransaction(any(PrivateTransaction.class), any(), any()))
        .thenReturn(MOCK_ORION_KEY);
    when(privacyController.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class)))
        .thenReturn(ValidationResult.valid());
    final Optional<PrivacyGroup> optionalPrivacyGroup =
        Optional.of(
            new PrivacyGroup(
                "", PrivacyGroup.Type.ONCHAIN, "", "", Arrays.asList(ENCLAVE_PUBLIC_KEY)));
    when(privacyController.findOnChainPrivacyGroupAndAddNewMembers(any(), any(), any()))
        .thenReturn(optionalPrivacyGroup);
    when(privacyController.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eea_sendRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController).sendTransaction(any(PrivateTransaction.class), any(), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), any(String.class));
    verify(privacyController)
        .createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), eq(Address.ONCHAIN_PRIVACY));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void transactionFailsForLegacyPrivateTransaction() {
    method =
        new OnChainEeaSendRawTransaction(
            transactionPool, privacyController, enclavePublicKeyProvider);

    final JsonRpcRequestContext request = getJsonRpcRequestContext();

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), JsonRpcError.ONCHAIN_PRIVACY_GROUP_ID_NOT_AVAILABLE);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequestContext getJsonRpcRequestContext() {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALID_LEGACY_PRIVATE_TRANSACTION_RLP}),
        user);
  }

  @Test
  public void offChainPrivacyGroupTransactionFailsWhenOnchainPrivacyGroupFeatureIsEnabled() {
    method =
        new OnChainEeaSendRawTransaction(
            transactionPool, privacyController, enclavePublicKeyProvider);

    when(privacyController.findOnChainPrivacyGroupAndAddNewMembers(any(), any(), any()))
        .thenReturn(Optional.empty());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eea_sendRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), JsonRpcError.ONCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }
}

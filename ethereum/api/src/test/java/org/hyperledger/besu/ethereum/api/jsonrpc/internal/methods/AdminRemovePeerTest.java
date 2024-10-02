/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.exceptions.P2PDisabledException;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AdminRemovePeerTest {

  @Mock private P2PNetwork p2pNetwork;

  private AdminRemovePeer method;
  private AdminRemovePeer methodDNSDisabled;
  private AdminRemovePeer methodDNSUpdateDisabled;

  final String validEnode =
      "enode://"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "@127.0.0.1:30303";

  final String validDNSEnode =
      "enode://"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "@node.acme.com:30303";

  final JsonRpcRequestContext validRequest =
      new JsonRpcRequestContext(
          new JsonRpcRequest("2.0", "admin_removePeer", new String[] {validEnode}));

  final JsonRpcRequestContext validDNSRequest =
      new JsonRpcRequestContext(
          new JsonRpcRequest("2.0", "admin_removePeer", new String[] {validDNSEnode}));

  @BeforeEach
  public void setup() {
    method =
        new AdminRemovePeer(
            p2pNetwork,
            Optional.of(
                ImmutableEnodeDnsConfiguration.builder()
                    .dnsEnabled(true)
                    .updateEnabled(true)
                    .build()));
    methodDNSDisabled =
        new AdminRemovePeer(
            p2pNetwork,
            Optional.of(
                ImmutableEnodeDnsConfiguration.builder()
                    .dnsEnabled(false)
                    .updateEnabled(true)
                    .build()));
    methodDNSUpdateDisabled =
        new AdminRemovePeer(
            p2pNetwork,
            Optional.of(
                ImmutableEnodeDnsConfiguration.builder()
                    .dnsEnabled(true)
                    .updateEnabled(false)
                    .build()));
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "admin_removePeer", new String[] {}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INVALID_PARAM_COUNT);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "admin_removePeer", null));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INVALID_PARAM_COUNT);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_removePeer", new String[] {null}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INVALID_PARAM_COUNT);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasInvalidEnode() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_removePeer", new String[] {"asdf"}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.PARSE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasInvalidEnodeLength() {
    String invalidLengthEnode =
        "enode://"
            + "0000000000000000000000000000000"
            + "00000000000000000000000000000000"
            + "00000000000000000000000000000000"
            + "00000000000000000000000000000000"
            + "@127.0.0.1:30303";
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_removePeer", new String[] {invalidLengthEnode}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.ENODE_ID_INVALID);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestRemovesValidEnode() {
    when(p2pNetwork.removeMaintainedConnectionPeer(any())).thenReturn(true);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(validRequest.getRequest().getId(), true);

    final JsonRpcResponse actualResponse = method.response(validRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestRemovesValidDNSEnode() {
    when(p2pNetwork.removeMaintainedConnectionPeer(any())).thenReturn(true);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            validRequest.getRequest().getId(),
            true); // DNS is mapped to an IP address, so we expect the non-DNS response

    final JsonRpcResponse actualResponse = method.response(validDNSRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestRemovesDNSEnodeButDNSDisabled() {
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validDNSRequest.getRequest().getId(), RpcErrorType.DNS_NOT_ENABLED);

    final JsonRpcResponse actualResponse = methodDNSDisabled.response(validDNSRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestRemovesDNSEnodeButDNSNotResolved() {
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validDNSRequest.getRequest().getId(), RpcErrorType.CANT_RESOLVE_PEER_ENODE_DNS);

    final JsonRpcResponse actualResponse = methodDNSUpdateDisabled.response(validDNSRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestRefusesListOfNodes() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_removePeer", new String[] {validEnode, validEnode}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INVALID_PARAM_COUNT);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestReturnsFalseIfRemoveFails() {
    when(p2pNetwork.removeMaintainedConnectionPeer(any())).thenReturn(false);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(validRequest.getRequest().getId(), false);

    final JsonRpcResponse actualResponse = method.response(validRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestReturnsErrorWhenP2pDisabled() {
    when(p2pNetwork.removeMaintainedConnectionPeer(any()))
        .thenThrow(
            new P2PDisabledException(
                "P2P networking disabled.  Unable to connect to remove peer."));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(validRequest.getRequest().getId(), RpcErrorType.P2P_DISABLED);

    final JsonRpcResponse actualResponse = method.response(validRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}

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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.P2PDisabledException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AdminAddPeerTest {

  @Mock private P2PNetwork p2pNetwork;
  private final JsonRpcParameter parameter = new JsonRpcParameter();

  private AdminAddPeer method;

  final String validEnode =
      "enode://"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "00000000000000000000000000000000"
          + "@127.0.0.1:30303";

  final JsonRpcRequest validRequest =
      new JsonRpcRequest("2.0", "admin_addPeer", new String[] {validEnode});

  @Before
  public void setup() {
    method = new AdminAddPeer(p2pNetwork, parameter);
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "admin_addPeer", new String[] {});
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "admin_addPeer", null);
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "admin_addPeer", new String[] {null});
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestHasInvalidEnode() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "admin_addPeer", new String[] {"asdf"});
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.PARSE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
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
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "admin_addPeer", new String[] {invalidLengthEnode});
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.ENODE_ID_INVALID);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestAddsValidEnode() {
    when(p2pNetwork.addMaintainConnectionPeer(any())).thenReturn(true);

    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(validRequest.getId(), true);

    final JsonRpcResponse actualResponse = method.response(validRequest);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestRefusesListOfNodes() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "admin_addPeer", new String[] {validEnode, validEnode});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestReturnsFalseIfAddFails() {
    when(p2pNetwork.addMaintainConnectionPeer(any())).thenReturn(false);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(validRequest.getId(), false);

    final JsonRpcResponse actualResponse = method.response(validRequest);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestReturnsErrorWhenP2pDisabled() {
    when(p2pNetwork.addMaintainConnectionPeer(any()))
        .thenThrow(
            new P2PDisabledException("P2P networking disabled.  Unable to connect to add peer."));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(validRequest.getId(), JsonRpcError.P2P_DISABLED);

    final JsonRpcResponse actualResponse = method.response(validRequest);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }
}

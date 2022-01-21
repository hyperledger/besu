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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivUninstallFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivUninstallFilterTest {

  private final String FILTER_ID = "0xdbdb02abb65a2ba57a1cc0336c17ef75";
  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  @Mock private FilterManager filterManager;
  @Mock private PrivacyController privacyController;
  @Mock private PrivacyIdProvider privacyIdProvider;

  private PrivUninstallFilter method;

  @Before
  public void before() {
    method = new PrivUninstallFilter(filterManager, privacyController, privacyIdProvider);
  }

  @Test
  public void getMethodReturnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("priv_uninstallFilter");
  }

  @Test
  public void privacyGroupIdIsRequired() {
    final JsonRpcRequestContext request = privUninstallFilterRequest(null, "0x1");

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 0");
  }

  @Test
  public void filterIdIsRequired() {
    final JsonRpcRequestContext request = privUninstallFilterRequest(PRIVACY_GROUP_ID, null);

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 1");
  }

  @Test
  public void correctFilterIsUninstalled() {
    final JsonRpcRequestContext request = privUninstallFilterRequest(PRIVACY_GROUP_ID, FILTER_ID);
    method.response(request);

    verify(filterManager).uninstallFilter(eq(FILTER_ID));
  }

  private JsonRpcRequestContext privUninstallFilterRequest(
      final String privacyGroupId, final String filterId) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "priv_uninstallFilter", new Object[] {privacyGroupId, filterId}));
  }
}

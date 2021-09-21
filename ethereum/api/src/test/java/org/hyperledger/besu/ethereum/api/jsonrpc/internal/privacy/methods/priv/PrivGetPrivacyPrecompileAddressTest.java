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
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.PRIVACY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;

import org.junit.Test;

public class PrivGetPrivacyPrecompileAddressTest {

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);

  @Test
  public void verifyPrivacyPrecompileAddress() {
    when(privacyParameters.getPrivacyAddress()).thenReturn(DEFAULT_PRIVACY);
    when(privacyParameters.isEnabled()).thenReturn(true);

    final PrivGetPrivacyPrecompileAddress privGetPrivacyPrecompileAddress =
        new PrivGetPrivacyPrecompileAddress(privacyParameters);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_getPrivacyPrecompileAddress", new Object[0]));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivacyPrecompileAddress.response(request);

    verify(privacyParameters).getPrivacyAddress();
    assertThat(response.getResult()).isEqualTo(Address.precompiled(PRIVACY).toString());
  }
}

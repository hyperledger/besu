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
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class AuthenticationUtilsTest {

  @Test
  public void getJwtTokenFromNullStringShouldReturnNull() {
    final String headerValue = null;

    final String token = AuthenticationUtils.getJwtTokenFromAuthorizationHeaderValue(headerValue);

    assertThat(token).isNull();
  }

  @Test
  public void getJwtTokenFromEmptyStringShouldReturnNull() {
    final String headerValue = "";

    final String token = AuthenticationUtils.getJwtTokenFromAuthorizationHeaderValue(headerValue);

    assertThat(token).isNull();
  }

  @Test
  public void getJwtTokenFromInvalidAuthorizationHeaderValueShouldReturnNull() {
    final String headerValue = "Foo eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9";

    final String token = AuthenticationUtils.getJwtTokenFromAuthorizationHeaderValue(headerValue);

    assertThat(token).isNull();
  }

  @Test
  public void getJwtTokenFromValidAuthorizationHeaderValueShouldReturnToken() {
    final String headerValue = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9";

    final String token = AuthenticationUtils.getJwtTokenFromAuthorizationHeaderValue(headerValue);

    assertThat(token).isEqualTo("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9");
  }
}

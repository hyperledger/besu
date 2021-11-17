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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.jwt.impl.JWTAuthProviderImpl;
import org.junit.Test;

public class AuthenticationUtilsTest {

  private static final String INVALID_TOKEN_WITHOUT_EXP =
      "ewogICJhbGciOiAibm9uZSIsCiAgInR5cCI6ICJKV1QiCn"
          + "0.eyJpYXQiOjE1MTYyMzkwMjIsInBlcm1pc3Npb25zIjpbIm5ldDpwZWVyQ291bnQiXX0";
  private static final String VALID_TOKEN =
      "ewogICJhbGciOiAibm9uZSIsCiAgInR5cCI6ICJKV1QiCn0.eyJpYXQiOjE1"
          + "MTYyMzkwMjIsImV4cCI6NDcyOTM2MzIwMCwicGVybWlzc2lvbnMiOlsibmV0OnBlZXJDb3VudCJdfQ";
  // private static final String VALID_TOKEN_DECODED_PAYLOAD =
  //  "{\"iat\": 1516239022,\"exp\": 4729363200," + "\"permissions\": [\"net:peerCount\"]}";

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

  @Test
  public void getUserFailsIfTokenDoesNotHaveExpiryClaim() {
    final AuthenticationService authenticationService = mock(AuthenticationService.class);
    final JWTAuth jwtAuth = new JWTAuthProviderImpl(null, new JWTAuthOptions());
    final StubUserHandler handler = new StubUserHandler();
    when(authenticationService.getJwtAuthProvider()).thenReturn(jwtAuth);

    AuthenticationUtils.getUser(
        Optional.of(authenticationService), INVALID_TOKEN_WITHOUT_EXP, handler);

    assertThat(handler.getEvent()).isEmpty();
  }

  @Test
  public void getUserSucceedsWithValidToken() {
    final AuthenticationService authenticationService = mock(AuthenticationService.class);
    final JWTAuth jwtAuth = new JWTAuthProviderImpl(null, new JWTAuthOptions());
    final StubUserHandler handler = new StubUserHandler();
    when(authenticationService.getJwtAuthProvider()).thenReturn(jwtAuth);

    AuthenticationUtils.getUser(Optional.of(authenticationService), VALID_TOKEN, handler);

    User successKid = handler.getEvent().get();
    assertThat(successKid.attributes().getLong("exp")).isEqualTo(4729363200L);
    assertThat(successKid.attributes().getLong("iat")).isEqualTo(1516239022L);
    assertThat(successKid.principal().getJsonArray("permissions").getString(0))
        .isEqualTo("net:peerCount");
    // assertThat(handler.getEvent().get().principal())
    //  .isEqualTo(new JsonObject(VALID_TOKEN_DECODED_PAYLOAD));
  }

  private static class StubUserHandler implements Handler<Optional<User>> {
    private Optional<User> event;

    @Override
    public void handle(final Optional<User> event) {
      this.event = event;
    }

    public Optional<User> getEvent() {
      return event;
    }
  }
}

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
package tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.login.AwaitLoginResponse;
import tech.pegasys.pantheon.tests.acceptance.dsl.httptransaction.LoginResponds;
import tech.pegasys.pantheon.tests.acceptance.dsl.httptransaction.LoginTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.httptransaction.LoginUnauthorizedTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

public class Login {

  public Condition loginSucceeds(final String username, final String password) {
    return (n) -> {
      final String token = n.executeHttpTransaction(new LoginTransaction(username, password));
      assertThat(token).isNotBlank();
    };
  }

  public Condition loginFails(final String username, final String password) {
    return (n) -> {
      n.executeHttpTransaction(new LoginUnauthorizedTransaction(username, password));
    };
  }

  public Condition loginSucceedsAndSetsAuthenticationToken(
      final String username, final String password) {

    return (n) -> {
      final String token = n.executeHttpTransaction(new LoginTransaction(username, password));
      assertThat(token).isNotBlank();
      ((PantheonNode) n).useAuthenticationTokenInHeaderForJsonRpc(token);
    };
  }

  public Condition awaitLoginResponse(final String username, final String password) {
    return new AwaitLoginResponse(new LoginResponds(username, password));
  }
}

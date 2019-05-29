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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

public class LoginUnauthorizedTransaction implements Transaction<Void> {

  private final String username;
  private final String password;

  public LoginUnauthorizedTransaction(final String username, final String password) {
    this.username = username;
    this.password = password;
  }

  @Override
  public Void execute(final NodeRequests node) {
    try {
      assertThat(node.login().send(username, password)).isEqualTo("Unauthorized");
      return null;
    } catch (final IOException e) {
      fail("Login request failed with exception: " + e.toString());
      return null;
    }
  }
}

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
package org.hyperledger.besu.tests.acceptance.dsl.condition.login;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.login.LoginTransaction;

public class LoginConditions {

  public Condition success(final String username, final String password) {
    return new ExpectLoginSuccess(username, password);
  }

  public Condition failure(final String username, final String password) {
    return new ExpectLoginUnauthorized(username, password);
  }

  public Condition disabled() {
    return new ExpectLoginDisabled();
  }

  public Condition awaitResponse(final String username, final String password) {
    return new AwaitLoginResponse<>(new LoginTransaction(username, password));
  }
}

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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

public class PrivDebugGetStateRoot implements Transaction<PrivacyRequestFactory.DebugGetStateRoot> {

  private final String privacyGroupId;
  private final String blockParam;

  public PrivDebugGetStateRoot(final String privacyGroupId, final String blockParam) {
    this.privacyGroupId = privacyGroupId;
    this.blockParam = blockParam;
  }

  @Override
  public PrivacyRequestFactory.DebugGetStateRoot execute(final NodeRequests node) {
    try {
      final PrivacyRequestFactory.DebugGetStateRoot response =
          node.privacy().privDebugGetStateRoot(privacyGroupId, blockParam).send();
      assertThat(response).as("check response is not null").isNotNull();
      return response;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}

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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.priv;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.util.List;

public class PrivCreatePrivacyGroupTransactionWithoutDescription implements Transaction<String> {
  private final List<String> addresses;
  private final String name;

  public PrivCreatePrivacyGroupTransactionWithoutDescription(
      final List<String> addresses, final String name) {
    this.addresses = addresses;
    this.name = name;
  }

  @Override
  public String execute(final NodeRequests node) {
    try {
      final PrivRequestFactory.PrivCreatePrivacyGroupResponse result =
          node.priv().privCreatePrivacyGroupWithoutDescription(addresses, name).send();
      assertThat(result).isNotNull();
      return result.getResult();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}

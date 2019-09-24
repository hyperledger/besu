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
package org.hyperledger.besu.tests.acceptance.dsl.condition.perm;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.permissioning.WhitelistPersistor;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import java.nio.file.Path;
import java.util.Collection;

public class WhiteListContainsKeyAndValue implements Condition {
  private final WhitelistPersistor.WHITELIST_TYPE whitelistType;
  private final Collection<String> whitelistValues;
  private final Path configFilePath;

  public WhiteListContainsKeyAndValue(
      final WhitelistPersistor.WHITELIST_TYPE whitelistType,
      final Collection<String> whitelistValues,
      final Path configFilePath) {
    this.whitelistType = whitelistType;
    this.whitelistValues = whitelistValues;
    this.configFilePath = configFilePath;
  }

  @Override
  public void verify(final Node node) {
    boolean result;
    try {
      result =
          WhitelistPersistor.verifyConfigFileMatchesState(
              whitelistType, whitelistValues, configFilePath);
    } catch (final Exception e) {
      result = false;
    }
    assertThat(result).isTrue();
  }
}

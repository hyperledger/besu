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

import org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor;
import org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor.ALLOWLIST_TYPE;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import java.nio.file.Path;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllowListContainsKeyAndValue implements Condition {
  private final ALLOWLIST_TYPE allowlistType;
  private final Collection<String> allowlistValues;
  private final Path configFilePath;

  private static final Logger LOG = LoggerFactory.getLogger(AllowListContainsKeyAndValue.class);

  public AllowListContainsKeyAndValue(
      final ALLOWLIST_TYPE allowlistType,
      final Collection<String> allowlistValues,
      final Path configFilePath) {
    this.allowlistType = allowlistType;
    this.allowlistValues = allowlistValues;
    this.configFilePath = configFilePath;
  }

  @Override
  public void verify(final Node node) {
    boolean result;
    try {
      result =
          AllowlistPersistor.verifyConfigFileMatchesState(
              allowlistType, allowlistValues, configFilePath);
    } catch (final Exception e) {
      result = false;
      LOG.error("Error verifying allowlist contains key and value", e);
    }
    assertThat(result).isTrue();
  }
}

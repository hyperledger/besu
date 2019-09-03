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
package tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.privacy;

import tech.pegasys.orion.testutil.OrionKeyConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.PantheonNodeConfiguration;

public class PrivacyNodeConfiguration {

  private final PantheonNodeConfiguration pantheonConfig;
  private final OrionKeyConfiguration orionConfig;

  PrivacyNodeConfiguration(
      final PantheonNodeConfiguration pantheonConfig, final OrionKeyConfiguration orionConfig) {
    this.pantheonConfig = pantheonConfig;
    this.orionConfig = orionConfig;
  }

  public PantheonNodeConfiguration getPantheonConfig() {
    return pantheonConfig;
  }

  public OrionKeyConfiguration getOrionKeyConfig() {
    return orionConfig;
  }
}

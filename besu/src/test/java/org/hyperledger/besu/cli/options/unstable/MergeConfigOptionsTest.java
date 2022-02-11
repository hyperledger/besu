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
package org.hyperledger.besu.cli.options.unstable;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.hyperledger.besu.config.experimental.MergeConfigOptions;

import org.hyperledger.besu.config.experimental.MergeConfiguration;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@SuppressWarnings({"JdkObsolete"})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MergeConfigOptionsTest {

  @Test
  public void shouldBeDisabledByDefault() {
    // disabledByDefault
    assertThat(MergeOptions.create().isMergeEnabled()).isFalse();
  }

  @Test
  public void shouldBeEnabledFromCliConsumer() {
    // enable
    var mockStack = new Stack<String>();
    mockStack.push("true");
    new MergeOptions.MergeConfigConsumer().consumeParameters(mockStack, null, null);

    assertThat(MergeConfigOptions.isMergeEnabled()).isTrue();
  }

  @Test
  public void shouldDoWithMergeEnabled() {
    final AtomicBoolean check = new AtomicBoolean(false);
    MergeConfigOptions.doIfMergeEnabled((() -> check.set(true)));
    assertThat(check.get()).isTrue();
  }

  @Test
  public void shouldRefuseToDisableMergeOnceSet() {
    MergeConfiguration.setMergeEnabled(true);
    assertThat(MergeConfiguration.isMergeEnabled()).isTrue();
    MergeConfiguration.setMergeEnabled(false);
    assertThat(MergeConfiguration.isMergeEnabled()).isTrue();
  }
}

/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.config.BftConfigOptions;

import java.util.Objects;

public class BftForkSpec<C extends BftConfigOptions> {

  private final long block;
  private final C configOptions;

  public BftForkSpec(final long block, final C configOptions) {
    this.block = block;
    this.configOptions = configOptions;
  }

  public long getBlock() {
    return block;
  }

  public C getConfigOptions() {
    return configOptions;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BftForkSpec<?> that = (BftForkSpec<?>) o;
    return block == that.block && Objects.equals(configOptions, that.configOptions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, configOptions);
  }
}

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
package org.hyperledger.besu.ethereum.permissioning;

public class GoQuorumPermissioningConfiguration {

  public static final long QIP714_DEFAULT_BLOCK = 0;

  private final long qip714Block;
  private final boolean enabled;

  public GoQuorumPermissioningConfiguration(final long qip714Block, final boolean enabled) {
    this.qip714Block = qip714Block;
    this.enabled = enabled;
  }

  public static GoQuorumPermissioningConfiguration enabled(final long qip714Block) {
    return new GoQuorumPermissioningConfiguration(qip714Block, true);
  }

  public static GoQuorumPermissioningConfiguration disabled() {
    return new GoQuorumPermissioningConfiguration(QIP714_DEFAULT_BLOCK, false);
  }

  public long getQip714Block() {
    return qip714Block;
  }

  public boolean isEnabled() {
    return enabled;
  }
}

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
package org.hyperledger.besu.ethereum.bonsai.worldview;

public class BonsaiWorldStateConfig {

  private boolean isFrozen;

  private boolean isTrieDisabled;

  public BonsaiWorldStateConfig() {
    this(false, false);
  }

  public BonsaiWorldStateConfig(final boolean isTrieDisabled) {
    this(false, isTrieDisabled);
  }

  public BonsaiWorldStateConfig(final BonsaiWorldStateConfig config) {
    this(config.isFrozen(), config.isTrieDisabled());
  }

  public BonsaiWorldStateConfig(final boolean isFrozen, final boolean isTrieDisabled) {
    this.isFrozen = isFrozen;
    this.isTrieDisabled = isTrieDisabled;
  }

  public boolean isFrozen() {
    return isFrozen;
  }

  public void setFrozen(final boolean frozen) {
    isFrozen = frozen;
  }

  public boolean isTrieDisabled() {
    return isTrieDisabled;
  }

  public void setTrieDisabled(final boolean trieDisabled) {
    isTrieDisabled = trieDisabled;
  }
}

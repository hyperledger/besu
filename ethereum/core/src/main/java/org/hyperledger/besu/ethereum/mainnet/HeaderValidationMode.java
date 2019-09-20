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
package org.hyperledger.besu.ethereum.mainnet;

public enum HeaderValidationMode {
  /** No Validation. data must be pre-validated */
  NONE,

  /** Skip proof of work validation */
  LIGHT_DETACHED_ONLY,

  /** Skip proof of work validation */
  LIGHT_SKIP_DETACHED,

  /** Skip proof of work validation */
  LIGHT,

  /** Skip rules that can be applied when the parent is already on the blockchain */
  DETACHED_ONLY,

  /** Skip rules that can be applied before the parent is added to the block chain */
  SKIP_DETACHED,

  /** Fully validate the header */
  FULL;

  public boolean isFormOfLightValidation() {
    return this == LIGHT || this == LIGHT_DETACHED_ONLY || this == LIGHT_SKIP_DETACHED;
  }
}

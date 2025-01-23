/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.plugin.data.BlockOverrides;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockStateCall {

  private final BlockOverrides blockOverrides;

  private final List<? extends CallParameter> calls;

  private final StateOverrideMap stateOverrideMap;

  private final boolean validation;

  public BlockStateCall(
      final List<? extends CallParameter> calls,
      final BlockOverrides blockOverrides,
      final StateOverrideMap stateOverrideMap,
      final boolean validation) {
    this.calls = calls != null ? calls : new ArrayList<>();
    this.blockOverrides =
        blockOverrides != null ? blockOverrides : BlockOverrides.builder().build();
    this.stateOverrideMap = stateOverrideMap;
    this.validation = validation;
  }

  public boolean isValidate() {
    return validation;
  }

  public BlockOverrides getBlockOverrides() {
    return blockOverrides;
  }

  public Optional<StateOverrideMap> getStateOverrideMap() {
    return Optional.ofNullable(stateOverrideMap);
  }

  public List<? extends CallParameter> getCalls() {
    return calls;
  }
}

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

package org.hyperledger.besu.config;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class QbftFork extends BftFork {

  public enum VALIDATOR_SELECTION_MODE {
    BLOCKHEADER,
    CONTRACT
  }

  public static final String VALIDATOR_SELECTION_MODE_KEY = "validatorselectionmode";
  public static final String VALIDATOR_CONTRACT_ADDRESS_KEY = "validatorcontractaddress";

  @JsonCreator
  public QbftFork(final ObjectNode forkConfigRoot) {
    super(forkConfigRoot);
  }

  public Optional<VALIDATOR_SELECTION_MODE> getValidatorSelectionMode() {
    final Optional<String> mode = JsonUtil.getString(forkConfigRoot, VALIDATOR_SELECTION_MODE_KEY);
    return mode.flatMap(
        m ->
            Arrays.stream(VALIDATOR_SELECTION_MODE.values())
                .filter(v -> v.name().equalsIgnoreCase(m))
                .findFirst());
  }

  public Optional<String> getValidatorContractAddress() {
    return JsonUtil.getString(forkConfigRoot, VALIDATOR_CONTRACT_ADDRESS_KEY);
  }
}

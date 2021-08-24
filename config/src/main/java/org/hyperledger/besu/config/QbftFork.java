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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
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

  public QbftFork(
      final long block,
      final Optional<Integer> blockPeriod,
      final Optional<BigInteger> blockReward,
      final Optional<List<String>> validators,
      final Optional<VALIDATOR_SELECTION_MODE> validatorSelectionMode,
      final Optional<String> validatorContractAddress) {
    super(block, blockPeriod, blockReward, validators);
    validatorSelectionMode.ifPresent(
        v -> forkConfigRoot.put(VALIDATOR_SELECTION_MODE_KEY, v.toString()));
    validatorContractAddress.ifPresent(v -> forkConfigRoot.put(VALIDATOR_CONTRACT_ADDRESS_KEY, v));
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

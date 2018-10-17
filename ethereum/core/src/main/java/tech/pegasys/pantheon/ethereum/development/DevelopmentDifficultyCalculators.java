/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.development;

import tech.pegasys.pantheon.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

/**
 * This provides a difficulty calculator that can be used during development efforts; given
 * development (typically) uses CPU based mining, a negligible difficulty ensures tests etc. execute
 * quickly.
 */
public class DevelopmentDifficultyCalculators {

  public static final BigInteger MINIMUM_DIFFICULTY = BigInteger.valueOf(500L);

  public static DifficultyCalculator<Void> DEVELOPER =
      (time, parent, context) -> {
        return MINIMUM_DIFFICULTY;
      };
}

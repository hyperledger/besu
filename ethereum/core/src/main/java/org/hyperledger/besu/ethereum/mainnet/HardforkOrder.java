/*
 * Copyright contributors to Hyperledger Besu.
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

public interface HardforkOrder {

  enum MainnetHardforkOrder implements HardforkOrder {
    FRONTIER,
    HOMESTEAD,
    DAO_FORK,
    TANGERINE_WHISTLE,
    SPURIOUS_DRAGON,
    BYZANTIUM,
    CONSTANTINOPLE,
    PETERSBURG,
    ISTANBUL,
    MUIR_GLACIER,
    BERLIN,
    LONDON,
    ARROW_GLACIER,
    GRAY_GLACIER,
    PARIS,
    SHANGHAI,
    CANCUN,
    CANCUN_EOF,
    PRAGUE,
    PRAGUE_EOF,
    FUTURE_EIPS,
    EXPERIMENTAL_EIPS
  }

  enum ClassicHardforkOrder implements HardforkOrder {
    FRONTIER,
    HOMESTEAD,
    CLASSIC_TANGERINE_WHISTLE,
    DIE_HARD,
    GOTHAM,
    DEFUSE_DIFFICULTY_BOMB,
    ATLANTIS,
    AGHARTA,
    PHOENIX,
    THANOS,
    MAGNETO,
    MYSTIQUE,
    SPIRAL
  }
}

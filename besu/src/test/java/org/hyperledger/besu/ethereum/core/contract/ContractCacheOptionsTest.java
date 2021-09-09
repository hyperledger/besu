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
package org.hyperledger.besu.ethereum.core.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cli.CommandTestAbstract;

import org.junit.Test;

public class ContractCacheOptionsTest extends CommandTestAbstract {

  @Test
  public void providedValueGoesToCodeCache() {
    parseCommand(ContractCacheOptions.CONTRACT_CACHE_WEIGHT, "13");
    assertThat(ContractCacheOptions.getContractCacheWeightKilobytes()).isEqualTo(13l);
    CodeCache cache = new CodeCache(13 * 1024);
    assertThat(cache.getWeight()).isEqualTo(13L * 1024);
  }
}

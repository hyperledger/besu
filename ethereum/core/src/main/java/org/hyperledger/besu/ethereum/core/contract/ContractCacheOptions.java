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

import picocli.CommandLine;

public class ContractCacheOptions {

  public static final String CONTRACT_CACHE_WEIGHT = "--contract-code-cache-weight-kb";

  public static Long getContractCacheWeightKilobytes() {
    return contractCacheWeightKilobytes;
  }

  @CommandLine.Option(
      names = {CONTRACT_CACHE_WEIGHT},
      description =
          "size in kilobytes to allow the cache to grow to before evicting the least recently used",
      fallbackValue = "250000",
      defaultValue = "250000",
      arity = "1")
  public static Long contractCacheWeightKilobytes = 250000l;
}

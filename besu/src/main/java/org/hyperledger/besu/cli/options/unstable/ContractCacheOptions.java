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

package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.ethereum.core.contract.ContractCacheConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class ContractCacheOptions implements CLIOptions<ContractCacheConfiguration> {

  public static final String CONTRACT_CACHE_WEIGHT = "--Xcontract-code-cache-weight-kb";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {CONTRACT_CACHE_WEIGHT},
      description =
          "size in kilobytes to allow the cached"
              + "contract bytecode to grow to before evicting the least recently used contract",
      fallbackValue = "250000",
      defaultValue = "250000",
      hidden = true,
      arity = "1")
  private Long contractCacheWeightKilobytes = 250000l;

  @Override
  public ContractCacheConfiguration toDomainObject() {
    return new ContractCacheConfiguration(contractCacheWeightKilobytes);
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(CONTRACT_CACHE_WEIGHT);
  }
}

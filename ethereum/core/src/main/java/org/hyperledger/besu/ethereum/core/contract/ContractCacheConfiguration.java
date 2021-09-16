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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractCacheConfiguration {
  public static final ContractCacheConfiguration DEFAULT_CONFIG =
      new ContractCacheConfiguration(250_000L);
  private final long contractCacheWeightKilobytes;
  private static ContractCacheConfiguration INSTANCE;
  private static final Logger LOG = LogManager.getLogger(ContractCacheConfiguration.class);

  public ContractCacheConfiguration(final long contractCacheWeightKilobytes) {
    this.contractCacheWeightKilobytes = contractCacheWeightKilobytes;
  }

  public static void init(final long contractCacheWeightKilobytes) {
    if (INSTANCE == null) {
      INSTANCE = new ContractCacheConfiguration(contractCacheWeightKilobytes);
    }
  }

  public static ContractCacheConfiguration getInstance() {
    if (INSTANCE == null) {
      LOG.debug("no instance, setting singleton to default");
      INSTANCE = DEFAULT_CONFIG;
    }
    return INSTANCE;
  }

  public long getContractCacheWeight() {
    return contractCacheWeightKilobytes * 1024L;
  }

  public long getContractCacheWeightKilobytes() {
    return contractCacheWeightKilobytes;
  }
}

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

import static tech.pegasys.pantheon.ethereum.mainnet.MainnetTransactionValidator.NO_CHAIN_ID;

import tech.pegasys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import io.vertx.core.json.JsonObject;

/**
 * A mock ProtocolSchedule which behaves similarly to Frontier (but for all blocks), albeit with a
 * much reduced difficulty (which supports testing on CPU alone).
 */
public class DevelopmentProtocolSchedule {

  public static ProtocolSchedule<Void> create(final JsonObject config) {
    final Integer chainId = config.getInteger("chainId", NO_CHAIN_ID);
    final MutableProtocolSchedule<Void> protocolSchedule = new MutableProtocolSchedule<>();
    protocolSchedule.putMilestone(0, DevelopmentProtocolSpecs.first(chainId, protocolSchedule));
    return protocolSchedule;
  }
}

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
package org.hyperledger.besu.consensus.qbft.core.types;

import org.hyperledger.besu.consensus.common.bft.BlockTimer;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Address;

import java.time.Clock;
import java.util.Collection;

public interface QbftFinalState {
  ValidatorMulticaster getValidatorMulticaster();

  NodeKey getNodeKey();

  RoundTimer getRoundTimer();

  boolean isLocalNodeValidator();

  Collection<Address> getValidators();

  Address getLocalAddress();

  Clock getClock();

  QbftBlockCreatorFactory getBlockCreatorFactory();

  int getQuorum();

  BlockTimer getBlockTimer();

  boolean isLocalNodeProposerForRound(ConsensusRoundIdentifier roundIdentifier);
}

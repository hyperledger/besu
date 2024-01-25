/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.Collections;

public class SnapProtocolManagerTestUtil {

  public static SnapProtocolManager create(final EthPeers ethPeers) {
    return create(
        BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST).getWorldArchive(), ethPeers);
  }

  public static SnapProtocolManager create(
      final WorldStateArchive worldStateArchive, final EthPeers ethPeers) {

    EthMessages messages = new EthMessages();

    return new SnapProtocolManager(Collections.emptyList(), ethPeers, messages, worldStateArchive);
  }

  public static SnapProtocolManager create(
      final WorldStateArchive worldStateArchive,
      final EthPeers ethPeers,
      final EthMessages snapMessages) {
    return new SnapProtocolManager(
        Collections.emptyList(), ethPeers, snapMessages, worldStateArchive);
  }

  public static RespondingEthPeer createPeer(
      final EthProtocolManager ethProtocolManager,
      final SnapProtocolManager snapProtocolManager,
      final long estimatedHeight) {
    return RespondingEthPeer.builder()
        .ethProtocolManager(ethProtocolManager)
        .snapProtocolManager(snapProtocolManager)
        .estimatedHeight(estimatedHeight)
        .build();
  }
}

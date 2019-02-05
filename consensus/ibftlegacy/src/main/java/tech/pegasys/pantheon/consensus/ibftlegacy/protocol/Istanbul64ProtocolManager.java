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
package tech.pegasys.pantheon.consensus.ibftlegacy.protocol;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

import java.util.List;

import com.google.common.collect.Lists;

/** This allows for interoperability with Quorum, but shouldn't be used otherwise. */
public class Istanbul64ProtocolManager extends EthProtocolManager {

  public Istanbul64ProtocolManager(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final int networkId,
      final boolean fastSyncEnabled,
      final int syncWorkers,
      final int txWorkers,
      final int computationWorkers) {
    super(
        blockchain,
        worldStateArchive,
        networkId,
        fastSyncEnabled,
        syncWorkers,
        txWorkers,
        computationWorkers);
  }

  @Override
  public void processMessage(final Capability cap, final Message message) {
    if (cap.equals(Istanbul64Protocol.ISTANBUL64)) {
      if (message.getData().getCode() != Istanbul64Protocol.INSTANBUL_MSG) {
        super.processMessage(EthProtocol.ETH63, message);
      } else {
        // TODO(tmm): Determine if the message should be routed to ibftController at a later date.
      }
    }
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    final List<Capability> result = Lists.newArrayList(Istanbul64Protocol.ISTANBUL64);
    result.addAll(super.getSupportedCapabilities());
    return result;
  }

  @Override
  public String getSupportedProtocol() {
    return Istanbul64Protocol.get().getName();
  }
}

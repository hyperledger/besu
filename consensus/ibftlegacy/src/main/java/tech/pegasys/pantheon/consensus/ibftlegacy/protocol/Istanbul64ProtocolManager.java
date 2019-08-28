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

import static java.util.Collections.singletonList;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.math.BigInteger;
import java.time.Clock;
import java.util.List;

/** This allows for interoperability with Quorum, but shouldn't be used otherwise. */
public class Istanbul64ProtocolManager extends EthProtocolManager {

  public Istanbul64ProtocolManager(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final BigInteger networkId,
      final boolean fastSyncEnabled,
      final int syncWorkers,
      final int txWorkers,
      final int computationWorkers,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration) {
    super(
        blockchain,
        worldStateArchive,
        networkId,
        fastSyncEnabled,
        syncWorkers,
        txWorkers,
        computationWorkers,
        clock,
        metricsSystem,
        ethereumWireProtocolConfiguration);
  }

  public Istanbul64ProtocolManager(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final BigInteger networkId,
      final boolean fastSyncEnabled,
      final int syncWorkers,
      final int txWorkers,
      final int computationWorkers,
      final Clock clock,
      final MetricsSystem metricsSystem) {
    super(
        blockchain,
        worldStateArchive,
        networkId,
        fastSyncEnabled,
        syncWorkers,
        txWorkers,
        computationWorkers,
        clock,
        metricsSystem);
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return singletonList(Istanbul64Protocol.ISTANBUL64);
  }

  @Override
  public String getSupportedProtocol() {
    return Istanbul64Protocol.get().getName();
  }
}

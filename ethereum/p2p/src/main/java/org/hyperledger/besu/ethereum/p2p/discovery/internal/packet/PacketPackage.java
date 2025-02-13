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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet;

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrrequest.EnrRequestPacketDataFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrresponse.EnrResponsePacketDataFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.findneighbors.FindNeighborsPacketDataFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.neighbors.NeighborsPacketDataFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.ping.PingPacketDataFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.pong.PongPacketDataFactory;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = PacketModule.class)
public interface PacketPackage {
  PacketFactory packetFactory();

  PacketSerializer packetSerializer();

  PacketDeserializer packetDeserializer();

  PingPacketDataFactory pingPacketDataFactory();

  PongPacketDataFactory pongPacketDataFactory();

  FindNeighborsPacketDataFactory findNeighborsPacketDataFactory();

  NeighborsPacketDataFactory neighborsPacketDataFactory();

  EnrRequestPacketDataFactory enrRequestPacketDataFactory();

  EnrResponsePacketDataFactory enrResponsePacketDataFactory();
}

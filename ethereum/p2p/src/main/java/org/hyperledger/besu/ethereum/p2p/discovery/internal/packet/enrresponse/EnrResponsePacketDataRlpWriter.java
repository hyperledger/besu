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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrresponse;

import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EnrResponsePacketDataRlpWriter {
  public @Inject EnrResponsePacketDataRlpWriter() {}

  public void writeTo(final EnrResponsePacketData enrResponsePacketData, final RLPOutput out) {
    out.startList();
    out.writeBytes(enrResponsePacketData.getRequestHash());
    out.writeRLPBytes(enrResponsePacketData.getEnr().serialize());
    out.endList();
  }
}

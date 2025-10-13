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

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.NodeRecordValidator;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.RequestHashValidator;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

@Singleton
public class EnrResponsePacketDataFactory {
  private final RequestHashValidator requestHashValidator;
  private final NodeRecordValidator nodeRecordValidator;

  public @Inject EnrResponsePacketDataFactory(
      final RequestHashValidator requestHashValidator,
      final NodeRecordValidator nodeRecordValidator) {
    this.requestHashValidator = requestHashValidator;
    this.nodeRecordValidator = nodeRecordValidator;
  }

  public EnrResponsePacketData create(final Bytes requestHash, final NodeRecord enr) {
    requestHashValidator.validate(requestHash);
    nodeRecordValidator.validate(enr);
    return new EnrResponsePacketData(requestHash, enr);
  }
}

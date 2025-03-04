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

import org.hyperledger.besu.crypto.SECPSignature;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;

@Singleton
public class PacketSignatureEncoder {

  public @Inject PacketSignatureEncoder() {}

  public Bytes encodeSignature(final SECPSignature signature) {
    final MutableBytes encoded = MutableBytes.create(65);
    UInt256.valueOf(signature.getR()).copyTo(encoded, 0);
    UInt256.valueOf(signature.getS()).copyTo(encoded, 32);
    final int v = signature.getRecId();
    encoded.set(64, (byte) v);
    return encoded;
  }
}

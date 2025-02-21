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

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import java.time.Clock;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

@Module
abstract class PacketModule {
  @Provides
  @Singleton
  static SignatureAlgorithm signatureAlgorithm() {
    return SignatureAlgorithmFactory.getInstance();
  }

  @Provides
  @Singleton
  static Clock clock() {
    return Clock.systemUTC();
  }

  @Provides
  @Singleton
  static NodeRecordFactory nodeRecordFactory() {
    return NodeRecordFactory.DEFAULT;
  }
}

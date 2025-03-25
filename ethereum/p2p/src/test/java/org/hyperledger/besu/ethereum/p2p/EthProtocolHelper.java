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
package org.hyperledger.besu.ethereum.p2p;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

public class EthProtocolHelper {
  private static final int ETH_LATEST_PROTOCOL_VERSION = 68;
  private static final Capability ETH_LATEST_PROTOCOL_CAPABILITY =
      Capability.create("eth", ETH_LATEST_PROTOCOL_VERSION);

  public static Capability getLatestVersion() {
    return ETH_LATEST_PROTOCOL_CAPABILITY;
  }
}

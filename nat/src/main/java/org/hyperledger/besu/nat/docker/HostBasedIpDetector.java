/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.nat.docker;

import org.hyperledger.besu.nat.core.IpDetector;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/** The Host based Ip detector. */
public class HostBasedIpDetector implements IpDetector {

  private static final String HOSTNAME = "HOST_IP";

  /** Default constructor */
  public HostBasedIpDetector() {}

  @Override
  @SuppressWarnings("AddressSelection")
  public Optional<String> detectAdvertisedIp() {
    try {
      return Optional.of(InetAddress.getByName(HOSTNAME).getHostAddress());
    } catch (final UnknownHostException e) {
      return Optional.empty();
    }
  }
}

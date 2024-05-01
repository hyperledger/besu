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
package org.hyperledger.besu.nat.core;

import java.util.Optional;

/** The interface Ip detector. */
public interface IpDetector {

  /**
   * Detect advertised ip.
   *
   * @return the optional Ip in String
   * @throws Exception in case of error while detecting advertised Ip
   */
  Optional<String> detectAdvertisedIp() throws Exception;
}

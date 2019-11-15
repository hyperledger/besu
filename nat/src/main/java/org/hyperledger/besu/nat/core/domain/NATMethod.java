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
package org.hyperledger.besu.nat.core.domain;

/**
 * This enum describes all supported NAT methods in Besu.
 *
 * <ul>
 *   <li><b>UPNP:</b> Universal Plug and Play is used to perform NAT tasks.
 *   <li><b>DOCKER:</b> Besu node is running inside a Docker container.
 *   <li><b>KUBERNETES:</b> Besu node is running inside a Kubernetes pod.
 * </ul>
 */
public enum NATMethod {
  UPNP,
  DOCKER,
  KUBERNETES,
  NONE;

  /**
   * Parses and returns corresponding enum value to the passed method name. This method throws an
   * {@link IllegalStateException} if the method name is invalid.
   *
   * @param natMethodName The name of the NAT method.
   * @return The corresponding {@link NATMethod}
   */
  public static NATMethod fromString(final String natMethodName) {
    for (final NATMethod mode : NATMethod.values()) {
      if (mode.name().equalsIgnoreCase(natMethodName)) {
        return mode;
      }
    }
    throw new IllegalStateException(String.format("Invalid NAT type provided: %s", natMethodName));
  }
}

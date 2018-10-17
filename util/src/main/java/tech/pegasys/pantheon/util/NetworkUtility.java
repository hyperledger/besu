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
package tech.pegasys.pantheon.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class NetworkUtility {

  private NetworkUtility() {}

  /**
   * Checks the port is not null and is in the valid range port (1-65536).
   *
   * @param port The port to check.
   * @return True if the port is valid, false otherwise.
   */
  public static boolean isValidPort(final int port) {
    return port > 0 && port < 65536;
  }

  public static String urlForSocketAddress(final String scheme, final InetSocketAddress address) {
    String hostName = address.getHostName();
    if ("0.0.0.0".equals(hostName)) {
      hostName = InetAddress.getLoopbackAddress().getHostName();
    }
    if ("0:0:0:0:0:0:0:0".equals(hostName)) {
      hostName = InetAddress.getLoopbackAddress().getHostName();
    }
    if (hostName.contains(":")) {
      hostName = "[" + hostName + "]";
    }
    return scheme + "://" + hostName + ":" + address.getPort();
  }
}

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
package org.hyperledger.besu.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class NetworkUtility {
  public static final String INADDR_ANY = "0.0.0.0";
  public static final String INADDR6_ANY = "0:0:0:0:0:0:0:0";

  private NetworkUtility() {}

  private static final Supplier<Boolean> ipv6Available =
      Suppliers.memoize(NetworkUtility::checkIpv6Availability);

  /**
   * Is IPv6 available?
   *
   * @return Returns true if the machine reports having any IPv6 addresses.
   */
  public static boolean isIPv6Available() {
    return ipv6Available.get();
  }

  /**
   * The standard for IPv6 availability is if the machine has any IPv6 addresses.
   *
   * @return Returns true if any IPv6 addresses are iterable via {@link NetworkInterface}.
   */
  private static Boolean checkIpv6Availability() {
    try {
      return NetworkInterface.networkInterfaces()
          .flatMap(NetworkInterface::inetAddresses)
          .anyMatch(addr -> addr instanceof Inet6Address);
    } catch (final Exception ignore) {
      // Any exception means we treat it as not available.
    }
    return false;
  }

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
    if (isUnspecifiedAddress(hostName)) {
      hostName = InetAddress.getLoopbackAddress().getHostName();
    }
    if (hostName.contains(":")) {
      hostName = "[" + hostName + "]";
    }
    return scheme + "://" + hostName + ":" + address.getPort();
  }

  public static boolean isNetworkInterfaceAvailable(final String ipAddress)
      throws SocketException, UnknownHostException {
    if (isUnspecifiedAddress(ipAddress)) {
      return true;
    }
    return NetworkInterface.getByInetAddress(InetAddress.getByName(ipAddress)) != null;
  }

  public static boolean isUnspecifiedAddress(final String ipAddress) {
    return INADDR_ANY.equals(ipAddress) || INADDR6_ANY.equals(ipAddress);
  }

  public static void checkPort(final int port, final String portTypeName) {
    if (!isValidPort(port)) {
      throw new IllegalPortException(
          String.format(
              "%s port requires a value between 1 and 65535. Got %d.", portTypeName, port));
    }
  }
}

package net.consensys.pantheon.util;

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

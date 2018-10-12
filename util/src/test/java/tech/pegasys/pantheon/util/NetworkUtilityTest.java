package tech.pegasys.pantheon.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

import org.junit.Test;

public class NetworkUtilityTest {

  @Test
  public void urlForSocketAddressHandlesIPv6() {
    final InetSocketAddress ipv6All = new InetSocketAddress("::", 80);
    assertFalse(NetworkUtility.urlForSocketAddress("http", ipv6All).contains("::"));
    assertFalse(NetworkUtility.urlForSocketAddress("http", ipv6All).contains("0:0:0:0:0:0:0:0"));
    final InetSocketAddress ipv6 = new InetSocketAddress("1:2:3:4:5:6:7:8", 80);
    assertTrue(NetworkUtility.urlForSocketAddress("http", ipv6).contains("[1:2:3:4:5:6:7:8]"));
  }
}

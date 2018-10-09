package net.consensys.pantheon.ethereum.jsonrpc.internal.results;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.Test;

public class NetworkResultTest {

  @Test
  public void localAndRemoteAddressShouldNotStartWithForwardSlash() {
    final SocketAddress socketAddress = new InetSocketAddress("1.2.3.4", 7890);
    final NetworkResult networkResult = new NetworkResult(socketAddress, socketAddress);

    assertThat(networkResult.getLocalAddress()).isEqualTo("1.2.3.4:7890");
    assertThat(networkResult.getRemoteAddress()).isEqualTo("1.2.3.4:7890");
  }
}

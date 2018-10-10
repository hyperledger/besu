package net.consensys.pantheon.ethereum.jsonrpc.internal.results;

import java.net.SocketAddress;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"localAddress", "remoteAddress"})
public class NetworkResult {

  private final String localAddress;
  private final String remoteAddress;

  public NetworkResult(final SocketAddress localAddress, final SocketAddress remoteAddress) {
    this.localAddress = removeTrailingSlash(localAddress.toString());
    this.remoteAddress = removeTrailingSlash(remoteAddress.toString());
  }

  @JsonGetter(value = "localAddress")
  public String getLocalAddress() {
    return localAddress;
  }

  @JsonGetter(value = "remoteAddress")
  public String getRemoteAddress() {
    return remoteAddress;
  }

  private String removeTrailingSlash(final String address) {
    if (address != null && address.startsWith("/")) {
      return address.substring(1);
    } else {
      return address;
    }
  }
}

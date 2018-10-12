package net.consensys.pantheon.ethereum.jsonrpc.internal.filter;

import java.util.UUID;

public class FilterIdGenerator {

  public String nextId() {
    return "0x" + UUID.randomUUID().toString().replaceAll("-", "");
  }
}

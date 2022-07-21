package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthProtocolLogger {
  private static final Logger LOG = LoggerFactory.getLogger(EthProtocolLogger.class);

  public static void logProcessMessage(final Capability cap, final int code) {
    LOG.trace("Process message {}, {}", cap, code);
  }
}

package net.consensys.pantheon.consensus.ibft;

import net.consensys.pantheon.ethereum.p2p.api.Message;

/** Static helper functions for producing and working with IbftEvent objects */
public class IbftEvents {
  public static IbftEvent fromMessage(final Message message) {
    throw new IllegalStateException("No IbftEvents are implemented yet");
  }

  public enum Type {
    ROUND_EXPIRY
  }
}

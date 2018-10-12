package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.ibft.IbftEvents.Type;

/** Category of events that will effect and are interpretable by the Ibft processing mechanism */
public interface IbftEvent {
  Type getType();
}

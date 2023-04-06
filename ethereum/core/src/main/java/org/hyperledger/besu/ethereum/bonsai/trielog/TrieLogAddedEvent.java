package org.hyperledger.besu.ethereum.bonsai.trielog;

public class TrieLogAddedEvent {

  private final TrieLogLayer layer;

  public TrieLogAddedEvent(final TrieLogLayer layer) {
    this.layer = layer;
  }

  public TrieLogLayer getLayer() {
    return layer;
  }

  @FunctionalInterface
  interface TrieLogAddedObserver {

    void onTrieLogAdded(TrieLogAddedEvent event);
  }
}

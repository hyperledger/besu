package org.hyperledger.besu.ethereum.bonsai.worldview;

public class BonsaiWorldStateConfig {

  private boolean isFrozen;

  private boolean isTrieDisabled;

  public BonsaiWorldStateConfig() {
    this(false, false);
  }

  public BonsaiWorldStateConfig(final boolean isTrieDisabled) {
    this(false, isTrieDisabled);
  }

  public BonsaiWorldStateConfig(final BonsaiWorldStateConfig config) {
    this(config.isFrozen(), config.isTrieDisabled());
  }

  public BonsaiWorldStateConfig(final boolean isFrozen, final boolean isTrieDisabled) {
    this.isFrozen = isFrozen;
    this.isTrieDisabled = isTrieDisabled;
  }

  public boolean isFrozen() {
    return isFrozen;
  }

  public void setFrozen(final boolean frozen) {
    isFrozen = frozen;
  }

  public boolean isTrieDisabled() {
    return isTrieDisabled;
  }

  public void setTrieDisabled(final boolean trieDisabled) {
    isTrieDisabled = trieDisabled;
  }
}

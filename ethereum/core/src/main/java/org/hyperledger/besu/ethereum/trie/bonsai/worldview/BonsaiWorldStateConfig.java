package org.hyperledger.besu.ethereum.trie.bonsai.worldview;

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

  /**
   * Checks if the world state is frozen. When the world state is frozen, it cannot mutate.
   *
   * @return true if the world state is frozen, false otherwise.
   */
  public boolean isFrozen() {
    return isFrozen;
  }

  /**
   * Sets the frozen status of the world state. When the world state is frozen, it cannot mutate.
   *
   * @param frozen the new frozen status to set.
   */
  public void setFrozen(final boolean frozen) {
    isFrozen = frozen;
  }

  /**
   * Checks if the trie is disabled for the world state. When the trie is disabled, the world state
   * will only work with the flat database and not the trie. In this mode, it's impossible to verify
   * the state root.
   *
   * @return true if the trie is disabled, false otherwise.
   */
  public boolean isTrieDisabled() {
    return isTrieDisabled;
  }

  /**
   * Sets the disabled status of the trie for the world state. When the trie is disabled, the world
   * state will only work with the flat database and not the trie. In this mode, it's impossible to
   * verify the state root.
   *
   * @param trieDisabled the new disabled status to set for the trie.
   */
  public void setTrieDisabled(final boolean trieDisabled) {
    isTrieDisabled = trieDisabled;
  }
}

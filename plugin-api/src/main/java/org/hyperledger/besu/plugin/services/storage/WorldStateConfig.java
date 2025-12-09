package org.hyperledger.besu.plugin.services.storage;

public interface WorldStateConfig {
    boolean isTrieDisabled();
    boolean isStateful();
    void setTrieDisabled(final boolean trieDisabled);
    void setStateful(final boolean stateful);
}

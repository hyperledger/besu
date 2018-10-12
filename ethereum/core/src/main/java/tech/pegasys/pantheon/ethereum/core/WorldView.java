package net.consensys.pantheon.ethereum.core;

/** Generic interface for a view over the accounts of the world state. */
public interface WorldView {
  WorldView EMPTY = address -> null;

  /**
   * Get an account provided it's address.
   *
   * @param address the address of the account to retrieve.
   * @return the {@link Account} corresponding to {@code address} or {@code null} if there is no
   *     such account.
   */
  Account get(Address address);
}

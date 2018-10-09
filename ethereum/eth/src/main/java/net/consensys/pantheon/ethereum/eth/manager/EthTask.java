package net.consensys.pantheon.ethereum.eth.manager;

import java.util.concurrent.CompletableFuture;

public interface EthTask<T> {

  CompletableFuture<T> run();

  void cancel();
}

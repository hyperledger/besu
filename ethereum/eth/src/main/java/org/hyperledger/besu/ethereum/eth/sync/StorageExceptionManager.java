package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.plugin.services.exception.StorageException;

public final class StorageExceptionManager {

  private static final String rocksdbClassName = "org.rocksdb.RocksDBException";
  private static final String ERR_BUSY = "Busy";
  private static final String ERR_LOCK_TIMED_OUT = "TimedOut(LockTimeout)";

  public static boolean canRetryOnError(final StorageException e) {
    return e.getMessage().contains(rocksdbClassName)
        && (e.getMessage().contains(ERR_BUSY) || e.getMessage().contains(ERR_LOCK_TIMED_OUT));
  }
}

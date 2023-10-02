package org.hyperledger.besu.plugin.services.storage.rocksdb;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

@ExtendWith(MockitoExtension.class)
public class RocksDBTransactionTest {
  @TempDir public Path folder;

  @Mock(answer = RETURNS_DEEP_STUBS)
  RocksDBMetrics mockMetrics;

  @Mock Transaction mockTransaction;
  @Mock WriteOptions mockOptions;

  RocksDBTransaction tx;

  @BeforeEach
  void setupTx() {
    tx = spy(new RocksDBTransaction(__ -> null, mockTransaction, mockOptions, mockMetrics));
  }

  @Test
  public void assertNominalBehavior() throws Exception {
    assertThatCode(tx::commit).doesNotThrowAnyException();
  }

  @Test
  public void assertDefaultBusyRetryBehavior() throws Exception {
    doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doNothing()
        .when(mockTransaction)
        .commit();

    assertThatCode(tx::commit).doesNotThrowAnyException();
  }

  @Test
  public void assertExplicitBusyRetryBehavior() throws Exception {
    doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("Busy"))
        .doNothing()
        .when(mockTransaction)
        .commit();

    assertThatCode(() -> tx.commitWithRetries(10)).doesNotThrowAnyException();
  }

  @Test
  public void assertLockTimeoutBusyRetryBehavior() throws Exception {
    doThrow(new RocksDBException("Busy"))
        .doThrow(new RocksDBException("TimedOut(LockTimeout)"))
        .doThrow(new RocksDBException("TimedOut(LockTimeout)"))
        .doNothing()
        .when(mockTransaction)
        .commit();

    assertThatCode(() -> tx.commitWithRetries(7)).doesNotThrowAnyException();
  }

  @Test
  public void assertBusyRetryFailBehavior() throws Exception {
    doThrow(new RocksDBException("Busy")).when(mockTransaction).commit();

    assertThatThrownBy(tx::commit)
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(RocksDBException.class)
        .hasMessageContaining("Busy");
  }

  @Test
  public void assertRocksTxCloseOnRetryDoesNotThrow() throws Exception {
    try (final OptimisticTransactionDB db =
        OptimisticTransactionDB.open(new Options().setCreateIfMissing(true), folder.toString())) {
      var writeOptions = new WriteOptions();
      Transaction innerTx = spy(db.beginTransaction(writeOptions));

      tx = spy(new RocksDBTransaction(__ -> null, innerTx, writeOptions, mockMetrics));

      doThrow(new RocksDBException("Busy"))
          .doThrow(new RocksDBException("Busy"))
          .doCallRealMethod()
          .when(innerTx)
          .commit();

      assertThatCode(tx::commit).doesNotThrowAnyException();
    }
  }
}

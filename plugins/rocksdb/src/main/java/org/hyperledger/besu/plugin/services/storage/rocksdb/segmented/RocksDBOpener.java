package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import org.rocksdb.DBOptions;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RocksDBOpener {
    public static final int DEFAULT_DELAY = 60;
    private static final Logger LOG = LoggerFactory.getLogger(RocksDBOpener.class);

    public static OptimisticTransactionDB openOptimisticTransactionDBWithWarning(
            final DBOptions options,
            final String dbPath,
            final List<ColumnFamilyDescriptor> columnDescriptors,
            final List<ColumnFamilyHandle> columnHandles) throws Exception {

        AtomicBoolean operationCompleted = new AtomicBoolean(false);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            if (!operationCompleted.get()) {
                LOG.warn("Opening RocksDB database is taking longer than 60 seconds... " +
                        "This may be due to prolonged RocksDB compaction.");
            }
        },DEFAULT_DELAY, TimeUnit.SECONDS);

        try {
            OptimisticTransactionDB db = OptimisticTransactionDB.open(options, dbPath, columnDescriptors, columnHandles);
            operationCompleted.set(true);
            return db;
        } finally {
            scheduler.shutdown();
        }
    }

    public static TransactionDB openTransactionDBWithWarning(
            final DBOptions options,
            final TransactionDBOptions transactionDBOptions,
            final String dbPath,
            final List<ColumnFamilyDescriptor> columnDescriptors,
            final List<ColumnFamilyHandle> columnHandles) throws Exception {

        AtomicBoolean operationCompleted = new AtomicBoolean(false);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            if (!operationCompleted.get()) {
                LOG.warn("Opening RocksDB database is taking longer than 60 seconds... " +
                        "This may be due to prolonged RocksDB compaction.");
            }
        }, DEFAULT_DELAY, TimeUnit.SECONDS);

        try {
            TransactionDB db =
                    TransactionDB.open(
                            options,
                            transactionDBOptions,
                            dbPath,
                            columnDescriptors,
                            columnHandles);
            operationCompleted.set(true);

            return db;
        } finally {
            // Ensure the scheduler shuts down after use
            scheduler.shutdown();
        }
    }

}


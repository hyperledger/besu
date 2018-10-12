package tech.pegasys.pantheon.services.kvstore;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class RocksDbKeyValueStorageTest extends AbstractKeyValueStorageTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Override
  protected KeyValueStorage createStore() throws Exception {
    return RocksDbKeyValueStorage.create(folder.newFolder().toPath());
  }
}

package linea;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @AutoService(BesuPlugin.class)
public class LineaTransactionSelectorPlugin implements BesuPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionSelectionService.class);

  @Override
  public Optional<String> getName() {
    return BesuPlugin.super.getName();
  }

  @Override
  public void register(final BesuContext context) {
    context
        .getService(TransactionSelectionService.class)
        .ifPresentOrElse(
            this::createAndRegister,
            () ->
                LOG.error(
                    "Failed to register TransactionSelectionService due to a missing TransactionSelectionService."));
  }

  private void createAndRegister(final TransactionSelectionService transactionSelectionService) {
    transactionSelectionService.registerTransactionSeclectorFactory(
        new LineaTransactionSelectorFactory());
  }

  @Override
  public void beforeExternalServices() {
    BesuPlugin.super.beforeExternalServices();
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}

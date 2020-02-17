# Private Transactions Migration 

Hyperledger Besu v1.4 implements a new data structure for private state storage that is not backwards compatible. 
A migration will be performed when starting v1.4 for the first time to reprocess existing private transactions 
and re-create the private state data in the v1.4 format. 

**Important**  

All nodes with existing private transactions will be migrated to the new private state storage 
when upgrading to v1.4. It is not possible to upgrade to v1.4 without migrating. 

## How to migrate 

**Important** 

As a precaution (that is, resyncing should not be required), ensure your Hyperledger Besu database is backed-up 
or other Besu nodes in your network are available to resync from if the migration does not complete as expected.  

We recommend that all nodes in a network do not upgrade and migrate at once. 

To migrate, add the `--privacy-enable-database-migration` flag to your Besu command line options. The migration starts 
automatically when this flag is specified. If you have existing private transactions and do not specify this flag, 
v1.4 will not start.  

After the migration starts, logs display the progress.  When the migration is complete, Besu continues 
starting up as usual. 

## During migration  

Do not stop the migration running once it has started. If the migration is stopped, you will need to restore
your Besu database from backup and restart the migration process. 

## Migration support 

If you have a long running network with a large volume of private transactions and/or would like to discuss
the migration process with us before upgrading, contact us at support@pegasys.tech
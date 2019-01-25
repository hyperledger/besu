description: Ethereum accounts used for testing only on private network
<!--- END of page meta data -->

# Accounts for Testing

You can use existing accounts for testing by including them in the genesis file for a private network. 
Alternatively, Pantheon provides predefined accounts in development mode. 
 
## Development Mode
 
 When you start Pantheon with the [`--network=dev`](../Reference/Pantheon-CLI-Syntax.md#network) 
 command line option, the `dev.json` genesis file is used by default. 
 
 The `dev.json` genesis file defines the accounts below that can be used for testing. 

{!global/test_accounts.md!}
 
## Genesis File 
 
To use existing test accounts, specify the accounts and balances in a genesis file for your test network.
For an example of defining accounts in the genesis file, refer to [`dev.json`](https://github.com/PegaSysEng/pantheon/blob/master/config/src/main/resources/dev.json).
 
Use the [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) command line option to 
start Pantheon with the genesis file defining the existing accounts.

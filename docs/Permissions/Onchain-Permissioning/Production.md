description: Deploying Permissioning Management Dapp for production 
<!--- END of page meta data -->

# Deploying Permissioning Management Dapp for Production 

To deploy the Permissioning Management dapp for production: 

1. Get the most recent release (tarball or zip) from the [projects release page](https://github.com/PegaSysEng/permissioning-smart-contracts/releases/latest). 

1. Unpack the distribution into a directory available to your webserver.

1. In the root of the directory to which the distribution was unpacked, add a file called `config.json` replacing 
the placeholders.  
   
     ```json tab="config.json" 
     {
       "accountIngressAddress":  "<Address of the account ingress contract>",
       "nodeIngressAddress": "<Address of the node ingress contract>",
       "networkId": "<ID of your Ethereum network>"
     }
     ```

1. On your webserver, host the contents of the directory as static files and direct root requests to `index.html`

## Starting a Production Permissioned Network 

Follow the procedure as for [Getting Started with Onchain Perissioning](Getting-Started-Onchain-Permissioning.md)
but do not perform the steps using `yarn` to install, build, and start the development server. Instead follow the procedure above to 
deploy the Permissioning Management dapp to your webserver.  
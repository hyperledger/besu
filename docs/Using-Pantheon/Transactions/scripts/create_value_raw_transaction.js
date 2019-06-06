    const Web3 = require('web3')
    const ethTx = require('ethereumjs-tx')
    const readline = require('readline');

    async function askQuestion(query) {
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout,
        });

        return new Promise(resolve => rl.question(query, ans => {
            rl.close();
            resolve(ans);
        }))
    }

    const args = process.argv.slice(2);

    // web3 initialization - must point to the HTTP JSON-RPC endpoint
    var provider = args[0] || 'http://localhost:8545';
    console.log("******************************************");
    console.log("Using provider : " + provider);
    console.log("******************************************");
    var web3 = new Web3(new Web3.providers.HttpProvider(provider))
    web3.transactionConfirmationBlocks = 1;
    // Sender address and private key
    // Second acccount in dev.json genesis file
    // Exclude 0x at the beginning of the private key
    const addressFrom = '0x627306090abaB3A6e1400e9345bC60c78a8BEf57'
    const privKey = Buffer.from('c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3', 'hex')

    // Receiver address and value to transfer
    // Third account in dev.json genesis file
    const addressTo = '0xf17f52151EbEF6C7334FAD080c5704D77216b732'
    const valueInEther = 2

    // Get the address transaction count in order to specify the correct nonce
    web3.eth.getTransactionCount(addressFrom, "pending").then((txnCount) => {
      // Create the transaction object
      var txObject = {
          nonce: web3.utils.numberToHex(txnCount),
          gasPrice: web3.utils.numberToHex(1000),
          gasLimit: web3.utils.numberToHex(21000),
          to: addressTo,
          value: web3.utils.numberToHex(web3.utils.toWei(valueInEther.toString(), 'ether'))
      };

      // Sign the transaction with the private key
      var tx = new ethTx(txObject);
      tx.sign(privKey)

      //Convert to raw transaction string
      var serializedTx = tx.serialize();
      var rawTxHex = '0x' + serializedTx.toString('hex');

      // log raw transaction data to the console so you can send it manually
      console.log("Raw transaction data: " + rawTxHex);

      // but also ask you if you want to send this transaction directly using web3
      (async() => {
        const ans = await askQuestion("******************************************\n\
    Do you want to send the signed value transaction now ? (Y/N):");
        if("y" == ans || "Y" == ans){
          // Send the signed transaction using web3
          web3.eth.sendSignedTransaction(rawTxHex)
            .on('receipt', receipt => { console.log('Receipt: ', receipt); })
            .catch(error => { console.log('Error: ', error.message); });
          console.log("******************************************");
          console.log("Value transaction sent, waiting for receipt.");
          console.log("******************************************");
        }else{
          console.log("******************************************");
          console.log("You can for instance send this transaction manually with the following command:");
          console.log("curl -X POST --data '{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\",\"params\":[\"" + rawTxHex + "\"],\"id\":1}'", provider);
        }
      })();

    })
    .catch(error => { console.log('Error: ', error.message); });

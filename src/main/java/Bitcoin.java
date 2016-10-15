import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Bitcoin {

    public static void main(String[] args) throws UnreadableWalletException {
        System.out.println("Creating an address:");
        createAddress(args);
        System.out.println("\nCreating a wallet:");
        Wallet wallet = createWallet();

        System.out.println("\nCreating a genesis block:");
        fetchGenesisBlock(wallet);/*
        System.out.println("\nSending coins:");
        sendBitcoins(args);*/
    }

    private static Address createAddress(String[] args) {
        // use test net by default
        String net = "test";

        if (args.length >= 1 && (args[0].equals("test") || args[0].equals("prod"))) {
            net = args[0];
            System.out.println("Using " + net + " network.");
        }

        // create a new EC Key ...
        ECKey key = new ECKey();

        // ... and look at the key pair
        System.out.println("We created key:\n" + key);

        // either test or production net are possible
        final NetworkParameters netParams;

        if (net.equals("prod")) {
            netParams = NetworkParameters.prodNet();
        } else {
            netParams = NetworkParameters.testNet();
        }

        // get valid Bitcoin address from public key
        Address addressFromKey = key.toAddress(netParams);

        System.out.println("On the " + net + " network, we can use this address:\n" + addressFromKey);
        return addressFromKey;
    }


    private static Wallet createWallet() {
        // work with testnet
        final NetworkParameters netParams = NetworkParameters.testNet();

        // Try to read the wallet from storage, create a new one if not possible.
        Wallet wallet = null;
        final File walletFile = new File("test.wallet");

        try {
            wallet = new Wallet(netParams);

            // 5 times
            for (int i = 0; i < 5; i++) {

                // create a key and add it to the wallet
                wallet.addKey(new ECKey());
            }

            // save wallet contents to disk
            wallet.saveToFile(walletFile);

        } catch (IOException e) {
            System.out.println("Unable to create wallet file.");
        }

        // fetch the first key in the wallet directly from the keychain ArrayList
        ECKey firstKey = wallet.currentReceiveKey();


        // output key
        System.out.println("First key in the wallet:\n" + firstKey);

        // and here is the whole wallet
        System.out.println("Complete content of the wallet:\n" + wallet);

        // we can use the hash of the public key
        // to check whether the key pair is in this wallet
        if (wallet.isPubKeyHashMine(firstKey.getPubKeyHash())) {
            System.out.println("Yep, that's my key.");
        } else {
            System.out.println("Nope, that key didn't come from this wallet.");
        }

        return wallet;
    }

   private static void fetchGenesisBlock(Wallet wallet) {
        // work with testnet
        final NetworkParameters networkParameters = NetworkParameters.testNet();

        // data structure for block chain storage
        BlockStore blockStore = new MemoryBlockStore(networkParameters);

        // declare object to store and understand block chain
        BlockChain blockChain;

        try {

            // initialize BlockChain object
            blockChain = new BlockChain(networkParameters, blockStore);

            // instantiate Peer object to handle connections
            final PeerGroup peerGroup = new PeerGroup(networkParameters, blockChain);
            // connect to peer node on localhost
            peerGroup.setUserAgent("Joey Sample wallet app", "1.2");
            peerGroup.addWallet(wallet);
            peerGroup.addPeerDiscovery(new DnsDiscovery(networkParameters));
            // TODO: replace with .startAsync and startBlockchainDownload(listener)
            peerGroup.start();
            //peerGroup.downloadBlockChain();

            // we found the hash of the genesis block on Bitcoin Block Explorer
            Sha256Hash blockHash = new Sha256Hash("00000007199508e34a9ff81e6ec0c477a4cccff2a4767a8eee39c11db367b008");

            // ask the node to which we're connected for the block
            // and wait for a response
            Future<Block> future = peerGroup.getDownloadPeer().getBlock(blockHash);
            System.out.println("Waiting for node to send us the requested block: " + blockHash);

            // get and use the Block's toString() to output the genesis block
            Block block = future.get();
            System.out.println("Here is the genesis block:\n" + block);

            // we're done; disconnect from the peer node
            peerGroup.stop();

            // handle the various exceptions; this needs more work
        } catch (BlockStoreException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }/*

    private static void sendBitcoins(String[] args) throws UnreadableWalletException {
        if (args.length != 4) {
            System.out.println("Usage: java SendCoins prod|test wallet amount recipient");
            System.exit(1);
        }

        // we get the following from the command line ...
        // (this is not secure - needs validation)
        String network = args[0];  // "test" or "prod"
        String walletFileName = args[1];  // wallet file name
        String amountToSend = args[2];  // milli-BTC
        String recipient = args[3];  // Bitcoin address

        // the Bitcoin network to use
        final NetworkParameters netParams;

        // check for production Bitcoin network ...
        if (network.equalsIgnoreCase("prod")) {
            netParams = NetworkParameters.prodNet();
            // ... otherwise use the testnet
        } else {
            netParams = NetworkParameters.testNet();
        }

        // data structure for block chain storage
        BlockStore blockStore = new MemoryBlockStore(netParams);

        // declare object to store and understand block chain
        BlockChain chain;

        // declare wallet
        Wallet wallet;

        try {

            // wallet file that contains Bitcoins we can send
            final File walletFile = new File(walletFileName);

            // load wallet from file
            wallet = Wallet.loadFromFile(walletFile);

            // how man milli-Bitcoins to send
            BigInteger btcToSend = new BigInteger(amountToSend);

            // initialize BlockChain object
            chain = new BlockChain(netParams, wallet, blockStore);

            // instantiate Peer object to handle connections
            final Peer peer = new Peer(netParams, new PeerAddress(InetAddress.getLocalHost()), chain);

            // connect to peer node on localhost
            peer.connect();

            // recipient address provided by official Bitcoin client
            Address recipientAddress = new Address(netParams, recipient);

            // tell peer to send amountToSend to recipientAddress
            Transaction sendTxn = wallet.sendCoins(peer, recipientAddress, btcToSend);

            // null means we didn't have enough Bitcoins in our wallet for the transaction
            if (sendTxn == null) {
                System.out.println("Cannot send requested amount of " + Utils.bitcoinValueToFriendlyString(btcToSend)
                        + " BTC; wallet only contains " + Utils.bitcoinValueToFriendlyString(wallet.getBalance()) + " BTC.");
            } else {

                // once communicated to the network (via our local peer),
                // the transaction will appear on Bitcoin explorer sooner or later
                System.out.println(Utils.bitcoinValueToFriendlyString(btcToSend) + " BTC sent. You can monitor the transaction here:\n"
                        + "http://blockexplorer.com/tx/" + sendTxn.getHashAsString());
            }

            // save wallet with new transaction(s)
            wallet.saveToFile(walletFile);

            // handle the various exceptions; this needs more work
        } catch (BlockStoreException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (PeerException e) {
            e.printStackTrace();
        } catch (AddressFormatException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/
}

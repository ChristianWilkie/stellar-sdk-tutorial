package org.stellar.sdk;

import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.ClaimableBalanceResponse;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import shadow.com.google.common.io.BaseEncoding;
import shadow.com.google.common.util.concurrent.AtomicDouble;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NetworkConnectionExample {
    private static final Logger log = Logger.getLogger( NetworkConnectionExample.class.getName() );
    private static BaseEncoding base32Encoding = BaseEncoding.base32().upperCase().omitPadding();

    public static void main(String[] args) throws Exception {
        Server server = new Server("https://horizon-testnet.stellar.org");
        Network network = Network.TESTNET;
        // For production use:
        // Server server = new Server("https://horizon.stellar.org");
        // Network network = Network.PUBLIC;
        // Edit this with your keypair!
        // Go to https://laboratory.stellar.org/#account-creator?network=test and click Generate Keypair
        KeyPair keyPair = StellarKeyUtils.createKeyPair("ADD_YOUR_FIRST_ACCOUNT_PRIVATE_KEY_HERE");

        log.info("Public key for account: " + keyPair.getAccountId());

        AccountResponse account;
        try {
           account = server.accounts().account(keyPair.getAccountId());
        } catch (Exception ex) {
            log.info("Account does not exist on network");
            throw ex;
        }

        log.info("Sequence number: " + account.getSequenceNumber());
        log.info("Home domain: " + account.getHomeDomain());
        log.info("Balances: ");
        Arrays.stream(account.getBalances()).collect(Collectors.toList()).forEach(balance -> {
            log.info("--> " + balance.getAssetType()
                    + " " + balance.getAssetCode().or("") + ": " + balance.getBalance());
            if (!balance.getAssetType().equals("native")) {
                log.info("----> issuer: " + balance.getAssetIssuer().or("n/a"));
            }
        });

        log.info("Data: ");
        account.getData().forEach((k, v) -> {
            log.info("--> " + k + ": " + new String(Base64.getDecoder().decode(v)));
            log.info("----> " + v);
        });

        log.info("Signers: ");
        Arrays.stream(account.getSigners()).collect(Collectors.toList()).forEach(signer -> {
            log.info("--> " + signer.getWeight() + ": " + signer.getKey());
            log.info("----> Type: " + signer.getType());
        });

        log.info("Thresholds: ");
        log.info("--> High: " + account.getThresholds().getHighThreshold());
        log.info("--> Med: " + account.getThresholds().getMedThreshold());
        log.info("--> Low: " + account.getThresholds().getLowThreshold());

        log.info("Claimable balances: ");
        ArrayList<ClaimableBalanceResponse> cbalances = getClaimableBalances(keyPair, server);
        cbalances.forEach(c -> {
            log.info("--> " + c.getId() + ": " + c.getAmount() + " " + c.getAssetString());
            log.info("----> From " + c.getSponsor().or("n/a"));
        });


        KeyPair destKeyPair = StellarKeyUtils.createPublicKeyFromString("ADD_YOUR_SECOND_ACCOUNT_KEYPAIR_HERE");
        // To claim and send XLM to another address use:
        claimAndSendXlm(keyPair, destKeyPair, server, network);
    }



    public static ArrayList<ClaimableBalanceResponse> getClaimableBalances(KeyPair kp, Server server)
            throws IOException, java.net.URISyntaxException {
        Page<ClaimableBalanceResponse> firstPage = server.claimableBalances().forClaimant(kp.getAccountId()).execute();
        return paginateClaimableBalanceResponses(firstPage, server);
    }

    public static ArrayList<ClaimableBalanceResponse> paginateClaimableBalanceResponses(
            Page<ClaimableBalanceResponse> firstPage, Server server) throws java.net.URISyntaxException, IOException {
        ArrayList<ClaimableBalanceResponse> cbalances = new ArrayList<>(firstPage.getRecords());
        Page<ClaimableBalanceResponse> nextPage = firstPage.getNextPage(server.getHttpClient());
        while (nextPage.getRecords().size() > 0) {
            cbalances.addAll(nextPage.getRecords());
            nextPage = nextPage.getNextPage(server.getHttpClient());
        }
        return cbalances;
    }

    public static void claimAndSendXlm(KeyPair source, KeyPair dest, Server server, Network network) throws Exception {
        AccountResponse account = server.accounts().account(source.getAccountId());
        List<ClaimableBalanceResponse> cbs = getClaimableBalances(source, server);
        Transaction.Builder t = new Transaction.Builder(account, network);

        AtomicDouble amount = new AtomicDouble(20);
        cbs.forEach(c -> {
            if (c.getAsset().getType().equals("native")) {
                ClaimClaimableBalanceOperation co = new ClaimClaimableBalanceOperation.Builder(c.getId()).build();
                t.addOperation(co);
                amount.set(amount.get() + Double.parseDouble(c.getAmount()));
            }
        });
        PaymentOperation p = new PaymentOperation.Builder(dest.getAccountId(),
                new AssetTypeNative(), String.valueOf(Math.floor(amount.get()))).build();
        t.addOperation(p);
        t.setTimeout(Transaction.Builder.TIMEOUT_INFINITE).setBaseFee(250);
        Transaction transaction = t.build();
        transaction.sign(source);
        server.submitTransaction(transaction);

        SubmitTransactionResponse response = server.submitTransaction((Transaction) transaction);
        if (response.isSuccess()) {
            log.info("Success!");
        } else {
            log.info("The transaction was submitted, but something went wrong. "
                    + response.getExtras().getResultCodes().getOperationsResultCodes());
            log.info(response.getExtras().getResultXdr());
        }

        log.info("Transaction XDR:");
        log.info(transaction.toEnvelopeXdrBase64());
    }


}

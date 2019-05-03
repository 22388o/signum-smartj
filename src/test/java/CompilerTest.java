
/*
 * This Java source file was generated by the Gradle 'init' task.
 */
import org.junit.Test;

import bt.compiler.Compiler;
import bt.sample.Forward;
import burst.kit.burst.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstID;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.BRSError;
import burst.kit.entity.response.BroadcastTransactionResponse;
import burst.kit.entity.response.GenerateTransactionResponse;
import burst.kit.service.BurstNodeService;
import io.reactivex.Single;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.BeforeClass;

/**
 * We assume a localhost testnet with 0 seconds mock mining for the tests to work.
 */
public class CompilerTest {

    static final String PASSPHRASE = "block talk, easy to use smart contracts for burst";
    static final String NODE = "http://localhost:6876";

    static BurstNodeService bns;
    static BurstCrypto bc;
    static BroadcastTransactionResponse response;

    @BeforeClass
    public void setup() {
        bns = BurstNodeService.getInstance(NODE);
        bc = BurstCrypto.getInstance();
        // forge a fitst block to get some balance
        forgeBlock();
    }

    @Test
    public void testForward() throws IOException {
        String bmf = "BURST-TSLQ-QLR9-6HRD-HCY22";
        String name = Forward.class.getName();

        registerAT(Forward.class);
        forgeBlock();

        BurstID atId = response.getTransactionID();

        bns.generateTransaction(BurstAddress.fromId(atId),
            bc.getPublicKey(PASSPHRASE), BurstValue.fromBurst(10), BurstValue.fromBurst(0.1), 1440)
        .flatMap(response -> {
            byte[] unsignedTransactionBytes = response.getUnsignedTransactionBytes().getBytes();
            byte[] signedTransactionBytes = bc.signTransaction(PASSPHRASE, unsignedTransactionBytes);
            return bns.broadcastTransaction(signedTransactionBytes);
        })
        .subscribe(this::onTransactionSent, this::handleError);
    }

    private void forgeBlock(){
        bns.submitNonce(PASSPHRASE, "0", null);
    }

    private void registerAT(Class c) throws IOException {
        Compiler comp = new Compiler(c.getName());
        comp.compile();
        comp.link();

        int deadline = 1440; // 4 days (in blocks of 4 minutes)
        byte[] pubkey = bc.getPublicKey(PASSPHRASE);
        Single<GenerateTransactionResponse> createAT = bns.generateCreateATTransaction(pubkey,
                BurstValue.fromBurst(1), deadline, c.getName(), c.getName(), new byte[0], comp.getCode().array(),
                new byte[0], 1, 1, 1, BurstValue.fromBurst(1));

        createAT.flatMap(response -> {
            byte[] unsignedTransactionBytes = response.getUnsignedTransactionBytes().getBytes();
            byte[] signedTransactionBytes = bc.signTransaction(PASSPHRASE, unsignedTransactionBytes);
            return bns.broadcastTransaction(signedTransactionBytes);
        }).subscribe(this::onTransactionSent, this::handleError);
    }


	private void onTransactionSent(BroadcastTransactionResponse r) {
		// Get the transaction ID of the newly sent transaction!
        response = r;
		System.out.println("Transaction sent! Transaction ID: " + r.getTransactionID().getID());
	}

	private void handleError(Throwable t) {
        fail(t.getMessage());
		if (t instanceof BRSError) {
			System.out.println("Caught BRS Error: " + ((BRSError) t).getDescription());
		} else {
			t.printStackTrace();
		}
	}
}

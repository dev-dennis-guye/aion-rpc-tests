package org.aion;

import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.rpc.client.Personal;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.web3j.Web3Provider;


public class Main {

    public static void main(String[] args) {
        Personal personal = new Personal(Web3Provider.getInstance());
        ECKey key = ECKeyFac.inst().create();
        String secret = "secret";
        ByteArrayWrapper secretBytes = ByteArrayWrapper.wrap(secret.getBytes());
        ByteArrayWrapper signature = ByteArrayWrapper.wrap(key.sign(secretBytes.toBytes()).toBytes());

        try{
            AionAddress pubkey=personal.ecRecover(secretBytes, signature);
            AionAddress expectedKey = new AionAddress(key.getAddress());
            if (pubkey.equals(expectedKey)){
                System.out.println("ecRecover:\t\t\tSUCCESS");
            }else {
                System.out.println("ecRecover:\t\t\tFAILURE");
            }
        }catch (Exception e){
            System.out.println("ecRecover:\t\t\tFAILURE WITH ERROR");
            e.printStackTrace();
        }

    }
}

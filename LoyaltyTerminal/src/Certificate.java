import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.*;

public class Certificate {
    private final String ID;
    private final String issuerName;
    private final String expiryDate;
    private final PublicKey publicKey;
    private final byte[] certificate;

    public Certificate (String ID, String issuerName, String expiryDate, PublicKey publicKey, PrivateKey privateKeyCA) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
        this.ID = ID;
        this.issuerName = issuerName;
        this.expiryDate = expiryDate;
        this.publicKey = publicKey;

        //Create cipher to generate signature of CA
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE,privateKeyCA);

        //Create signature of CA
        String info = ID + issuerName + expiryDate + publicKey;
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(infoBytes);
        byte[] signature = encryptCipher.doFinal(hash);

        //Combine info and signature into certificate
        certificate = new byte[infoBytes.length + signature.length];
        System.arraycopy(infoBytes, 0, certificate, 0, infoBytes.length);
        System.arraycopy(signature, 0, certificate, infoBytes.length, signature.length);
    }

    public byte[] getCertificate() {
        return certificate;
    }
}

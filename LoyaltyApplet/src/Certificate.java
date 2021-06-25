import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class Certificate {
    private final String ID;
    private final String issuerName;
    private final String expiryDate;
    private PublicKey publicKey = null;
    private final byte[] certificate;
    private final byte[] signature;

    public Certificate(String ID, String issuerName, String expiryDate, PublicKey publicKey, PrivateKey privateKeyCA) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
        this.ID = ID;
        this.issuerName = issuerName;
        this.expiryDate = expiryDate;
        this.publicKey = publicKey;

        //Create cipher to generate signature of CA
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE,privateKeyCA);

        //Create signature of CA
        byte[] IDBytes = ID.getBytes(StandardCharsets.UTF_8);
        byte[] IDLength = ByteBuffer.allocate(8).putInt(IDBytes.length).array();
        byte[] issuerBytes = issuerName.getBytes(StandardCharsets.UTF_8);
        byte[] issuerLength = ByteBuffer.allocate(8).putInt(issuerBytes.length).array();
        byte[] expiryBytes = expiryDate.getBytes(StandardCharsets.UTF_8);
        byte[] expiryLength = ByteBuffer.allocate(8).putInt(expiryBytes.length).array();
        byte[] pubKeyBytes = publicKey.getEncoded();
        byte[] pubKeyLength = ByteBuffer.allocate(8).putInt(pubKeyBytes.length).array();
        int lengthMessage = 8 + IDBytes.length + 8 + issuerBytes.length + 8 + expiryBytes.length + 8 + pubKeyBytes.length;

        //Combine info into one array
        byte[] send = new byte[lengthMessage];
        System.arraycopy(IDLength, 0, send, 0, 8);
        System.arraycopy(IDBytes, 0, send, 8, IDBytes.length);
        System.arraycopy(issuerLength, 0, send, 8 + IDBytes.length, 8);
        System.arraycopy(issuerBytes, 0, send, 8 + IDBytes.length + 8, issuerBytes.length);
        System.arraycopy(expiryLength, 0, send, 8 + IDBytes.length + 8 + issuerBytes.length, 8);
        System.arraycopy(expiryBytes, 0, send, 8 + IDBytes.length + 8 + issuerBytes.length + 8, expiryBytes.length);
        System.arraycopy(pubKeyLength, 0, send, 8 + IDBytes.length + 8 + issuerBytes.length + 8 + expiryBytes.length, 8);
        System.arraycopy(pubKeyBytes, 0, send, 8 + IDBytes.length + 8 + issuerBytes.length + 8 + expiryBytes.length + 8, pubKeyBytes.length);

        String info = ID + issuerName + expiryDate + publicKey;
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(infoBytes);
        signature = encryptCipher.doFinal(hash);
        byte[] signatureLength = ByteBuffer.allocate(8).putInt(signature.length).array();

        //Combine info and signature into certificate
        certificate = new byte[send.length + 8 + signature.length];
        System.arraycopy(send, 0, certificate, 0, send.length);
        System.arraycopy(signatureLength, 0, certificate, send.length, 8);
        System.arraycopy(signature, 0, certificate, send.length + 8, signature.length);
    }

    public Certificate(byte[] certificate){
        this.certificate = certificate;
        //Get information out of incoming
        int IDLength = ByteBuffer.wrap(Arrays.copyOfRange(certificate, 0, 8)).getInt();
        byte[] IDBytes = Arrays.copyOfRange(certificate, 8, 8 + IDLength);
        ID = new String(IDBytes, StandardCharsets.UTF_8);
        int issuerLength = ByteBuffer.wrap(Arrays.copyOfRange(certificate, 8 + IDLength, 8 + IDLength + 8)).getInt();
        byte[] issuerBytes = Arrays.copyOfRange(certificate, 8 + IDLength + 8, 8 + IDLength + 8 + issuerLength);
        issuerName = new String(issuerBytes, StandardCharsets.UTF_8);
        int expiryLength = ByteBuffer.wrap(Arrays.copyOfRange(certificate, 8 + IDLength + 8 + issuerLength, 8 + IDLength + 8 + issuerLength + 8)).getInt();
        byte[] expiryBytes = Arrays.copyOfRange(certificate, 8 + IDLength + 8 + issuerLength + 8, 8 + IDLength + 8 + issuerLength + 8 + expiryLength);
        expiryDate = new String(expiryBytes, StandardCharsets.UTF_8);
        int pubKeyLength =  ByteBuffer.wrap(Arrays.copyOfRange(certificate, 8 + IDLength + 8 + issuerLength + 8 + expiryLength, 8 + IDLength + 8 + issuerLength + 8 + expiryLength + 8)).getInt();
        byte[] pubKeyBytes = Arrays.copyOfRange(certificate, 8 + IDLength + 8 + issuerLength + 8 + expiryLength + 8, 8 + IDLength + 8 + issuerLength + 8 + expiryLength + 8 + pubKeyLength);
        int signatureLength =  ByteBuffer.wrap(Arrays.copyOfRange(certificate, 8 + IDLength + 8 + issuerLength + 8 + expiryLength + pubKeyLength, 8 + IDLength + 8 + issuerLength + 8 + expiryLength + 8 + pubKeyLength + 8)).getInt();
        signature = Arrays.copyOfRange(certificate, 8 + IDLength + 8 + issuerLength + 8 + expiryLength + 8 + pubKeyLength + 8, 8 + IDLength + 8 + issuerLength + 8 + expiryLength + 8 + pubKeyLength + 8 + signatureLength);

        //Use a KeyFactory to regenerate the keys from the byte arrays
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyBytes);
            publicKey = kf.generatePublic(pubKeySpec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] getCertificate() {
        return certificate;
    }

    public String getID() { return ID; }

    public String getExpiryDate() { return expiryDate; }

    public String getIssuerName() { return issuerName; }

    public byte[] getSignature() { return signature; }

    public PublicKey getPublickKey() { return publicKey; }
}

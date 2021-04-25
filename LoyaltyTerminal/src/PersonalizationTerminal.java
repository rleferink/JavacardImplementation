public class PersonalizationTerminal {
    public static void main(String[] args) {
        System.out.println("Hello World!");

        String originalString = "EncryptionTest";

        String encryptedString = AES256.encrypt(originalString);
        String decryptedString = AES256.decrypt(encryptedString);

        System.out.println(originalString);
        System.out.println(encryptedString);
        System.out.println(decryptedString);
    }
}

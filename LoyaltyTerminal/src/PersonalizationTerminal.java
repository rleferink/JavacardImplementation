import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import javacard.framework.AID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.smartcardio.*;
import javax.swing.*;
import javax.xml.crypto.Data;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Random;


public class PersonalizationTerminal extends JPanel implements ActionListener {
    JTextField display;
    JPanel keypad;
    static final Dimension PREFERRED_SIZE = new Dimension(400, 300);
    static final Point PREFERRED_LOCATION = new Point(460, 0);
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);
    static final int DISPLAY_WIDTH = 20;

    static final String MSG_ERROR = "    -- error --     ";
    static final String MSG_DISABLED = " -- insert card --  ";
    static final String MSG_INVALID = " -- invalid card -- ";

    static final byte[] CALC_APPLET_AID = { (byte) 0x3B, (byte) 0x29,
            (byte) 0x63, (byte) 0x61, (byte) 0x6C, (byte) 0x63, (byte) 0x01 };
    static final String CALC_APPLET_AID_string = "3B2963616C6301";

    static final CommandAPDU SELECT_APDU = new CommandAPDU(
            (byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, CALC_APPLET_AID);

    Card card;
    CardChannel applet;
    CardSimulator simulator;
    CardTerminal terminal2;
    Database database;

    KeyPair pairCA;

    public PersonalizationTerminal(CardTerminal personalization_terminal, CardSimulator simulator, Database database, KeyPair pairCA){
        System.out.println("Start Personalization");

        JFrame ptFrame = new JFrame("Personalisation Terminal");
        ptFrame.setPreferredSize(PREFERRED_SIZE);
        ptFrame.setLocation(PREFERRED_LOCATION);
        Container c = ptFrame.getContentPane();
        c.add(this);
        ptFrame.setResizable(false);
        ptFrame.pack();
        ptFrame.setVisible(true);

        this.pairCA = pairCA;

        buildGUI(ptFrame);
        setEnabled(false, card);

        terminal2 = personalization_terminal;
        this.simulator = simulator;
        this.database = database;
    }


    public void setEnabled(boolean b, Card card) {
        super.setEnabled(b);
        if (b) {
            setText("0");
        } else {
            setText("MSG_DISABLED");
        }
        Component[] keys = keypad.getComponents();
        for (int i = 0; i < keys.length; i++) {
            keys[i].setEnabled(b);
        }
        this.card = card;
        if (card != null){
            applet = card.getBasicChannel();
        }
    }

    void setText(String txt) {
        display.setText(txt);
    }

    public static void main(String[] args) {
        //To test encryption
        /*String originalString = "EncryptionTest";
        String encryptedString = AES256.encrypt(originalString);
        String decryptedString = AES256.decrypt(encryptedString);
        System.out.println(originalString);
        System.out.println(encryptedString);
        System.out.println(decryptedString);
        */
    }

    void buildGUI(JFrame parent) {
        setLayout(new BorderLayout());
        display = new JTextField(DISPLAY_WIDTH);
        display.setHorizontalAlignment(JTextField.RIGHT);
        display.setEditable(false);
        display.setFont(FONT);
        display.setBackground(Color.darkGray);
        display.setForeground(Color.green);
        add(display, BorderLayout.NORTH);
        keypad = new JPanel(new GridLayout(1, 1));
        key("OK", Color.green);
        add(keypad, BorderLayout.CENTER);
        parent.addWindowListener(new CloseEventListener());
    }

    void key(String txt, Color c) {
        if (txt == null) {
            keypad.add(new JLabel());
        } else {
            JButton button = new JButton(txt);
            button.setOpaque(true);
            button.setForeground(c);
            button.addActionListener(this);
            keypad.add(button);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            //generate key pair for card
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pairCard = generator.generateKeyPair();

            //create certificate for the card
            String cardID = "card1";
            Certificate certificateCard = new Certificate(cardID, "CA", "01-01-2022", pairCard.getPublic(), pairCA.getPrivate());

            //Create authentication code and store info in database
            String authenticationCode = "authCode1";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] authCodeHash = digest.digest(authenticationCode.getBytes(StandardCharsets.UTF_8));
            database.addCard(cardID, authCodeHash, certificateCard);

            //TODO send cardID, certificate and key pair to LoyaltyApplet and block personalization
            sendInfoToCard(cardID, certificateCard, pairCard);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
            ex.printStackTrace();
        }
    }

    void sendInfoToCard(String cardID, Certificate certificateCard, KeyPair keyPair){
        byte[] cardIDBytes = cardID.getBytes(StandardCharsets.UTF_8);
        byte[] IDLength = BigInteger.valueOf(cardIDBytes.length).toByteArray();
        byte[] certificateBytes = certificateCard.getCertificate();
        byte[] certLength = BigInteger.valueOf(certificateBytes.length).toByteArray();
        byte[] keyPairBytes = keyPair.toString().getBytes(StandardCharsets.UTF_8);
        byte[] pairLength = BigInteger.valueOf(keyPairBytes.length).toByteArray();
        int lengthMessage = 8 + cardIDBytes.length + 8 + certificateBytes.length + 8 + keyPairBytes.length;

        byte[] send = new byte[lengthMessage];
        System.arraycopy(IDLength, 0, send, 0, IDLength.length);
        System.arraycopy(cardIDBytes, 0, send, 8, cardIDBytes.length);
        System.arraycopy(certLength, 0, send, 8 + cardIDBytes.length, certLength.length);
        System.arraycopy(certificateBytes, 0, send, 8 + cardIDBytes.length + 8, certificateBytes.length);
        System.arraycopy(pairLength, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length, pairLength.length);
        System.arraycopy(keyPairBytes, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length + 8, keyPairBytes.length);

        CommandAPDU apdu_sendInfo = new CommandAPDU(0x00, AppUtil.AppMode.PERSONALIZE.mode, 0, 0, send);
        ResponseAPDU apdu_res = null;
        try {
            apdu_res = sendCommandAPDU(apdu_sendInfo);
        } catch (CardException e) {
            System.out.println((MSG_ERROR));
            e.printStackTrace();
        }



        /*byte[] send = new byte[counter.length + certificatePOS.length];
        System.arraycopy(counter, 0, send, 0, counter.length);
        System.arraycopy(certificatePOS, 0, send, counter.length, certificatePOS.length);
        CommandAPDU apdu_certificate = new CommandAPDU(0x00, state, AppUtil.AppComState.SEND_CERTIFICATE.mode, 0, send, 30);
        //step 6: receive certificate and counter = 1
        ResponseAPDU res_certificate = null;
        try {
            res_certificate = sendCommandAPDU(apdu_certificate);
        } catch (CardException e) {
            System.out.println((MSG_ERROR));
            e.printStackTrace();
        }
        received = res_certificate.getData();
        short buffer_size = (short) received.length;
        //TODO: Change to accommodate new certificate
        String certificate_card = new String(Arrays.copyOfRange(received,1,buffer_size-13));
        if(received[0] == counter[0] + 1){
            System.out.println("Counter CORRECT");
        }
        else{
            System.out.println("Counter INCORRECT");
            return null;
        }
        if(certificate_card.equals("certificate card")){
            System.out.println("Certificate card CORRECT");
        }
        else{
            System.out.println("certificate INCORRECT");
            return null;
        }
        return received;*/
    }

    ResponseAPDU sendCommandAPDU(CommandAPDU capdu) throws CardException {
        ResponseAPDU rapdu = applet.transmit(capdu);

        int sw = rapdu.getSW();
        if (sw != 0x9000) {
            setText(MSG_ERROR);
        }

        return rapdu;
    }

    class CloseEventListener extends WindowAdapter {
        public void windowClosing(WindowEvent we) {
            System.exit(0);
        }
    }
}

//Example code to encrypt
            /*
            String secretMessage = "1234";
            Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE,privateKeyCA);

            //create byte[] to store encrypted message
            byte[] secretMessageBytes = secretMessage.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedMessageBytes = encryptCipher.doFinal(secretMessageBytes);

            //create decrypt cipher
            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, publicKeyCA);

            //create byte[] to store decrypted message and then into a string
            byte[] decryptedMessageBytes = decryptCipher.doFinal(encryptedMessageBytes);
            String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

            //check if original message is equal to the decrypted message
            if(secretMessage.equals(decryptedMessage)){
                System.out.println("Loyalty Applet: messages are equal");
            };
             */

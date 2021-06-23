import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import javacard.framework.AID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.smartcardio.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.security.*;
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
        setEnabled(false);

        terminal2 = personalization_terminal;
        this.simulator = simulator;
        this.database = database;

        //TODO obtain keys from certificate

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
            //generate key pair for Card
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pairCard = generator.generateKeyPair();
            PrivateKey privateKeyCA = pairCA.getPrivate();
            PublicKey publicKeyCA = pairCA.getPublic();
            PrivateKey privateKeyCard = pairCard.getPrivate();
            PublicKey publicKeyCard = pairCard.getPublic();
            String cardID = "1";
            String issuerName = "CA";
            String expiryDate = "01-01-2022";
            Random authenticationCode = new Random();

            String secretMessage = cardID + issuerName + expiryDate + publicKeyCard;
            Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE,privateKeyCA);
            byte[] secretMessageBytes = secretMessage.getBytes(StandardCharsets.UTF_8);
            byte[] certificateCard = encryptCipher.doFinal(secretMessageBytes);
            //TODO certificate naar LoyaltyApplet
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
            ex.printStackTrace();
        }
    }

    class CloseEventListener extends WindowAdapter {
        public void windowClosing(WindowEvent we) {
            System.exit(0);
        }
    }
}

//set string which needs to be encrypted
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

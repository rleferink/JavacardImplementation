import com.licel.jcardsim.smartcardio.CardSimulator;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.smartcardio.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;

public class PersonalizationTerminal extends JPanel implements ActionListener {
    JTextField display;
    JPanel keypad;
    static final Dimension PREFERRED_SIZE = new Dimension(400, 300);
    static final Point PREFERRED_LOCATION = new Point(460, 0);
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);
    static final int DISPLAY_WIDTH = 20;

    static final String MSG_ERROR = "    -- error --     ";

    Card card;
    CardChannel applet;
    CardSimulator simulator;
    CardTerminal terminal2;
    Database database;

    KeyPair pairCA;

    public PersonalizationTerminal(CardTerminal personalization_terminal, CardSimulator simulator, Database database, KeyPair pairCA, Card card){
        JFrame ptFrame = new JFrame("Personalisation Terminal");
        ptFrame.setPreferredSize(PREFERRED_SIZE);
        ptFrame.setLocation(PREFERRED_LOCATION);
        Container c = ptFrame.getContentPane();
        c.add(this);
        ptFrame.setResizable(false);
        ptFrame.pack();
        ptFrame.setVisible(true);

        this.pairCA = pairCA;
        this.card = card;

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

    public static void main(String[] args) { }

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

            //Create random authentication code and store info in database
            byte[] auth = new byte[1];
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] authCodeHash = digest.digest(auth);
            database.addCard(cardID, authCodeHash, certificateCard);

            sendInfoToCard(cardID, certificateCard, pairCard, pairCA.getPublic());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
            ex.printStackTrace();
        }
    }

    void sendInfoToCard(String cardID, Certificate certificateCard, KeyPair keyPair, PublicKey publicKeyCA){
        //Extract keys from key pair
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        //Prepare info for sending
        byte[] cardIDBytes = cardID.getBytes(StandardCharsets.UTF_8);
        byte[] IDLength = ByteBuffer.allocate(8).putInt(cardIDBytes.length).array();
        byte[] certificateBytes = certificateCard.getCertificate();
        byte[] certLength = ByteBuffer.allocate(8).putInt(certificateBytes.length).array();
        byte[] pubKeyBytes = publicKey.getEncoded();
        byte[] pubKeyLength = ByteBuffer.allocate(8).putInt(pubKeyBytes.length).array();
        byte[] privKeyBytes = privateKey.getEncoded();
        byte[] privKeyLength = ByteBuffer.allocate(8).putInt(privKeyBytes.length).array();
        byte[] pubCABytes = publicKeyCA.getEncoded();
        byte[] pubCALength = ByteBuffer.allocate(8).putInt(pubCABytes.length).array();
        int lengthMessage = 8 + cardIDBytes.length + 8 + certificateBytes.length + 8 + pubKeyBytes.length + 8 + privKeyBytes.length + 8 + pubCABytes.length;

        //Combine info into one array
        byte[] send = new byte[lengthMessage];
        System.arraycopy(IDLength, 0, send, 0, IDLength.length);
        System.arraycopy(cardIDBytes, 0, send, 8, cardIDBytes.length);
        System.arraycopy(certLength, 0, send, 8 + cardIDBytes.length, certLength.length);
        System.arraycopy(certificateBytes, 0, send, 8 + cardIDBytes.length + 8, certificateBytes.length);
        System.arraycopy(pubKeyLength, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length, pubKeyLength.length);
        System.arraycopy(pubKeyBytes, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length + 8, pubKeyBytes.length);
        System.arraycopy(privKeyLength, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length + 8 + pubKeyBytes.length, privKeyLength.length);
        System.arraycopy(privKeyBytes, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length + 8 + pubKeyBytes.length + 8, privKeyBytes.length);
        System.arraycopy(pubCALength, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length + 8 + pubKeyBytes.length + 8 + privKeyBytes.length, pubCALength.length);
        System.arraycopy(pubCABytes, 0, send, 8 + cardIDBytes.length + 8 + certificateBytes.length + 8 + pubKeyBytes.length + 8 + privKeyBytes.length + 8, pubCABytes.length);

        //Cut combined info into smaller pieces for a commandAPDU
        ArrayList<byte[]> sendingChunks = new ArrayList<>();
        int i;
        for (i = 0; i < (lengthMessage / 250) * 250; i += 250){
            byte[] chunk = new byte[250];
            System.arraycopy(send, i, chunk, 0, 250);
            sendingChunks.add(chunk);
        }
        byte[] lastChunk = new byte[lengthMessage - i];
        System.arraycopy(send, i, lastChunk, 0, lengthMessage - i);
        sendingChunks.add(lastChunk);

        //Send the length of the info to the card
        byte[] sendLength = ByteBuffer.allocate(8).putInt(lengthMessage).array();
        CommandAPDU apdu_sendLength = new CommandAPDU(0x00, AppUtil.AppMode.PERSONALIZE.mode, AppUtil.AppComState.SEND_LENGTH.mode, 0, sendLength, 1);
        ResponseAPDU apdu_resLength = null;
        try {
            apdu_resLength = sendCommandAPDU(apdu_sendLength);
        } catch (CardException e) {
            System.out.println((MSG_ERROR));
            e.printStackTrace();
        }

        //Return if card is already personalized
        byte[] response = apdu_resLength.getData();
        if(response[0] == 0){
            setText("Card already personalized");
            return;
        }

        //Send the chunks to the card
        for (byte[] chunk:sendingChunks) {
            CommandAPDU apdu_sendInfo = new CommandAPDU(0x00, AppUtil.AppMode.PERSONALIZE.mode, AppUtil.AppComState.SEND_INFO.mode, 0, chunk, 1);
            ResponseAPDU apdu_resInfo = null;
            try {
                apdu_resInfo = sendCommandAPDU(apdu_sendInfo);
            } catch (CardException e) {
                System.out.println((MSG_ERROR));
                e.printStackTrace();
            }
        }

        //Send command to process the info sent to the card
        CommandAPDU apdu_processInfo = new CommandAPDU(0x00, AppUtil.AppMode.PERSONALIZE.mode, AppUtil.AppComState.PROCESS_INFO.mode, 0, new byte[0], 1);
        ResponseAPDU apdu_res = null;
        try {
            apdu_res = sendCommandAPDU(apdu_processInfo);
        } catch (CardException e) {
            System.out.println((MSG_ERROR));
            e.printStackTrace();
        }

        response = apdu_res.getData();
        if(response[0] == 0){
            setText("Card is already personalized");
        } else {
            setText("Personalization is done");
        }
    }

    ResponseAPDU sendCommandAPDU(CommandAPDU capdu) throws CardException {
        ResponseAPDU rapdu = applet.transmit(capdu);

        int sw = rapdu.getSW();
        if (sw != 0x9000) {
            setText(MSG_ERROR);
            System.out.println(sw);
        }
        return rapdu;
    }

    class CloseEventListener extends WindowAdapter {
        public void windowClosing(WindowEvent we) {
            System.exit(0);
        }
    }
}
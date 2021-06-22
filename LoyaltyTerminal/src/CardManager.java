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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class CardManager extends JPanel implements ActionListener {
    public static CardChannel applet;
    JPanel keypad;
    JTextField inputBox;
    JTextArea infoLabel;
    static final Dimension PREFERRED_SIZE = new Dimension(400, 120);
    static final Point PREFERRED_LOCATION = new Point(0, 380);
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);
    static final int DISPLAY_WIDTH = 20;

    static final byte[] CALC_APPLET_AID = { (byte) 0x3B, (byte) 0x29,
            (byte) 0x63, (byte) 0x61, (byte) 0x6C, (byte) 0x63, (byte) 0x01 };
    static final String CALC_APPLET_AID_string = "3B2963616C6301";
    static final CommandAPDU SELECT_APDU = new CommandAPDU(
            (byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, CALC_APPLET_AID);

    boolean active = false;

    static PosTerminal posTerminal;
    static PersonalizationTerminal personalizationTerminal;
    Card card;
    // Obtain a CardTerminal
    static CardTerminals cardTerminals = CardTerminalSimulator.terminals("POS Terminal", "Personalization Terminal");

    CardSimulator simulator;
    CardTerminal terminal1 = cardTerminals.getTerminal("POS Terminal");
    CardTerminal terminal2 = cardTerminals.getTerminal("Personalization Terminal");

    public CardManager(JFrame frame) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        buildGUI(frame);
        setEnabled(true);
        // Create simulator and install applet
        simulator = new CardSimulator();
        AID calcAppletAID = new AID(CALC_APPLET_AID,(byte)0,(byte)7);
        // @Andrius: This inserts a card
        simulator.installApplet(calcAppletAID, LoyaltyApplet.class);

        //Create generator for keypairs
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        //generate key pair for CA
        KeyPair pairCA = generator.generateKeyPair();
        PrivateKey privateKeyCA = pairCA.getPrivate();
        PublicKey publicKeyCA = pairCA.getPublic();

        //generate key pair and certificate for POS Terminal
        KeyPair pairPosTerminal = generator.generateKeyPair();
        PublicKey publicKeyPOS = pairPosTerminal.getPublic();
        Certificate c = new Certificate("POS1", "CA", "01-01-2022", publicKeyPOS);
        byte[] certificatePOS = c.getCertificate(privateKeyCA);

        //hieronder een frame maken om mee te geven aan de posTerminal
        JFrame posFrame = new JFrame();
        posTerminal = new PosTerminal(posFrame, publicKeyCA, pairPosTerminal, certificatePOS);
        personalizationTerminal = new PersonalizationTerminal(cardTerminals.getTerminal("Personalization Terminal"), pairCA);

        personalizationTerminal.revalidate();
        posTerminal.revalidate();
    }

    void buildGUI(JFrame parent) {
        setLayout(new BorderLayout());
        inputBox = new JTextField(DISPLAY_WIDTH);
        inputBox.setFont(FONT);
        inputBox.setBackground(Color.darkGray);
        inputBox.setForeground(Color.green);
        inputBox.setCaretColor(Color.green);
        add(inputBox, BorderLayout.NORTH);
        keypad = new JPanel(new GridLayout(1, 3));

        JTextArea label = new JTextArea("Insert card into");
        label.setEnabled(false);
        label.setMargin(new Insets(5,5,5,5));
        keypad.add(label);
        key("Personalize", Color.black);
        key("POS Terminal", Color.black);

        add(keypad, BorderLayout.CENTER);

        infoLabel = new JTextArea(("..."));
        infoLabel.setFont(new Font("Monospaced", Font.BOLD, 18));
        add(infoLabel, BorderLayout.SOUTH);
        add(inputBox, BorderLayout.NORTH);
        parent.addWindowListener(new CloseEventListener());
    }

    void key(String txt, Color c) {
        JButton button = new JButton(txt);
        button.setOpaque(true);
        button.setForeground(c);
        button.addActionListener(this);
        keypad.add(button);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Object src = e.getSource();
            if (src instanceof JButton) {
                String c = ((JButton) src).getText();

                if (c=="Personalize"){
                    // Insert Card into "Personalization Terminal"
                    simulator.assignToTerminal(terminal2);
                    if (active){
                        posTerminal.setEnabled(false);
                    }
                    card = terminal2.connect("*");
                    personalizationTerminal.setEnabled(true);
                }
                else if (c=="POS Terminal"){
                    // Insert Card into "POS terminal"
                    if (!active){
                        personalizationTerminal.setEnabled(false);
                    }
                    simulator.assignToTerminal(terminal1);
                    card = terminal1.connect("*");
                    posTerminal.setEnabled(true);
                }

                applet = card.getBasicChannel();
                ResponseAPDU resp = applet.transmit(SELECT_APDU);
                if (resp.getSW() != 0x9000) {
                    throw new Exception("Select failed");
                }
            }
        } catch (Exception ex) {
            System.err.println("Card status problem!");
        }
    }


    class CloseEventListener extends WindowAdapter {
        public void windowClosing(WindowEvent we) {
            System.exit(0);
        }
    }

    class SimulatedCardThread extends Thread {
        public void run() {

        }
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        JFrame frame = new JFrame("Card Manager");
        frame.setPreferredSize(PREFERRED_SIZE);
        frame.setLocation(PREFERRED_LOCATION);
        Container c = frame.getContentPane();
        CardManager panel = new CardManager(frame);
        c.add(panel);
        frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);
    }
}
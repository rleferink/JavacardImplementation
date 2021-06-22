import javacard.framework.AID;
import javacard.framework.ISO7816;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Enumeration;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.swing.*;

import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import com.licel.jcardsim.smartcardio.CardSimulator;
import javacard.framework.Applet;

import java.math.BigInteger;
/**
 * POS terminal for the Loyalty Applet.
 **
 * @author Roland Leferink
 * @author Marc van de Werfhorst
 * @author Romy Stähli
 *
 */
public class PosTerminal extends JPanel implements ActionListener {

    static PersonalizationTerminal pt;
    static CardManager cm;
    //private JavaxSmartCardInterface simulatorInterface; // SIM
    private AppUtil.AppMode appMode = AppUtil.AppMode.ADD;

    private static final long serialVersionUID = 1L;
    static final String TITLE = "Loyalty Applet";
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);
    static final Dimension PREFERRED_SIZE = new Dimension(400, 300);

    static final int DISPLAY_WIDTH = 20;
    static final String MSG_ERROR = "    -- error --     ";
    static final String MSG_DISABLED = " -- insert card --  ";
    static final String MSG_INVALID = " -- invalid card -- ";

    static final byte[] CALC_APPLET_AID = { (byte) 0x3B, (byte) 0x29,
            (byte) 0x63, (byte) 0x61, (byte) 0x6C, (byte) 0x63, (byte) 0x01 };
    static final String CALC_APPLET_AID_string = "3B2963616C6301";

    static final CommandAPDU SELECT_APDU = new CommandAPDU(
            (byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, CALC_APPLET_AID);

    String POSTerminal = "certificate POSTerminal";
    int terminalId = 19;

    //create an array consisting of timestamp, card number, terminalID, amount of points.
    //terminal keeps track of the most recent 100 transactions
    int lastTransactionIndex = 0;
    Byte [][] transactions = new Byte[100][4];

    private short enteredValue=0;

    JTextField display;
    JPanel keypad;

    CardChannel applet;

    static byte[] certificatePOS;
    static PublicKey publicKeyCA;
    static KeyPair pairPosTerminal;

    public PosTerminal(JFrame parent, PublicKey publicKeyCA, KeyPair pairPosTerminal, byte[] certificatePOS) {
        JFrame posFrame = new JFrame(TITLE);
        Container c = posFrame.getContentPane();
        c.add(this);
        posFrame.setResizable(false);
        posFrame.pack();
        posFrame.setVisible(true);

        this.certificatePOS = certificatePOS;
        this.publicKeyCA = publicKeyCA;
        this.pairPosTerminal = pairPosTerminal;

        buildGUI(parent);
        setEnabled(false);
        //(new SimulatedCardThread()).start();
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
        keypad = new JPanel(new GridLayout(4, 5));
        key("1", Color.black);
        key("2", Color.black);
        key("3", Color.black);
        key("X", Color.red);
        checkBox("Add", true);
        key("4", Color.black);
        key("5", Color.black);
        key("6", Color.black);
        key("<", Color.orange);
        checkBox("Spend", false);
        key("7", Color.black);
        key("8", Color.black);
        key("9", Color.black);
        key("OK", Color.green);
        checkBox("View", false);
        key(null, null);
        key("0", Color.black);
        key(null, null);
        key(null, null);
        key(null, null);
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

    ButtonGroup rbModeGroup = new ButtonGroup();

    void checkBox(String txt, boolean enable) {
        JRadioButton rb = new JRadioButton(txt, enable);
        rbModeGroup.add(rb);
        keypad.add(rb);
        rb.addActionListener(e -> {
            enteredValue = 0;
            switch (e.getActionCommand()){
                case "Add":
                    appMode = AppUtil.AppMode.ADD;
                    break;
                case "Spend":
                    appMode = AppUtil.AppMode.SPEND;
                    break;
                case "View":
                    appMode = AppUtil.AppMode.VIEW;
                    break;
            }
            setText(enteredValue);
        });
    }

    String getText() {
        return display.getText();
    }

    void setText(String txt) {
        try {
            if (appMode == AppUtil.AppMode.ADD) {
                txt = String.format("ADD  : %20s", txt);
            } else if (appMode == AppUtil.AppMode.SPEND) {
                txt = String.format("SPEND: %20s", txt);
            } else {
                txt = String.format("VIEW : %20s", txt);
            }
        } catch (NumberFormatException nfe) {
            txt = String.format("MSG  : %20s", txt);
        }
        display.setText(txt);
    }

    void setText(int n) {
        setText(Integer.toString(n));
    }

    public void setEnabled(boolean b) {
        super.setEnabled(b);
        if (b) {
            setText(0);
        } else {
            setText(MSG_DISABLED);
        }
        Component[] keys = keypad.getComponents();
        for (int i = 0; i < keys.length; i++) {
            keys[i].setEnabled(b);
        }
    }

    /* Connect the terminal with a simulated smartcard JCardSim
     */
    /*class SimulatedCardThread extends Thread {
        public void run() {
            // Obtain a CardTerminal
            CardTerminals cardTerminals = CardTerminalSimulator.terminals("My terminal 1");
            CardTerminal terminal1 = cardTerminals.getTerminal("My terminal 1");

            // Create simulator and install applet
            CardSimulator simulator = new CardSimulator();
            AID calcAppletAID = new AID(CALC_APPLET_AID,(byte)0,(byte)7);
            // @Andrius: This inserts a card
            simulator.installApplet(calcAppletAID, LoyaltyApplet.class);

            // Insert Card into "My terminal 1"
            simulator.assignToTerminal(terminal1);

            try {
                Card card = terminal1.connect("*");

                applet = card.getBasicChannel();
                ResponseAPDU resp = applet.transmit(SELECT_APDU);
                if (resp.getSW() != 0x9000) {
                    throw new Exception("Select failed");
                }
                setEnabled(true);
            } catch (Exception e) {
                System.err.println("Card status problem!");
            }
        }
    }*/

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Object src = e.getSource();
            if (src instanceof JButton) {
                String c = ((JButton) src).getText();
                if (c=="OK"){
                    byte[] received_data = {};
                    byte[] counter = getBytes(BigInteger.valueOf(1));
                    short amount = 0;
                    switch (appMode){
                        case ADD:
                            //step 3-6 certificates
                            received_data = sendAndCheckCertificate(counter, received_data, AppUtil.AppMode.ADD.mode);

                            //step 12 get amount
                            amount = enteredValue;
                            System.out.println("Amount entered: " + amount);

                            //step 13 increase balance on the card
                            received_data = changeBalance(counter, received_data,amount, AppUtil.AppMode.ADD.mode, transactions);

                            //step 14 store transaction
                            //TODO: Storing the transaction if we want to do it
                            //store_transaction(cardId, timestamp, terminalID, amount)

                            setText("Remove card");
                            break;
                        case SPEND:
                            System.out.println("Terminal: spending points");

                            //step 3: amount to spend is entered
                            amount = enteredValue;
                            System.out.println("Amount entered: " + amount);

                            //step 4: send certificate
                            received_data = sendAndCheckCertificate(counter, received_data, AppUtil.AppMode.SPEND.mode);

                            //step 12: any revoked transactions in the database?
                            //TODO: !!!!!!!

                            //step 13: is balance >= n? | counter +=1
                            received_data = changeBalance(counter, received_data,amount, AppUtil.AppMode.SPEND.mode, transactions);

                            //step 16: store transaction?
                            //TODO if we still think this is useful
                            //store_transaction(cardId, timestamp, terminalID, amount)

                            setText("Remove card");
                            System.out.println("End spending protocol");
                            break;
                        case VIEW:
                            byte[] empty_data = {(byte) 0};
                            CommandAPDU apdu_view = new CommandAPDU(0x00, AppUtil.AppMode.VIEW.mode, 0, 0, empty_data, 1);
                            ResponseAPDU res_view = null;
                            try {
                                res_view = sendCommandAPDU(apdu_view);
                            } catch (CardException ex) {
                                System.out.println((MSG_ERROR));
                                ex.printStackTrace();
                            }
                            received_data = res_view.getData();
                            setText((short) received_data[0]);
                            break;
                    }
                }
                else if (c=="X"){  // Reset environment
                    for (Enumeration<AbstractButton> buttons = rbModeGroup.getElements(); buttons.hasMoreElements();) {
                        enteredValue=0;
                        AbstractButton button = buttons.nextElement();
                        if (button.getText()=="Add"){
                            button.setSelected(true);
                        }
                        else {
                            button.setSelected(false);
                        }
                    }
                    setText(enteredValue);
                }
                else if (c=="<"){ // Remove one digit
                    enteredValue = (short) (enteredValue/10);
                    setText(enteredValue);
                }
                else { // Print digits
                    enteredValue = (short)(enteredValue * 10 + Integer.parseInt(c));
                    setText(enteredValue);
                }

            }
        } catch (Exception ex) {
            System.out.println(MSG_ERROR);
            System.out.println(ex);
        }
    }

    byte[] changeBalance(byte[] counter, byte[] received, short amount, byte state, Byte[][] transactions){
        counter[0] = (byte) (received[0] + 1);
        System.out.println("T -> C: balance >= amount? | " + (counter[0]));
        byte[] amountArray = {(byte) amount};
        byte[] terminalNr = {(byte) terminalId};
        byte[] balance_check = new byte[counter.length + terminalNr.length + amountArray.length];
        System.arraycopy(counter, 0, balance_check, 0, counter.length);
        System.arraycopy(terminalNr, 0, balance_check, counter.length, terminalNr.length);
        System.arraycopy(amountArray, 0, balance_check, counter.length + terminalNr.length, amountArray.length);
        CommandAPDU apdu_amount_check = new CommandAPDU(0x00, state, AppUtil.AppComState.SEND_AMOUNT_CHECK.mode, 0, balance_check, 3);

        ResponseAPDU res_amount_check = null;
        try {
            res_amount_check = sendCommandAPDU(apdu_amount_check);
        } catch (CardException e) {
            System.out.println((MSG_ERROR));
            e.printStackTrace();
        }

        received = res_amount_check.getData();
        //received[0] = counter
        //received[1] = cardId
        //received [2] = yes/no

        if(received[0] == counter[0] + 1){
            System.out.println("Counter CORRECT");
        }
        else{
            System.out.println("Counter INCORRECT");
            return null;
        }

        int cardId = received[1];

        if(received[2] == (byte)1){
            System.out.println("Balance high enough");
        }
        else if(state == AppUtil.AppMode.SPEND.mode){
            System.out.println("Balance NOT high enough");
            return null;
        }

        //store transaction
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        //TODO store timestamp in transaction
        //transactions[lastTransactionIndex][0] = timestamp.toString().getBytes();
        transactions[lastTransactionIndex][0] = (byte)0;
        transactions[lastTransactionIndex][1] = (byte)cardId;
        transactions[lastTransactionIndex][2] = (byte)terminalId;
        transactions[lastTransactionIndex][3] = (byte)amount;
        System.out.println("Transaction " + lastTransactionIndex + ": " + transactions[lastTransactionIndex][0] + " " + transactions[lastTransactionIndex][1] + " " + transactions[lastTransactionIndex][2] + " " + transactions[lastTransactionIndex][3]);
        lastTransactionIndex+=1;

        return received;
    }

    byte[] sendAndCheckCertificate(byte[] counter, byte[] received, byte state){
        byte[] send = new byte[counter.length + certificatePOS.length];
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
        return received;
    }

    ResponseAPDU sendCommandAPDU(CommandAPDU capdu) throws CardException {
        ResponseAPDU rapdu = applet.transmit(capdu);

        byte[] data = rapdu.getData();
        int sw = rapdu.getSW();
        if (sw != 0x9000) {
            setText(MSG_ERROR);
        } else if (data.length > 4) {
            setText((short) (((data[1] & 0x000000FF) << 32) | ((data[2] & 0x000000FF) << 16) |
                    ((data[3] & 0x000000FF) << 8) | (data[4] & 0x000000FF)));
        }
        return rapdu;
    }

    void log(ResponseAPDU obj){
        //display.append(obj.toString() + ", Data="+ toHexString(obj.getData()) + "\n");
        System.out.println(obj.toString() + ", Data=" + toHexString(obj.getData()));
    }

    void log(CommandAPDU obj){
        //display.append(obj.toString() + ", Data="+ toHexString(obj.getData()) + "\n");
        System.out.println(obj.toString() + ", Data=" + toHexString(obj.getData()));
    }

    byte[] getBytes(BigInteger big) {
        byte[] data = big.toByteArray();
        if (data[0] == 0) {
            byte[] tmp = data;
            data = new byte[tmp.length - 1];
            System.arraycopy(tmp, 1, data, 0, tmp.length - 1);
        }
        return data;
    }

    String toHexString(byte[] in) {
        //System.out.println("len: " + in.length);
        StringBuilder out = new StringBuilder(2*in.length);
        for(int i = 0; i < in.length; i++) {
            out.append(String.format("%02x ", (in[i] & 0xFF)));
        }
        return out.toString().toUpperCase();
    }

    class CloseEventListener extends WindowAdapter {
        public void windowClosing(WindowEvent we) {
            System.exit(0);
        }
    }

    public ResponseAPDU sendKey(byte P1) {
        CommandAPDU apdu = new CommandAPDU(0, (byte)appMode.toString().toCharArray()[0], P1, 0, 5);
        try {
            return applet.transmit(apdu);
        } catch (CardException e) {
            return null;
        }
    }

    public Dimension getPreferredSize() {
        return PREFERRED_SIZE;
    }

    public void main(String[] args) {
        JFrame frame = new JFrame(TITLE);
        Container c = frame.getContentPane();
        PosTerminal panel = new PosTerminal(frame, publicKeyCA, this.pairPosTerminal, this.certificatePOS);
        c.add(panel);
        frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);


    }
}


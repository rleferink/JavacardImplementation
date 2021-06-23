import java.util.*;

public class Database {
    private ArrayList<cardInfo> cards;

    public Database(){
        cards = new ArrayList<>();
    }

    public void addCard(String cardID, byte[] authCode, Certificate certificate){
        cards.add(new cardInfo(cardID, authCode, certificate));
    }

    class cardInfo{
        private String cardID;
        private byte[] authCodeHash;
        private Certificate certificate;
        private boolean active;

        public cardInfo(String cardID, byte[] authCode, Certificate certificate){
            this.cardID = cardID;
            this.authCodeHash = authCode;
            this.certificate = certificate;
            active = true;
        }
    }
}

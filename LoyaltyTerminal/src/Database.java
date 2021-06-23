import java.util.*;

public class Database {
    private ArrayList<cardInfo> cards;

    public Database(){
        cards = new ArrayList<>();
    }

    public void addCard(String cardID, String authCode, Certificate certificate){
        cards.add(new cardInfo(cardID, authCode, certificate));
    }

    class cardInfo{
        private String cardID;
        private String authCode;
        private Certificate certificate;
        private boolean active;

        public cardInfo(String cardID, String authCode, Certificate certificate){
            this.cardID = cardID;
            this.authCode = authCode;
            this.certificate = certificate;
            active = true;
        }
    }
}

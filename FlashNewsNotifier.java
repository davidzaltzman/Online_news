import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
 
public class FlashNewsNotifier {

    private static final String URL = "https://www.kore.co.il/flashNews";
    private static final String SENT_IDS_FILE = "sent_ids.txt";

    // עכשיו הערכים מתקבלים מ-Environment Variables (למשל מ-GitHub Secrets)
    private static final String FROM_EMAIL = System.getenv("EMAIL_FROM");
    private static final String APP_PASSWORD = System.getenv("EMAIL_PASSWORD");
    private static final String TO_EMAIL = System.getenv("EMAIL_TO");

    public static void main(String[] args) {
        try {
            LinkedHashSet<String> sentIds = loadSentIds();

            Document doc = Jsoup.connect(URL).get();
            Elements h3Elements = doc.select("h3");

            List<String> newMessages = new ArrayList<>();
            for (var element : h3Elements) {
                if (!element.tagName().equals("h3")) continue;
                String text = element.text().trim();
                if (text.isEmpty()) continue;

                String id = sha256Hex(text);
                if (!sentIds.contains(id)) {
                    newMessages.add(text);
                    sentIds.add(id);
                }
            }

            if (!newMessages.isEmpty()) {
                sendEmail(newMessages);
                saveSentIds(sentIds, newMessages.size()); // עכשיו מעדכנים ל- 300 ולא ל- 120
                System.out.println("נשלחו " + newMessages.size() + " הודעות חדשות.");
            } else {
                System.out.println("אין הודעות חדשות לשלוח.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static LinkedHashSet<String> loadSentIds() throws IOException {
        File file = new File(SENT_IDS_FILE);
        if (!file.exists()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Files.readAllLines(file.toPath()));
    }

    private static void saveSentIds(LinkedHashSet<String> ids, int newMessagesCount) throws IOException {
        List<String> idList = new ArrayList<>(ids);
        int excess = idList.size() - 300;
        if (excess > 0) {
            // מסירים בדיוק כפי שנכנס – כלומר מסירים הראשונים שנכנסו
            idList = idList.subList(excess, idList.size()); 
        }
        Files.write(Paths.get(SENT_IDS_FILE), idList);
    }

    private static void sendEmail(List<String> messages) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(FROM_EMAIL, APP_PASSWORD);
                    }
                });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(FROM_EMAIL));  
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL));  
        message.setSubject("🎯 הידד! 😄 יש הודעות חדשות מאתר כל רגע");

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Arial; direction: rtl;'>");
        // html.append("<h2 style='color: #0B5394;'>📢 התחדשנו בהודעות חדשות מהאתר!</h2>");

        for (String msg : messages) {
            html.append("<div style='border: 1px solid #FFA500; background-color: #FFF3E0; ")
                    .append("padding: 15px; margin-bottom: 15px; border-radius: 8px; font-size: 16px;'>")
                    .append(msg)
                    .append("</div>");
        }

        html.append("<hr style='margin-top:30px;'>");
        // html.append("<div style='color: #888; font-size: 12px;'>הודעה זו נשלחה אוטומטית מתוך FlashNewsNotifier</div>");
        html.append("</body></html>");

        message.setContent(html.toString(), "text/html; charset=UTF-8");
        Transport.send(message);
    }

    private static String sha256Hex(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8)); 
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0'); 
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

public class FlashNewsNotifier {

    private static final String URL = "https://www.kore.co.il/flashNews";
    private static final String LAST_FILE = "last.txt";
    private static final int MAX_STORED_IDS = 5000;

    private static final String FROM_EMAIL = System.getenv("EMAIL_FROM");
    private static final String PASSWORD   = System.getenv("EMAIL_PASSWORD");
    private static final String TO_EMAIL   = System.getenv("EMAIL_TO");

    public static void main(String[] args) {
        try {
            Set<String> previousIds = readPreviousIds();

            Document doc = Jsoup.connect(URL).get();
            Elements h3Elements = doc.select("h3");

            Map<String, String> messageIdMap = new LinkedHashMap<>();
            for (Element element : h3Elements) {
                // שולח רק h3 שאין לו אלמנטים פנימיים
                if (!element.children().isEmpty()) continue;

                String text = normalizeText(element.text());
                if (text.isEmpty()) continue;

                String id = sha256(text);
                messageIdMap.put(text, id);
            }

            List<String> newMessages = new ArrayList<>();
            for (String msg : messageIdMap.keySet()) {
                if (!previousIds.contains(messageIdMap.get(msg))) {
                    newMessages.add(msg);
                }
            }

            if (!newMessages.isEmpty()) {
                sendEmail(newMessages);

                // עדכון הקובץ אחרי השליחה
                for (String id : messageIdMap.values()) {
                    previousIds.add(id); // LinkedHashSet שומר סדר הוספה
                }

                writeLatestIds(previousIds);

                System.out.println("✅ נשלחו " + newMessages.size() + " הודעות חדשות.");
            } else {
                System.out.println("ℹ️ אין הודעות חדשות לשלוח.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ===================== לוגיקת זיהוי ===================== */

    private static Set<String> readPreviousIds() {
        try {
            return new LinkedHashSet<>(Files.readAllLines(Path.of(LAST_FILE), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new LinkedHashSet<>();
        }
    }

    private static void writeLatestIds(Set<String> allIds) throws IOException {
        // שמירה של ה־MAX_STORED_IDS האחרונים לפי סדר
        List<String> idsList = new ArrayList<>(allIds);
        int start = Math.max(0, idsList.size() - MAX_STORED_IDS);
        List<String> trimmed = idsList.subList(start, idsList.size());

        Files.write(
                Path.of(LAST_FILE),
                trimmed,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String normalizeText(String text) {
        // נירמול Unicode והסרת רווחים מיותרים
        return Normalizer.normalize(text, Normalizer.Form.NFKC).trim();
    }

    /* ===================== שליחת מייל ===================== */

    private static void sendEmail(List<String> messages) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_EMAIL, PASSWORD);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(FROM_EMAIL));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL));
        message.setSubject("🔔 הודעות חדשות מאתר כל רגע");

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Arial; direction: rtl;'>");

        for (String msg : messages) {
            html.append("<div style='border:1px solid #FFA500; background:#FFF3E0; ")
                .append("padding:15px; margin-bottom:15px; border-radius:8px;'>")
                .append(msg)
                .append("</div>");
        }

        html.append("</body></html>");

        message.setContent(html.toString(), "text/html; charset=UTF-8");
        Transport.send(message);
    }
}

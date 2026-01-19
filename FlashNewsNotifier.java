import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
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
            List<String> previousIds = readPreviousIds();

            Document doc = Jsoup.connect(URL).get();
            Elements h3Elements = doc.select("h3");

            List<String> allMessages = new ArrayList<>();
            Map<String, String> messageIdMap = new LinkedHashMap<>();

            for (var element : h3Elements) {
                String text = element.text().trim();
                if (text.isEmpty()) continue;

                String id = sha256(text);
                allMessages.add(text);
                messageIdMap.put(text, id);
            }

            List<String> newMessages = new ArrayList<>();
            for (String msg : allMessages) {
                String id = messageIdMap.get(msg);
                if (!previousIds.contains(id)) {
                    newMessages.add(msg);
                }
            }

            if (!newMessages.isEmpty()) {
                sendEmail(newMessages);
                writeLatestIds(previousIds, newMessages, messageIdMap);
                System.out.println("✅ נשלחו " + newMessages.size() + " הודעות חדשות.");
            } else {
                System.out.println("ℹ️ אין הודעות חדשות לשלוח.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ===================== לוגיקת זיהוי ===================== */

    private static List<String> readPreviousIds() {
        try {
            return Files.readAllLines(Path.of(LAST_FILE));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static void writeLatestIds(
            List<String> existingIds,
            List<String> newMessages,
            Map<String, String> idMap
    ) throws IOException {

        List<String> combined = new ArrayList<>(existingIds);

        for (String msg : newMessages) {
            String id = idMap.get(msg);
            if (!combined.contains(id)) {
                combined.add(id);
            }
        }

        int start = Math.max(0, combined.size() - MAX_STORED_IDS);
        List<String> trimmed = combined.subList(start, combined.size());

        Files.write(
                Path.of(LAST_FILE),
                trimmed,
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
        message.setSubject("📬 הודעות חדשות מאתר כל רגע");

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

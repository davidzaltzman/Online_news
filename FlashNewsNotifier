import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FlashNewsNotifier {

    private static final String URL = "https://www.kore.co.il/flashNews";
    private static final String SENT_IDS_FILE = "sent_ids.txt";

    // 🔧 עדכן כאן את כתובת האימייל והסיסמה שלך (App Password בלבד אם אתה משתמש בג'ימייל!)
    private static final String FROM_EMAIL = "davidr202120@gmail.com";
    private static final String APP_PASSWORD = "fqgg ioxb pdsm qhgk";
    private static final String TO_EMAIL = "dr0533137306@gmail.com, rivka202120@gmail.com";

    public static void main(String[] args) {
        try {
            LinkedHashSet<String> sentIds = loadSentIds(); // שמירה על סדר הכנסה

            Document doc = Jsoup.connect(URL).get();
            Elements h3Elements = doc.select("h3");

            List<String> newMessages = new ArrayList<>();
            for (var element : h3Elements) {
                String text = element.text().trim();
                if (!text.isEmpty() && !sentIds.contains(text)) {
                    newMessages.add(text);
                    sentIds.add(text);
                }
            }

            if (!newMessages.isEmpty()) {
                sendEmail(newMessages);
                saveSentIds(sentIds);
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

    private static void saveSentIds(Set<String> ids) throws IOException {
        // שמירה על 70 האחרונות בלבד
        List<String> trimmed = new ArrayList<>(ids);
        if (trimmed.size() > 70) {
            trimmed = trimmed.subList(trimmed.size() - 70, trimmed.size());
        }
        Files.write(Paths.get(SENT_IDS_FILE), trimmed);
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
        message.setSubject("🎯 הידד! 😄 יש הודעה חדשה מאתר כל רגע");

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Arial; direction: rtl;'>");
        html.append("<h2 style='color: #0B5394;'>📢 התחדשנו בהודעות חדשות מהאתר!</h2>");

        for (String msg : messages) {
            html.append("<div style='border: 1px solid #FFA500; background-color: #FFF3E0; ")
                    .append("padding: 15px; margin-bottom: 15px; border-radius: 8px; font-size: 16px;'>")
                    .append(msg)
                    .append("</div>");
        }

        html.append("<hr style='margin-top:30px;'>");
        html.append("<div style='color: #888; font-size: 12px;'>הודעה זו נשלחה אוטומטית מתוך FlashNewsNotifier</div>");
        html.append("</body></html>");

        message.setContent(html.toString(), "text/html; charset=UTF-8");
        Transport.send(message);
    }
}

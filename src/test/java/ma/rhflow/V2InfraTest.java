package ma.rhflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import ma.rhflow.service.MailService;
import ma.rhflow.util.XlsxFiles;

class V2InfraTest {

    @Test
    void xlsxStartsWithZipSignature() {
        byte[] bytes = XlsxFiles.table("Effectif", new String[] {"Nom"}, List.<String[]>of(new String[] {"Amrani"}));
        assertTrue(bytes.length > 4);
        assertEquals('P', (char) bytes[0]);
        assertEquals('K', (char) bytes[1]);
    }

    @Test
    void mailHtmlEscapesAndAddsButton() {
        String html = MailService.html("TechNova", "Bonjour <b>\nligne", "http://localhost:4200/portal", "Voir");
        assertTrue(html.contains("TechNova"));
        assertTrue(html.contains("&lt;b&gt;"));
        assertTrue(html.contains("http://localhost:4200/portal"));
        assertFalse(html.contains("<b>"));
    }

    @Test
    void mailDoesNotThrowWhenSmtpIsOffOrDown() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailService off = new MailService(sender, null, false, "rhflow@localhost", "http://localhost:4200");
        assertDoesNotThrow(() -> off.send("a@b.c", "Sujet", "Texte", "/portal", "Ouvrir"));
        verifyNoInteractions(sender);

        MailService on = new MailService(sender, null, true, "rhflow@localhost", "http://localhost:4200");
        assertDoesNotThrow(() -> on.send("a@b.c", "Sujet", "Texte"));
    }
}

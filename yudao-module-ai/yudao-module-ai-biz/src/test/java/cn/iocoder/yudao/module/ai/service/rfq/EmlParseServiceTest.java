package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EmlParseServiceTest {

    @Test
    void parseShouldKeepOriginalBytesForUnsupportedAttachment() throws Exception {
        byte[] attachmentContent = new byte[]{1, 2, 3, 4};
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("buyer@example.com"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("sales@example.com"));
        message.setSubject("RFQ with drawing", StandardCharsets.UTF_8.name());

        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("Please quote this part.", StandardCharsets.UTF_8.name());
        multipart.addBodyPart(textPart);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDisposition(Part.ATTACHMENT);
        attachmentPart.setFileName("drawing.png");
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(attachmentContent, "image/png")));
        multipart.addBodyPart(attachmentPart);
        message.setContent(multipart);
        message.saveChanges();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        EmailDTO emailDTO = new EmlParseService().parse(EmailRawDTO.builder()
                .messageId("<message-1@example.com>")
                .rawMime(outputStream.toString(StandardCharsets.ISO_8859_1))
                .build());

        assertEquals("Please quote this part.", emailDTO.getBodyText());
        assertEquals(1, emailDTO.getAttachments().size());
        EmailDTO.Attachment attachment = emailDTO.getAttachments().get(0);
        assertEquals("drawing.png", attachment.getFileName());
        assertEquals(4L, attachment.getSize());
        assertArrayEquals(attachmentContent, attachment.getContent());
        assertNotNull(attachment.getText());
    }

}

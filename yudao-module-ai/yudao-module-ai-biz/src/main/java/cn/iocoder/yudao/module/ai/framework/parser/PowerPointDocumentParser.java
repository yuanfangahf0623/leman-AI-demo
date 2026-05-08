package cn.iocoder.yudao.module.ai.framework.parser;

import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.sl.usermodel.TextShape;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * PowerPoint 文档解析器，支持 ppt、pptx 和 pptm。
 */
@Component
public class PowerPointDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("ppt", "pptx", "pptm");

    @Override
    public boolean supports(String filename, String contentType) {
        return DocumentParseUtils.hasExtension(filename, EXTENSIONS)
                || DocumentParseUtils.contentTypeContains(contentType, "ms-powerpoint")
                || DocumentParseUtils.contentTypeContains(contentType, "presentationml");
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (SlideShow slideShow = SlideShowFactory.create(new ByteArrayInputStream(bytes))) {
                StringBuilder builder = new StringBuilder();
                for (Object slideObject : slideShow.getSlides()) {
                    Slide slide = (Slide) slideObject;
                    for (Object shapeObject : slide.getShapes()) {
                        Shape shape = (Shape) shapeObject;
                        if (shape instanceof TextShape textShape) {
                            String text = textShape.getText();
                            if (text != null && !text.isBlank()) {
                                builder.append(text.trim()).append('\n');
                            }
                        }
                    }
                }
                String content = builder.toString();
                Map<String, Object> metadata = DocumentParseUtils.baseMetadata(context, getClass().getSimpleName(),
                        DocumentParseUtils.extension(context.getFilename()));
                metadata.put("slideCount", slideShow.getSlides().size());
                metadata.put("charCount", content.length());
                metadata.put("lineCount", DocumentParseUtils.countLines(content));
                return new ParsedDocument(DocumentParseUtils.titleFromFilename(context.getFilename(),
                        "PowerPoint Document"), content, metadata);
            }
        } catch (Exception ex) {
            throw new DocumentParseException("PowerPoint 文档解析失败", ex);
        }
    }

}

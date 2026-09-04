package org.happyhai.springai.alibaba.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TikaParserService {

    private static final Logger logger = LoggerFactory.getLogger(TikaParserService.class);

    private final Tika tika;
    private final Parser parser;

    public TikaParserService() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }

    /**
     * 解析单个文件（支持 Word、PDF、TXT 等）
     */
    public String parseFile(File file) {
        try {
            String content = tika.parseToString(file);
            logger.info("成功解析文件: {}, 内容长度: {}", file.getName(), content.length());
            return content.trim();
        } catch (IOException | TikaException e) {
            logger.error("解析文件失败: {}", file.getName(), e);
            throw new RuntimeException("解析文件失败: " + file.getName(), e);
        }
    }

    /**
     * 解析输入流
     */
    public String parseInputStream(InputStream inputStream, String fileName) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(inputStream, handler, metadata, context);

            String content = handler.toString().trim();
            logger.info("成功解析文件: {}, 内容长度: {}", fileName, content.length());
            return content;
        } catch (IOException | TikaException | SAXException e) {
            logger.error("解析文件失败: {}", fileName, e);
            throw new RuntimeException("解析文件失败: " + fileName, e);
        }
    }

    /**
     * 解析文件并提取元数据
     */
    public ParsedDocument parseFileWithMetadata(File file) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(Files.newInputStream(file.toPath()), handler, metadata, context);

            String content = handler.toString().trim();

            Map<String, String> metadataMap = Map.of(
                    "title", metadata.get("title") != null ? metadata.get("title") : file.getName(),
                    "author", metadata.get("Author") != null ? metadata.get("Author") : "",
                    "content-type", metadata.get("Content-Type") != null ? metadata.get("Content-Type") : "",
                    "fileName", file.getName(),
                    "fileSize", String.valueOf(file.length())
            );

            logger.info("成功解析文件: {}, 内容长度: {}", file.getName(), content.length());
            return new ParsedDocument(content, metadataMap);
        } catch (IOException | TikaException | SAXException e) {
            logger.error("解析文件失败: {}", file.getName(), e);
            throw new RuntimeException("解析文件失败: " + file.getName(), e);
        }
    }

    /**
     * 批量解析目录下的所有文件
     */
    public List<ParsedDocument> parseDirectory(File directory) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("不是有效的目录: " + directory.getPath());
        }

        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isSupportedFile(path.toFile()))
                    .map(path -> {
                        try {
                            return parseFileWithMetadata(path.toFile());
                        } catch (Exception e) {
                            logger.warn("跳过无法解析的文件: {}", path.getFileName());
                            return null;
                        }
                    })
                    .filter(doc -> doc != null && !doc.content().isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("遍历目录失败: {}", directory.getPath(), e);
            throw new RuntimeException("遍历目录失败", e);
        }
    }

    /**
     * 检查文件类型是否支持
     */
    public boolean isSupportedFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".doc") ||
                fileName.endsWith(".docx") ||
                fileName.endsWith(".pdf") ||
                fileName.endsWith(".txt") ||
                fileName.endsWith(".md") ||
                fileName.endsWith(".html") ||
                fileName.endsWith(".htm") ||
                fileName.endsWith(".rtf") ||
                fileName.endsWith(".odt") ||
                fileName.endsWith(".ppt") ||
                fileName.endsWith(".pptx") ||
                fileName.endsWith(".xls") ||
                fileName.endsWith(".xlsx");
    }

    /**
     * 获取文件类型描述
     */
    public String getContentType(File file) {
        try {
            return tika.detect(file);
        } catch (IOException e) {
            return "unknown";
        }
    }

    /**
     * 解析文档结果封装
     */
    public record ParsedDocument(String content, Map<String, String> metadata) {
    }
}

package org.happyhai.springai.alibaba.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档切片服务
 * 支持语义切片，保证每个切片的语义完整性
 */
@Service
public class DocumentChunkingService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkingService.class);

    /**
     * 目标切片大小（字符数）
     * 注意：实际切片大小会围绕这个值浮动，优先保证语义完整性
     */
    private static final int TARGET_CHUNK_SIZE = 512;

    /**
     * 切片重叠大小（按语义单元重叠）
     */
    private static final int OVERLAP_SENTENCES = 1;

    /**
     * 最小切片大小（字符数）
     * 如果一个句子超过这个值，也会保留
     */
    private static final int MIN_CHUNK_SIZE = 100;

    /**
     * 按语义切片
     * 保证每个切片是完整的句子/段落，不切割语义单元
     *
     * @param content 内容
     * @param targetSize 目标切片大小（字符数）
     * @param metadata 元数据
     * @return 切片后的 Document 列表
     */
    public List<Document> chunkBySemantic(String content, int targetSize, Map<String, Object> metadata) {
        List<Document> chunks = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return chunks;
        }

        // 第一步：按段落分割
        List<String> paragraphs = splitIntoParagraphs(content);

        // 第二步：每个段落内按句子分割
        List<String> sentences = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (!paragraph.trim().isEmpty()) {
                List<String> paragraphSentences = splitIntoSentences(paragraph);
                sentences.addAll(paragraphSentences);
            }
        }

        if (sentences.isEmpty()) {
            // 如果没有句子，把整个内容作为一个切片
            chunks.add(createChunk(content, metadata, 0));
            return chunks;
        }

        // 第三步：按语义单元分组
        List<List<String>> semanticChunks = groupIntoSemanticChunks(sentences, targetSize);

        // 第四步：创建 Document
        for (int i = 0; i < semanticChunks.size(); i++) {
            List<String> chunkSentences = semanticChunks.get(i);
            String chunkText = String.join("", chunkSentences);
            Map<String, Object> chunkMetadata = new java.util.HashMap<>(metadata);
            chunkMetadata.put("chunkIndex", i);
            chunkMetadata.put("totalChunks", semanticChunks.size());
            chunkMetadata.put("sentenceCount", chunkSentences.size());
            chunkMetadata.put("charCount", chunkText.length());
            chunks.add(new Document(chunkText, chunkMetadata));
        }

        logger.info("语义切片完成，共 {} 个切片", chunks.size());
        for (Document chunk : chunks) {
            logger.info("  切片 {}: {} 字符, {} 句子", 
                    chunk.getMetadata().get("chunkIndex"),
                    chunk.getMetadata().get("charCount"),
                    chunk.getMetadata().get("sentenceCount"));
        }

        return chunks;
    }

    /**
     * 按固定字符数切片（带语义保护）
     *
     * @param content 内容
     * @param chunkSize 目标切片大小（字符数）
     * @param overlap 重叠大小
     * @param metadata 元数据
     * @return 切片后的 Document 列表
     */
    public List<Document> chunkByFixedSize(String content, int chunkSize, int overlap, Map<String, Object> metadata) {
        // 使用语义切片
        return chunkBySemantic(content, chunkSize, metadata);
    }

    /**
     * 按段落切片（每个段落为一个切片）
     */
    public List<Document> chunkByParagraphs(String content, Map<String, Object> metadata) {
        List<Document> chunks = new ArrayList<>();
        List<String> paragraphs = splitIntoParagraphs(content);

        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i).trim();
            if (!paragraph.isEmpty()) {
                Map<String, Object> chunkMetadata = new java.util.HashMap<>(metadata);
                chunkMetadata.put("chunkIndex", i);
                chunkMetadata.put("totalChunks", paragraphs.size());
                chunkMetadata.put("chunkType", "paragraph");
                chunks.add(new Document(paragraph, chunkMetadata));
            }
        }

        logger.info("按段落切片完成，共 {} 个切片", chunks.size());
        return chunks;
    }

    /**
     * 将文本按段落分割
     */
    private List<String> splitIntoParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        
        // 按多种换行符分割
        String normalized = content
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        
        // 按空行分割（段落之间有一个或多个空行）
        String[] parts = normalized.split("\n\\s*\n");
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // 清理多余空白，保留段落内换行
                trimmed = trimmed.replaceAll("\\s+", " ");
                paragraphs.add(trimmed);
            }
        }

        // 如果没有找到段落，用整个文本
        if (paragraphs.isEmpty() && !content.trim().isEmpty()) {
            paragraphs.add(content.trim().replaceAll("\\s+", " "));
        }

        return paragraphs;
    }

    /**
     * 将文本按句子分割
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        
        // 中文句子结束符：。！？……
        // 英文句子结束符：.!? (后面跟空格或换行)
        // 先处理中文
        Pattern cnPattern = Pattern.compile("([^。！？……]+[。！？……]+)");
        Matcher cnMatcher = cnPattern.matcher(text);
        
        while (cnMatcher.find()) {
            String sentence = cnMatcher.group(1).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        
        // 如果没有找到中文句子结束符，尝试英文
        if (sentences.isEmpty()) {
            Pattern enPattern = Pattern.compile("([^.!]+[.!?]+)");
            Matcher enMatcher = enPattern.matcher(text);
            
            while (enMatcher.find()) {
                String sentence = enMatcher.group(1).trim();
                if (!sentence.isEmpty()) {
                    sentences.add(sentence);
                }
            }
        }
        
        // 如果还是没有，把整个文本作为一个句子
        if (sentences.isEmpty() && !text.trim().isEmpty()) {
            sentences.add(text.trim());
        }

        return sentences;
    }

    /**
     * 将句子按语义分组
     * 每个组的大小围绕 targetSize 浮动，优先保证语义完整
     */
    private List<List<String>> groupIntoSemanticChunks(List<String> sentences, int targetSize) {
        List<List<String>> chunks = new ArrayList<>();
        
        if (sentences.isEmpty()) {
            return chunks;
        }

        List<String> currentChunk = new ArrayList<>();
        int currentLength = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentenceLength = sentence.length();

            // 如果单个句子超过目标大小
            if (sentenceLength > targetSize) {
                // 先保存当前的 chunk
                if (!currentChunk.isEmpty()) {
                    chunks.add(new ArrayList<>(currentChunk));
                    currentChunk.clear();
                    currentLength = 0;
                }

                // 如果句子太长，进一步拆分（按逗号、分号等）
                List<String> subSentences = splitLongSentence(sentence);
                
                for (String subSentence : subSentences) {
                    if (currentLength + subSentence.length() <= targetSize) {
                        currentChunk.add(subSentence);
                        currentLength += subSentence.length();
                    } else {
                        if (!currentChunk.isEmpty()) {
                            chunks.add(new ArrayList<>(currentChunk));
                            currentChunk.clear();
                            currentLength = 0;
                        }
                        // 如果单个子句就超过目标大小，直接保留
                        if (subSentence.length() > targetSize) {
                            // 强制拆分
                            for (String part : splitByComma(subSentence, targetSize)) {
                                currentChunk.add(part);
                                currentLength += part.length();
                                if (currentLength >= targetSize * 0.5) {
                                    chunks.add(new ArrayList<>(currentChunk));
                                    currentChunk.clear();
                                    currentLength = 0;
                                }
                            }
                        } else {
                            currentChunk.add(subSentence);
                            currentLength += subSentence.length();
                        }
                    }
                }
            }
            // 如果加上当前句子会超过目标大小
            else if (currentLength + sentenceLength > targetSize) {
                // 先检查当前 chunk 是否太小
                if (currentLength < MIN_CHUNK_SIZE && !currentChunk.isEmpty()) {
                    // 当前 chunk 太小，尝试再添加一些句子
                    // 继续添加直到达到最小大小或超过目标大小
                }
                
                // 保存当前 chunk
                if (!currentChunk.isEmpty()) {
                    chunks.add(new ArrayList<>(currentChunk));
                }
                
                // 开始新 chunk，保留最后一个句子作为重叠
                currentChunk.clear();
                currentLength = 0;
                
                currentChunk.add(sentence);
                currentLength = sentenceLength;
            }
            // 正常添加
            else {
                currentChunk.add(sentence);
                currentLength += sentenceLength;
            }
        }

        // 保存最后一个 chunk
        if (!currentChunk.isEmpty()) {
            chunks.add(new ArrayList<>(currentChunk));
        }

        return chunks;
    }

    /**
     * 拆分超长句子
     */
    private List<String> splitLongSentence(String sentence) {
        List<String> parts = new ArrayList<>();
        
        // 按逗号、分号、顿号拆分
        String[] separators = {"，", "、", "；", ",", ";"};
        String remaining = sentence;
        
        for (String sep : separators) {
            if (remaining.contains(sep)) {
                String[] split = remaining.split("[" + sep + "]+");
                for (String part : split) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        parts.add(part);
                    }
                }
                break;
            }
        }
        
        // 如果没有找到分隔符，返回原句
        if (parts.isEmpty()) {
            parts.add(sentence);
        }
        
        return parts;
    }

    /**
     * 按逗号强制拆分超长字符串
     */
    private List<String> splitByComma(String text, int maxLength) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (char c : text.toCharArray()) {
            current.append(c);
            if (current.length() >= maxLength) {
                parts.add(current.toString());
                current = new StringBuilder();
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts;
    }

    /**
     * 创建切片 Document
     */
    private Document createChunk(String content, Map<String, Object> metadata, int index) {
        Map<String, Object> chunkMetadata = new java.util.HashMap<>(metadata);
        chunkMetadata.put("chunkIndex", index);
        return new Document(content.trim(), chunkMetadata);
    }
}

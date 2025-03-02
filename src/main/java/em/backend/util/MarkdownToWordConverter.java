package em.backend.util;

import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.apache.poi.xwpf.usermodel.Borders;

import lombok.extern.slf4j.Slf4j;

/**
 * 工具类，用于将Markdown文本转换为Word文档
 */
@Slf4j
public class MarkdownToWordConverter {

    /**
     * 将Markdown文本转换为Word文档
     *
     * @param markdown Markdown文本
     * @param outputFileName 输出文件名（不含路径和扩展名）
     * @return 生成的Word文档文件
     * @throws IOException 如果文件操作失败
     */
    public static File convertToWord(String markdown, String outputFileName) throws IOException {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("markdown_to_word");
        
        // 处理文件名，替换非法字符
        String safeFileName = sanitizeFileName(outputFileName);
        File outputFile = new File(tempDir.toFile(), safeFileName + ".docx");
        
        // 创建Word文档
        XWPFDocument document = new XWPFDocument();
        
        // 解析Markdown
        Parser parser = Parser.builder().build();
        Node document_node = parser.parse(markdown);
        
        // 处理Markdown节点
        processNode(document, document_node);
        
        // 保存文档
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            document.write(out);
        }
        
        log.info("Markdown转Word成功，文件路径: {}", outputFile.getAbsolutePath());
        return outputFile;
    }
    
    /**
     * 清理文件名，移除或替换非法字符
     * 
     * @param fileName 原始文件名
     * @return 处理后的安全文件名
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "document";
        }
        
        // 替换Windows文件系统中的非法字符 (\ / : * ? " < > |)
        String safeFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // 如果文件名过长，截断它
        if (safeFileName.length() > 100) {
            safeFileName = safeFileName.substring(0, 100);
        }
        
        return safeFileName;
    }
    
    /**
     * 处理Markdown节点
     */
    private static void processNode(XWPFDocument document, Node node) {
        if (node instanceof org.commonmark.node.Document) {
            // 处理文档节点
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                processNode(document, child);
            }
        } else if (node instanceof Heading) {
            // 处理标题
            Heading heading = (Heading) node;
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("Heading" + heading.getLevel());
            XWPFRun run = paragraph.createRun();
            
            // 获取标题文本
            StringBuilder sb = new StringBuilder();
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                extractText(child, sb);
            }
            
            run.setText(sb.toString());
            run.setBold(true);
            
            // 根据标题级别设置字体大小
            switch (heading.getLevel()) {
                case 1:
                    run.setFontSize(18);
                    break;
                case 2:
                    run.setFontSize(16);
                    break;
                case 3:
                    run.setFontSize(14);
                    break;
                default:
                    run.setFontSize(12);
            }
        } else if (node instanceof Paragraph) {
            // 处理段落
            XWPFParagraph paragraph = document.createParagraph();
            
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                processInlineNode(paragraph, child);
            }
        } else if (node instanceof BulletList) {
            // 处理无序列表
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof ListItem) {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setNumID(getOrCreateNumbering(document, false));
                    
                    for (Node itemChild = child.getFirstChild(); itemChild != null; itemChild = itemChild.getNext()) {
                        if (itemChild instanceof Paragraph) {
                            for (Node inlineChild = itemChild.getFirstChild(); inlineChild != null; inlineChild = inlineChild.getNext()) {
                                processInlineNode(paragraph, inlineChild);
                            }
                        } else {
                            processNode(document, itemChild);
                        }
                    }
                }
            }
        } else if (node instanceof OrderedList) {
            // 处理有序列表
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof ListItem) {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setNumID(getOrCreateNumbering(document, true));
                    
                    for (Node itemChild = child.getFirstChild(); itemChild != null; itemChild = itemChild.getNext()) {
                        if (itemChild instanceof Paragraph) {
                            for (Node inlineChild = itemChild.getFirstChild(); inlineChild != null; inlineChild = inlineChild.getNext()) {
                                processInlineNode(paragraph, inlineChild);
                            }
                        } else {
                            processNode(document, itemChild);
                        }
                    }
                }
            }
        } else if (node instanceof BlockQuote) {
            // 处理引用块
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(720); // 缩进0.5英寸
            paragraph.setBorderLeft(Borders.SINGLE);
            
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Paragraph) {
                    for (Node inlineChild = child.getFirstChild(); inlineChild != null; inlineChild = inlineChild.getNext()) {
                        processInlineNode(paragraph, inlineChild);
                    }
                } else {
                    processNode(document, child);
                }
            }
        } else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
            // 处理代码块
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(720); // 缩进0.5英寸
            paragraph.setBorderLeft(Borders.SINGLE);
            paragraph.setBorderRight(Borders.SINGLE);
            paragraph.setBorderTop(Borders.SINGLE);
            paragraph.setBorderBottom(Borders.SINGLE);
            
            XWPFRun run = paragraph.createRun();
            String code = "";
            
            if (node instanceof FencedCodeBlock) {
                code = ((FencedCodeBlock) node).getLiteral();
            } else {
                code = ((IndentedCodeBlock) node).getLiteral();
            }
            
            run.setText(code);
            run.setFontFamily("Courier New");
        } else {
            // 处理其他节点
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                processNode(document, child);
            }
        }
    }
    
    /**
     * 处理内联节点
     */
    private static void processInlineNode(XWPFParagraph paragraph, Node node) {
        if (node instanceof Text) {
            XWPFRun run = paragraph.createRun();
            run.setText(((Text) node).getLiteral());
        } else if (node instanceof Emphasis) {
            // 斜体
            XWPFRun run = paragraph.createRun();
            StringBuilder sb = new StringBuilder();
            extractText(node, sb);
            run.setText(sb.toString());
            run.setItalic(true);
        } else if (node instanceof StrongEmphasis) {
            // 粗体
            XWPFRun run = paragraph.createRun();
            StringBuilder sb = new StringBuilder();
            extractText(node, sb);
            run.setText(sb.toString());
            run.setBold(true);
        } else if (node instanceof Link) {
            // 链接
            Link link = (Link) node;
            XWPFRun run = paragraph.createRun();
            StringBuilder sb = new StringBuilder();
            extractText(node, sb);
            run.setText(sb.toString());
            run.setColor("0000FF");
            run.setUnderline(UnderlinePatterns.SINGLE);
        } else if (node instanceof Code) {
            // 行内代码
            XWPFRun run = paragraph.createRun();
            run.setText(((Code) node).getLiteral());
            run.setFontFamily("Courier New");
        } else {
            // 处理其他内联节点
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                processInlineNode(paragraph, child);
            }
        }
    }
    
    /**
     * 提取节点中的文本
     */
    private static void extractText(Node node, StringBuilder sb) {
        if (node instanceof Text) {
            sb.append(((Text) node).getLiteral());
        } else {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                extractText(child, sb);
            }
        }
    }
    
    /**
     * 获取或创建编号
     */
    private static BigInteger getOrCreateNumbering(XWPFDocument document, boolean ordered) {
        try {
            // 简化实现，直接使用默认编号
            XWPFNumbering numbering = document.createNumbering();
            
            // 为简单起见，我们使用一个基本的编号方案
            return BigInteger.valueOf(1); // 使用默认编号
        } catch (Exception e) {
            log.error("创建编号失败", e);
            return BigInteger.valueOf(1); // 出错时返回默认值
        }
    }
} 
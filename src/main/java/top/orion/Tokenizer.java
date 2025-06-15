package top.orion;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 分词工具
 * @author Orion
 * @since 2025/6/16 0:43
 */
public class Tokenizer {

    private static final IKAnalyzer analyzer = new IKAnalyzer(true); // 使用最大切分模式

    /**
     * 分词并提取关键词
     * @param text 输入文本
     * @return 分词后的词列表
     */
    public static List<String> tokenize(String text) {
        List<String> words = new ArrayList<>();
        Analyzer analyzer = new IKAnalyzer(true); // 使用最大切分模式

        try (TokenStream ts = analyzer.tokenStream("dummyField", new StringReader(text))) {
            ts.reset(); // 必须调用 reset()

            CharTermAttribute term = ts.getAttribute(CharTermAttribute.class);

            while (ts.incrementToken()) {
                words.add(term.toString());
            }

            ts.end(); // 结束处理
        } catch (IOException e) {
            e.printStackTrace();
        }

        return words;
    }

}

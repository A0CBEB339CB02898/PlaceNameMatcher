package top.orion;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 地名匹配器
 * 支持正则预处理、Jaro-Winkler 相似度 和 TF-IDF + Cosine 相似度混合算法
 *
 * @author Orion
 * @since 2025/6/15 18:25
 */
public class PlaceNameMatcher {

    // 停用词集合
    private static final Set<String> STOPWORDS = new HashSet<>();

    static {
        loadStopWords();
        loadIDFMap();
    }
    static Map<String, Double> IDF_MAP = new HashMap<>();


    /**
     * 从文件加载 IDF_MAP
     */
    private static void loadIDFMap() {
        try (InputStream is = PlaceNameMatcher.class.getClassLoader().getResourceAsStream("src/main/resources/idf_map.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String word = parts[0].trim();
                    double idf = Double.parseDouble(parts[1].trim());
                    IDF_MAP.put(word, idf);
                }
            }

        } catch (Exception e) {
            System.err.println("无法加载 idf_map.txt，使用空 IDF_MAP 继续运行");
        }
    }

    /**
     * 从外部文件加载停用词
     */
    private static void loadStopWords() {
        try (
                InputStream is = PlaceNameMatcher.class.getClassLoader().getResourceAsStream("stopwords.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(is), StandardCharsets.UTF_8))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty()) {
                    STOPWORDS.add(word);
                }
            }
        } catch (Exception e) {
            System.err.println("无法加载 stopwords.txt，使用默认停用词表继续运行");
            // 默认回退
            Collections.addAll(STOPWORDS, "国家重点", "风景名胜区", "景区", "路", "街", "大道");
        }
    }

    // 权重配置（可外置为配置文件）
    private static double WEIGHT_JARO_WINKLER = 0.6;
    private static double WEIGHT_TFIDF_COSINE = 0.4;
    private static double THRESHOLD = 0.85;

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    /**
     * 对外暴露的统一匹配接口 默认阈值和权重下简单匹配
     * @param name1 地名1
     * @param name2 地名2
     * @return 是否为同一地点
     */
    public static boolean match(String name1, String name2) {
        PlaceNameMatcher matcher = new PlaceNameMatcher();
        return matcher.isSamePlace(name1, name2);
    }

    /**
     * 设置匹配阈值（方便动态调整）
     * @param threshold 新的匹配阈值
     */
    public static void setThreshold(double threshold) {
        THRESHOLD = threshold;
    }

    /**
     * 设置各算法权重（方便调优）
     * @param jwWeight JW 权重
     * @param cosineWeight Cosine 权重
     */
    public static void setWeights(double jwWeight, double cosineWeight) {
        WEIGHT_JARO_WINKLER = jwWeight;
        WEIGHT_TFIDF_COSINE = cosineWeight;
    }

    /**
     * 使用加权评分方式综合判断两个地名是否为同一地点
     * @param name1 地名1
     * @param name2 地名2
     * @return 是否为同一地点
     */
    public boolean isSamePlace(String name1, String name2) {
        // 1. 预处理：去除冗余词
        String cleanName1 = normalizePlaceName(name1);
        String cleanName2 = normalizePlaceName(name2);

        if (cleanName1.equals(cleanName2)) {
            return true;
        }

        // 2. 各项评分（0~1）
        double jwScore = jaroWinkler.apply(cleanName1, cleanName2); // 字符串相似度
        double cosineScore = computeTfIdfCosine(cleanName1, cleanName2); // 语义相似度

        // 3. 综合得分
        double totalScore = jwScore * WEIGHT_JARO_WINKLER + cosineScore * WEIGHT_TFIDF_COSINE;

        System.out.println("jwScore:"+jwScore);
        System.out.println("cosineScore:"+cosineScore);
        System.out.println("totalScore:"+totalScore);
        // 4. 判断是否超过阈值
        return totalScore > THRESHOLD;
    }

    /**
     * 去除地名中的冗余词
     * @param name 原始地名
     * @return 标准化后的地名
     */
    private String normalizePlaceName(String name) {
        for (String stopword : STOPWORDS) {
            name = name.replace(stopword, "");
        }
        return name.trim();
    }

    /**
     * 使用 HanLP 分词并提取关键词
     * @param text 输入文本
     * @return 分词后的词列表
     */
    private List<String> tokenize(String text) {
        List<Term> termList = HanLP.segment(text);
        return termList.stream()
                .map(term -> term.word)
                .filter(word -> !STOPWORDS.contains(word))
                .collect(Collectors.toList());
    }

    /**
     * 计算两个地名之间的 TF-IDF + Cosine 相似度
     * @param name1 地名1
     * @param name2 地名2
     * @return Cosine 相似度
     */
    private double computeTfIdfCosine(String name1, String name2) {
        List<String> tokens1 = tokenize(name1);
        List<String> tokens2 = tokenize(name2);

        Set<String> allWords = new HashSet<>();
        allWords.addAll(tokens1);
        allWords.addAll(tokens2);

        Map<String, Integer> tf1 = computeTermFrequency(tokens1);
        Map<String, Integer> tf2 = computeTermFrequency(tokens2);

        // 假设整个语料库有 10000 个文档（可调整）
        int totalDocs = 10000;

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String word : allWords) {
            int count1 = tf1.getOrDefault(word, 0);
            int count2 = tf2.getOrDefault(word, 0);

            // 简单 TF
            double tf1Val = Math.log(1 + count1);
            double tf2Val = Math.log(1 + count2);

            // IDF（假设每个词在 1~100 文档中出现过）
            double idf = Math.log((double) totalDocs / (1 + IDF_MAP.getOrDefault(word, 1.0)));

            // TF-IDF
            double tfidf1 = tf1Val * idf;
            double tfidf2 = tf2Val * idf;

            dotProduct += tfidf1 * tfidf2;
            norm1 += tfidf1 * tfidf1;
            norm2 += tfidf2 * tfidf2;
        }

        if (norm1 == 0 || norm2 == 0) return 0;

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }


    /**
     * 统计词频
     * @param tokens 分词结果
     * @return 词频映射
     */
    private Map<String, Integer> computeTermFrequency(List<String> tokens) {
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            freq.put(token, freq.getOrDefault(token, 0) + 1);
        }
        return freq;
    }
}

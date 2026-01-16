package top.orion;

import cn.hutool.extra.pinyin.PinyinUtil;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;


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
    private static final Map<String, Double> IDF_MAP = new HashMap<>();
    private static List<String> SORTED_STOPWORDS = Collections.emptyList(); // 已排序的停用词列
    // 权重配置（可外置为配置文件）
    private static double WEIGHT_JARO_WINKLER = 0.35;  // 原始字符匹配
    private static double WEIGHT_TFIDF_COSINE = 0.3; // 语义匹配

    private static double WEIGHT_PINYIN = 0.35;      // 拼音匹配
    private static double THRESHOLD = 0.85;          // 匹配阈值
    private static volatile PlaceNameMatcher instance;

    static {
        loadStopWords();
        loadIDFMap();
    }

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    private PlaceNameMatcher() {
        // 私有构造器
    }

    public static PlaceNameMatcher getInstance() {
        if (instance == null) {
            synchronized (PlaceNameMatcher.class) {
                if (instance == null) {
                    instance = new PlaceNameMatcher();
                }
            }
        }
        return instance;
    }


    /**
     * 从文件加载 IDF_MAP（使用 NIO 风格 + BufferedReader）
     */
    private static void loadIDFMap() {
        try {
            InputStream is = PlaceNameMatcher.class.getClassLoader().getResourceAsStream("idf_map.txt");
            if (is == null) {
                System.err.println("PlaceNameMatcher --- ⚠️ 警告：idf_map.txt 未找到，使用空 IDF_MAP 继续运行");
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String word = parts[0].trim();
                        double idf = Double.parseDouble(parts[1].trim());
                        IDF_MAP.put(word, idf);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("PlaceNameMatcher --- 加载 IDF_MAP 失败：" + e.getMessage());
        }
    }


    /**
     * 从外部文件加载停用词（使用 NIO 风格 + BufferedReader）
     */
    private static void loadStopWords() {
        try (InputStream is = PlaceNameMatcher.class.getClassLoader().getResourceAsStream("stopwords.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(is), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty()) {
                    STOPWORDS.add(word);
                }
            }

        } catch (Exception e) {
            System.err.println("PlaceNameMatcher --- 无法加载 stopwords.txt，使用默认停用词表继续运行");
            Collections.addAll(STOPWORDS, "国家重点", "风景名胜区", "景区", "路", "街", "大道");
        } finally {
            // 默认词也排序
            SORTED_STOPWORDS = new ArrayList<>(STOPWORDS);
            SORTED_STOPWORDS.sort((a, b) -> Integer.compare(b.length(), a.length()));
        }
    }


    /**
     * 对外暴露的统一匹配接口 默认阈值和权重下简单匹配
     *
     * @param name1 地名1
     * @param name2 地名2
     * @return 匹配重合度 [0-1]
     */
    public static boolean match(String name1, String name2) {
        return getInstance().isSamePlace(name1, name2) > THRESHOLD;
    }

    /**
     * 对外暴露的统一匹配接口 默认阈值和权重下简单匹配
     *
     * @param name1 地名1
     * @param name2 地名2
     * @return 匹配重合度 [0-1]
     */
    public static double matchDegree(String name1, String name2) {
        return getInstance().isSamePlace(name1, name2);
    }

    /**
     * 设置匹配阈值（方便动态调整）
     *
     * @param threshold 新的匹配阈值
     */
    public static void setThreshold(double threshold) {
        THRESHOLD = threshold;
    }

    /**
     * 设置各算法权重（方便调优）
     *
     * @param jwWeight     JW 权重
     * @param cosineWeight Cosine 权重
     * @param pinyinWeight 拼音权重
     */
    public static void setWeights(double jwWeight, double cosineWeight, double pinyinWeight) {
        WEIGHT_JARO_WINKLER = jwWeight;
        WEIGHT_TFIDF_COSINE = cosineWeight;
        WEIGHT_PINYIN = pinyinWeight;
    }

    /**
     * 去除字符串中的标点符号。（包含中英文）
     * 测试用例：!@#$%^&*()_+-=[]{}！@#￥%……&*（）——+「」【】
     *
     * @param input 原始字符串
     * @return 去除标点后的字符串
     */
    public static String removePunctuation(String input) {
        // 正则表达式，匹配所有的标点符号
        String regex = "[\\p{P}$^+=￥]";
        // 使用replaceAll方法替换所有匹配到的标点符号为空字符""
        return input.replaceAll(regex, "");
    }

    /**
     * 使用加权评分方式综合判断两个地名是否为同一地点
     *
     * @param name1 地名1
     * @param name2 地名2
     * @return 是否为同一地点
     */
    public double isSamePlace(String name1, String name2) {
        // 1. 预处理：去除冗余词
        String cleanName1 = normalizePlaceName(name1);
        String cleanName2 = normalizePlaceName(name2);

        // 如果一个为空，直接返回 false
        if (cleanName1.isEmpty() || cleanName2.isEmpty()) {
            return 0;
        }

        // 如果长度差异太大，也可以提前返回 false（可选）
        if (Math.abs(cleanName1.length() - cleanName2.length()) > 8) {
            return 0;
        }

        if (cleanName1.equals(cleanName2)) {
            return 1;
        }


        // 2. 各项评分（0~1）
        double jwScore = jaroWinkler.apply(cleanName1, cleanName2); // 字符串相似度
        double cosineScore = computeTfIdfCosine(cleanName1, cleanName2); // 语义相似度
        double pinyinScore = computePinyinSimilarity(cleanName1, cleanName2); // 拼音相似度


// 3. 综合得分
        double totalScore =
                jwScore * WEIGHT_JARO_WINKLER +
                        cosineScore * WEIGHT_TFIDF_COSINE +
                        pinyinScore * WEIGHT_PINYIN;

//        System.out.println("jwScore:" + jwScore);
//        System.out.println("cosineScore:" + cosineScore);
//        System.out.println("pinyinScore:" + pinyinScore);
//        System.out.println("totalScore:" + totalScore);
//        System.out.println("THRESHOLD:" + THRESHOLD);
//        System.out.println("WEIGHT_JARO_WINKLER:" + WEIGHT_JARO_WINKLER);
//        System.out.println("WEIGHT_TFIDF_COSINE:" + WEIGHT_TFIDF_COSINE);

        // 4. 判断是否超过阈值
        return totalScore;
    }

    /**
     * 去除地名中的冗余词
     *
     * @param name 原始地名
     * @return 标准化后的地名
     */
    public String normalizePlaceName(String name) {
        // 将繁体字转为简体字
        name = ZhConverterUtil.toSimple(name);

        for (String stopword : SORTED_STOPWORDS) {
            // 判断是否包含英文字符
            if (stopword.matches(".*[a-zA-Z]+.*")) {
                // 英文停用词：使用单词边界匹配
                name = name.replaceAll("\\b" + Pattern.quote(stopword) + "\\b", "");
            } else {
                // 中文停用词：直接替换
                name = name.replace(stopword, "");
            }
        }
        // 去除标点符号
        name = removePunctuation(name);
        return name.trim();
    }

    /**
     * 计算两个地名之间的 TF-IDF + Cosine 相似度
     *
     * @param name1 地名1
     * @param name2 地名2
     * @return Cosine 相似度
     */
    private double computeTfIdfCosine(String name1, String name2) {
        List<String> tokens1 = Tokenizer.tokenize(name1);
        List<String> tokens2 = Tokenizer.tokenize(name2);

        Set<String> allWords = new HashSet<>();
        allWords.addAll(tokens1);
        allWords.addAll(tokens2);

        Map<String, Integer> tf1 = computeTermFrequency(tokens1);
        Map<String, Integer> tf2 = computeTermFrequency(tokens2);

        // 只保留出现次数 >= 1 的词
        tf1.entrySet().removeIf(e -> e.getValue() < 1);
        tf2.entrySet().removeIf(e -> e.getValue() < 1);

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
     *
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


    /**
     * 使用 Hutool 将汉字转换为拼音字符串
     */
    private String toPinyin(String str) {
        return PinyinUtil.getPinyin(str);
    }

    /**
     * 计算两个地名拼音的 Jaro-Winkler 相似度
     */
    private double computePinyinSimilarity(String name1, String name2) {
        String pinyin1 = toPinyin(name1);
        String pinyin2 = toPinyin(name2);
        return jaroWinkler.apply(pinyin1, pinyin2);
    }

}

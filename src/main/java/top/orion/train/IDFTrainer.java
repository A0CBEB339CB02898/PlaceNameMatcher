package top.orion.train;

import com.hankcs.hanlp.HanLP;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Orion
 * @since 2025/6/16 0:17
 */
public class IDFTrainer {

    // 地名语料路径
    private static final String CORPUS_PATH = "src/main/resources/places_corpus.txt";

    public static void main(String[] args) {
        try {
            Map<String, Double> idfMap = trainIDF();
            idfMap.forEach((word, idf) -> System.out.printf("%s: %.2f%n", word, idf));
            exportIDFToFile(idfMap, "src/main/resources/idf_map.txt"); // 输出到文件
            System.out.println("IDF_MAP 已成功导出到 idf_map.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从地名语料中训练 IDF 值
     */
    public static Map<String, Double> trainIDF() throws IOException {
        List<String> documents = readAllLines(CORPUS_PATH);
        int totalDocs = documents.size();

        // 统计每个词在多少文档中出现过
        Map<String, Integer> docFrequency = new HashMap<>();

        for (String doc : documents) {
            List<String> words = tokenize(doc);
            Set<String> uniqueWords = new HashSet<>(words);

            for (String word : uniqueWords) {
                docFrequency.put(word, docFrequency.getOrDefault(word, 0) + 1);
            }
        }

        // 计算 IDF
        Map<String, Double> idfMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : docFrequency.entrySet()) {
            String word = entry.getKey();
            int df = entry.getValue();
            double idf = Math.log((double) totalDocs / df);
            idfMap.put(word, idf);
        }

        return idfMap;
    }

    /**
     * 分词处理
     */
    private static List<String> tokenize(String text) {
        return HanLP.segment(text).stream()
                .map(term -> term.word)
                .filter(word -> word.length() > 1) // 可选：过滤单字
                .collect(Collectors.toList());
    }

    /**
     * 读取所有行
     */
    private static List<String> readAllLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }

    /**
     * 将 IDF_MAP 导出为文件
     */
    public static void exportIDFToFile(Map<String, Double> idfMap, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), "UTF-8"))) {
            //加入排序 让输出结果更清晰
            List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(idfMap.entrySet());
            sortedEntries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
            for (Map.Entry<String, Double> entry : sortedEntries) {
                writer.write(entry.getKey() + "=" + String.format("%.4f", entry.getValue()));
                writer.newLine();
            }
        }
    }

}

# 地名匹配器（PlaceNameMatcher）

一个基于 Java 的地名模糊匹配工具，适用于地图服务中不同 API 返回的地名一致性判断。结合 Jaro-Winkler 字符串相似度与 TF-IDF + Cosine 向量空间模型，支持权重配置和阈值调整，具备良好的可扩展性和性能表现。

---

## 📌 项目简介

在多个地图 API（如高德、百度、腾讯）返回的地名存在格式差异时，本工具可以帮助判断两个地名是否指向同一地点。

### 示例场景：
- “岳麓山风景名胜区” vs “岳麓山国家重点风景名处”
- “长沙国金中心” vs “长沙IFS国金中心”
- “弹子石老街” vs “弹子石老街景区”

---

## 🧩 核心功能

| 功能 | 描述 |
|------|------|
| ✅ 混合评分算法 | 使用 Jaro-Winkler 和 TF-IDF Cosine 相似度加权打分 |
| ✅ 停用词过滤 | 支持自定义停用词列表，去除冗余词汇 |
| ✅ IDF 权重计算 | 支持从语料库训练 IDF 值并加载 |
| ✅ 热更新配置 | 可动态设置匹配阈值和各算法权重 |
| ✅ 单例模式优化 | 配置只加载一次，提升高频调用效率 |

---

## 📁 项目结构
src
└─ main
   ├─ resources
   │  ├─ idf_map.txt
   │  ├─ places_corpus.txt
   │  ├─ places_corpus_bak.txt
   │  └─ stopwords.txt
   └─ java
      └─ top
         └─ orion
            ├─ demo.java
            ├─ PlaceNameMatcher.java
            ├─ Tokenizer.java
            └─ train
               └─ IDFTrainer.java


---

## 🛠 技术栈

| 组件 | 描述 |
|------|------|
| `Java 8+` | 跨平台、高性能语言基础 |
| `IKAnalyzer` | 中文分词工具，支持最大切分 |
| `Apache Commons Text` | 提供 Jaro-Winkler 字符串相似度实现 |

---

## 🔍 匹配逻辑说明

1. **标准化处理**  
   - 使用 [stopwords.txt](https://github.com/A0CBEB339CB02898/PlaceNameMatcher/blob/master/src/main/resources/stopwords.txt)过滤无意义词汇
   - 如：“弹子石老街风景区” → “弹子石老街”

2. **字符串相似度计算（Jaro-Winkler）**
   - 快速判断两个地名的字符层面相似性
   - 默认权重：0.6

3. **TF-IDF + Cosine 相似度计算**
   - 对分词后的词语进行 TF-IDF 加权向量构建
   - 计算语义层面上的相似度
   - 默认权重：0.4

4. **综合得分公式**
   totalScore = jwScore * weightJW + cosineScore * weightCosine
   
5. **最终判定**
   - 若 `totalScore > THRESHOLD`，则认为是同一个地点
   - 默认阈值：0.85

---

## 🧪 使用示例

```java
// 判断两个地名是否一致
boolean result = PlaceNameMatcher.match("木青瓦院HOLIDAY VILLA", "木青瓦院假日别墅");
// 修改匹配阈值和权重
PlaceNameMatcher.setThreshold(0.9); PlaceNameMatcher.setWeights(0.7, 0.3);
```

更多使用方式请参考 [demo.java](https://github.com/A0CBEB339CB02898/PlaceNameMatcher/blob/master/src/main/java/top/orion/demo.java)

---

## 📦 停用词 & IDF 资源

### ✅ stopwords.txt
```txt
 国家重点
 风景名胜区
 新区
 地铁站
 酒店
 ...
```
### ✅ idf_map.txt (50w条地名数据训练)
```txt
广场=3.4350
酒店=2.2330
路=4.0168
...
```

> 💡 `idf_map.txt` 可通过运行 [IDFTrainer.java](https://github.com/A0CBEB339CB02898/PlaceNameMatcher/blob/master/src/main/java/top/orion/train/IDFTrainer.java) 自动生成。

---

## 📈 性能优化建议

| 优化点 | 描述 |
|--------|------|
| ✅ 缓存机制 | 加入 NORMALIZED_CACHE 和 TOKENIZED_CACHE，避免重复计算 |
| ✅ LRU 或 TTL 机制 | 控制缓存大小和生命周期 |
| ✅ 分词限制 | 设置最大 token 数防止向量膨胀 |
| ✅ 日志级别控制 | 使用 debug 输出调试信息，生产环境关闭 |
| ✅ 多线程安全 | 使用 ConcurrentHashMap 或 synchronized 控制并发 |

---

## 📂 资源加载说明

- 所有资源均通过 `ClassLoader.getResourceAsStream()` 加载，兼容打包后部署。
- 建议显式指定编码为 UTF-8，防止乱码问题。
- 如果文件缺失或为空，程序不会中断，但精度可能下降。

---

## 🧠 可扩展方向

| 方向 | 描述 |
|------|------|
| 🔄 引入 BERT 语义模型 | 提升长文本地名匹配准确率 |
| 🗺️ 地理编码辅助 | 结合经纬度信息提升判断依据 |
| 📈 Web 接口封装 | 封装为 RESTful API 供外部系统调用 |
| 📊 加入可视化面板 | 展示地名匹配结果与相似度分布 |

---

## 📝 贡献指南

欢迎贡献代码、优化分词策略、加入新算法、改进缓存机制等！

### 提交规范
- 使用简洁命名，描述清楚改动目的
- 添加单元测试验证核心函数行为
- 保持原有风格，不破坏已有接口

---



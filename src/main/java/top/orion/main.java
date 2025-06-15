package top.orion;

/**
 * @author Orion
 * @since 2025/6/15 18:59
 */
public class main {
    public static void main(String[] args) {
        //计算耗时
        long startTime = System.currentTimeMillis();
        String name1 = "推瓦·木青瓦院HOLIDAY VILLA(中山路地铁站店)";
        String name2 = "木青瓦院HOLIDAY VILLA(中山路地铁站店)";
        System.out.println(PlaceNameMatcher.match(name1, name2));
        long endTime = System.currentTimeMillis();
        System.out.println("耗时：" + (endTime - startTime) + "ms");
    }
}

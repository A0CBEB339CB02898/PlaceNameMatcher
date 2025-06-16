package top.orion;

/**
 * @author Orion
 * @since 2025/6/15 18:59
 */
public class demo {

    //test
    public static void main(String[] args) {

        String name1 = "HOLIDAY VILLA(中山路地铁站店)";
        String name2 = "木HOLIDAY VILLA(中山路地铁站店)";
        for (int i = 0; i < 10; i++) {
            //计算耗时
            long startTime = System.currentTimeMillis();
            System.out.println(PlaceNameMatcher.match(name1, name2));
            if (i == 4) {
                PlaceNameMatcher.setThreshold(0.5);
                PlaceNameMatcher.setWeights(0.5, 0.5, 0.5);
            }
            long endTime = System.currentTimeMillis();
            System.out.println("耗时：" + (endTime - startTime) + "ms");
        }


        PlaceNameMatcher.setThreshold(1);

    }

}

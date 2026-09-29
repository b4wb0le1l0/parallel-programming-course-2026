package practice;

import java.awt.desktop.SystemSleepEvent;

public class S01Exercise {

    public static long countEven (long[] values) {
        long count = 0;
        for (int i = 0; i< values.length; i++) {
            if (values[i] % 2 == 0) {
                count++;
            }
        }

        return count;
    }

    public static void main(String[] args) {
        long[] values = {3, 4, 9, 12};
        System.out.println("countEven=" + countEven(values));
    }
}

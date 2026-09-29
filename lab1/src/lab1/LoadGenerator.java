package lab1;

import java.util.Random;

public class LoadGenerator {
    public static final int SIZE = 1 << 20; // 1 048 576 чисел
    public static final long SEED = 42;

    public static long[] generate() {
        double[] weights = new double[1024];
        double totalWeight = 0;
        for (int k = 1; k < weights.length; k++) {
            weights[k] = 1.0 / Math.pow(k, 1.15);
            totalWeight += weights[k];
        }

        double[] boundaries = new double[1024];
        double accumulated = 0;
        for (int k = 1; k < weights.length; k++) {
            accumulated += weights[k] / totalWeight;
            boundaries[k] = accumulated;
        }
        boundaries[1023] = 1.0;

        Random random = new Random(SEED);
        long[] values = new long[SIZE];
        for (int i = 0; i < values.length; i++) {
            double point = random.nextDouble();
            for (int k = 1; k < boundaries.length; k++) {
                if (point < boundaries[k]) {
                    values[i] = k;
                    break;
                }
            }
        }
        return values;
    }

    public static void main(String[] args) {
        long[] values = generate();
        long firstBucket = 0;
        for (long value : values) {
            if (value < 4) firstBucket++;
        }
        System.out.println("Values: " + values.length);
        System.out.println("Seed: " + SEED);
        System.out.println("Bucket 0 ratio: " + (double) firstBucket / values.length);
        System.out.print("First 10: ");
        for (int i = 0; i < 10; i++) System.out.print(values[i] + " ");
        System.out.println();
    }
}

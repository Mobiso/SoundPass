import java.io.IOException;

/**
 * A simple test harness for the SoundRNG class.
 * Usage:
 *   javac SoundRNG.java SoundRNGTest.java
 *   java SoundRNGTest <entropyFile> <entropyPerInt> <bits> <numRandoms>
 * 
 * Example:
 *   java SoundRNGTest entropy.raw 32 16 10
 *   -> Reads 32 bits of entropy per integer, trims to 16 bits, and prints 10 random values.
 */
public class SoundRNGTest {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java SoundRNGTest <entropyFile> <entropyPerInt> <bits> <numRandoms>");
            System.exit(1);
        }

        String entropyFile = args[0];
        int entropyPerInt;
        int bits;
        int numRandoms;
        try {
            entropyPerInt = Integer.parseInt(args[1]);
            bits = Integer.parseInt(args[2]);
            numRandoms = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.err.println("Error: entropyPerInt, bits, and numRandoms must be integers.");
            return;
        }

        // Instantiate the RNG
        SoundRNG rng = new SoundRNG(entropyFile, entropyPerInt);

        System.out.println("Generating " + numRandoms + " random values (" + bits + " bits each):");
        for (int i = 0; i < numRandoms; i++) {
            int value = rng.nextInt(bits);
            System.out.printf("%d: %d%n", i + 1, value);
        }

        // Close the entropy source reader when done
        try {
            rng.entropy_source_reader.close();
        } catch (IOException e) {
            System.err.println("Warning: failed to close entropy source reader.");
        }
    }
}


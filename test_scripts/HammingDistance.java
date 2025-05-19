import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HammingDistance {

    // Hardcoded audio file set for comparison (4 pairs of user and adversary files)
    private static final String[] audioFiles = {
        "kth-quiet-room_USER_AUDIO_DATA.bin",      // Index 0: User Audio 1
        "KTH-quiet-room_ADVERSARY_AUDIO_DATA.bin", // Index 1: Adversary Audio 1
        "kth-corridor-constant-brus_USER_AUDIO_DATA.bin",      // Index 2: User Audio 2
        "KTH-corridor-constant-brus_ADVERSARY_AUDIO_DATA.bin", // Index 3: Adversary Audio 2
        "Gallerian_USER_AUDIO_DATA.bin",      // Index 4: User Audio 3
        "Gallerian_ADVERSARY_AUDIO_DATA.bin", // Index 5: Adversary Audio 3
        "parkbank_USER_AUDIO_DATA.bin",      // Index 6: User Audio 4
        "parkbank_ADVERSARY_AUDIO_DATA.bin"  // Index 7: Adversary Audio 4
    };

    private final static int chunks = 10;

    public static void main(String[] args) {
         if (args.length != 2 || !args[0].equals("--files")) {
            System.err.println("Usage: java HammingDistance --files <AudioFileSetIndex>");
            System.exit(1);
        }

        // Parse the index of the audio file set to compare
        int fileSetIndex = -1;
        try {
            fileSetIndex = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error: The index must be a valid integer.");
            System.exit(1);
        }

        // Ensure the index is within valid bounds (0-3 for the four file pairs)
        if (fileSetIndex < 0 || fileSetIndex > 3) {
            System.err.println("Error: Invalid index. Valid indices are 0, 1, 2, and 3.");
            System.exit(1);
        }

        // Select the appropriate audio files based on the index
        String userAudioFile = audioFiles[fileSetIndex * 2];
        String adversaryAudioFile = audioFiles[fileSetIndex * 2 + 1];

        System.out.println("Comparing: ");
        System.out.println("User Audio File: " + userAudioFile);
        System.out.println("Adversary Audio File: " + adversaryAudioFile);

        // Test conditions for lsbAmount (1-8) and bitsToExtract (4 to 128, powers of 2)
        for (int lsbAmount = 1; lsbAmount <= 8; lsbAmount++) {
            for (int bitsToExtract = 4; bitsToExtract <= 128; bitsToExtract *= 2) {
                System.out.println("Testing with lsbAmount = " + lsbAmount + " and bitsToExtract = " + bitsToExtract);

                try {
                    File userFile = new File(userAudioFile);
                    File adversaryFile = new File(adversaryAudioFile);
                    if (!userFile.exists() || !adversaryFile.exists()) {
                        System.err.println("Error: One or both audio files do not exist.");
                        System.exit(1);
                    }

                    AudioRNG userAudioRNG = new AudioRNG(userAudioFile, bitsToExtract, lsbAmount);
                    AudioRNG adversaryAudioRNG = new AudioRNG(adversaryAudioFile, bitsToExtract, lsbAmount);

                    // Get the size of the user and adversary audio files
                    long userFileSize = userFile.length();
                    long adversaryFileSize = adversaryFile.length();


                    // Ensure that both files have the same size for comparison
                    if (userFileSize != adversaryFileSize) {
                        System.out.println("Error: The audio files must have the same length.");
                        System.exit(1);
                    }

                    // Hamming distances will be calculated for each pair generated during each iteration
                    List<Integer> hammingDistances = new ArrayList<>();

                    for(int i = 0; i < 10000; i++){
                        // Generate raw bytes from the entropy source file
                        byte[] userRawBytes = generateRawBytes(userAudioRNG, bitsToExtract, chunks);
                        byte[] adversaryRawBytes = generateRawBytes(adversaryAudioRNG, bitsToExtract, chunks);

                        int hammingDistance = calculateHammingDistance(userRawBytes, adversaryRawBytes);
                        hammingDistances.add(hammingDistance);
                    }
                    // Calculate min, max, and average distances over 10,000 iterations
                    int minDistance = hammingDistances.stream().min(Integer::compare).orElse(0);
                    int maxDistance = hammingDistances.stream().max(Integer::compare).orElse(0);
                    double averageDistance = hammingDistances.stream().mapToInt(Integer::intValue).average().orElse(0);

                    System.out.println("Min Hamming distance: " + minDistance);
                    System.out.println("Max Hamming distance: " + maxDistance);
                    System.out.println("Average Hamming distance: " + averageDistance);


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }


    // Method to generate raw bytes using the AudioRNG class based on chunks and bitsToExtract
    public static byte[] generateRawBytes(AudioRNG audioRNG, int bitsNeeded, int chunksNeeded) throws IOException {
        // Generate raw bytes that match the password generation
        return audioRNG.nextRawBytes(bitsNeeded*chunksNeeded);
    }

    // Method to calculate the Hamming distance between two byte arrays
    public static int calculateHammingDistance(byte[] userBytes, byte[] adversaryBytes) {
        int distance = 0;

        // Compare each byte from the two files
        for (int i = 0; i < userBytes.length; i++) {
            // Get the XOR result of the two bytes at position i
            byte xorResult = (byte) (userBytes[i] ^ adversaryBytes[i]);

            // Count the number of 1's in the XOR result (this gives us the number of differing bits)
            distance += countSetBits(xorResult);
        }
        return distance;
    }

    // Helper method to count the number of set bits (1's) in a byte
    public static int countSetBits(byte b) {
        int count = 0;
        int value = b & 0xFF;
        while (value  != 0) {
            count += value & 1; // Increment count if the least significant bit is 1
            value  >>>= 1;        // Right shift the bits (unsigned)
        }
        return count;
    }

}

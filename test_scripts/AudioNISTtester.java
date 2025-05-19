
import java.io.IOException;

public class AudioNISTtester {
    
    private static final String[] audioFiles = {
        "kth-quiet-room_USER_AUDIO_DATA.bin",      // Index 0: User Audio 1
        "KTH-quiet-room_ADVERSARY_AUDIO_DATA.bin", // Index 1: Adversary Audio 1
        "kth-corridor-constant-brus_USER_AUDIO_DATA.bin",      // Index 2: User Audio 2
        "KTH-corridor-constant-brus_ADVERSARY_AUDIO_DATA.bin", // Index 3: Adversary Audio 2
        "Gallerian_USER_AUDIO_DATA.bin",      // Index 4: User Audio 3
        "Gallerian_ADVERSARY_AUDIO_DATA.bin", // Index 5: Adversary Audio 3
        "parkbank_USER_AUDIO_DATA.bin",      // Index 6: User Audio 4
        "parkbank_ADVERSARY_AUDIO_DATA.bin",  // Index 7: Adversary Audio 4
        "loud_from_speakers_and_radio_AUDIO_DATA.bin", // Index 8:
        "radio_listenable_volume_AUDIO_DATA.bin",    // Index 9:
        "quiet_AUDIO_DATA.bin",  // Index 10:
        "kontor_AUDIO_DATA.bin", // Index 11:
        "vardagsrum_AUDIO_DATA.bin",    // Index 12:
        "bibliotek_AUDIO_DATA.bin"  // Index 13:  
    };

    public static void main(String[] args) {
       
        if (args.length != 2 || !args[0].equals("--file")) {
            System.err.println("Usage: java tester --file <AudioFileSetIndex>");
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

        // Ensure the index is within valid bounds
        if (fileSetIndex < 0 || fileSetIndex > audioFiles.length) {
            System.err.println("Error: Invalid index. Valid indices are [0," + (audioFiles.length - 1) + "]");
            System.exit(1);
        }

        // Parameters for the test
        int bitsPerSequence = 1000000;
        int sequenceCount = 100;


        String entropyFilePath = audioFiles[fileSetIndex];  // Make sure this file exists
        
        for (int bitsToExtract = 4; bitsToExtract <= 128; bitsToExtract *= 2) {
            for (int lsbAmount = 1; lsbAmount <= 8; lsbAmount++) {
                System.out.printf("===> Starting test: File = %s | LSB = %d | BitsToExtract = %d%n", entropyFilePath, lsbAmount, bitsToExtract);

                try {
                    // Initialize AudioRNG class
                    AudioRNG rng = new AudioRNG(entropyFilePath, bitsToExtract, lsbAmount);
        
                    // Run method that writes a fixed number of sequences
                    rng.writeNISTBinaryFile(bitsPerSequence, sequenceCount);

                    // Print success messages
                    System.out.println("writeNISTBinaryFile() completed.");

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}

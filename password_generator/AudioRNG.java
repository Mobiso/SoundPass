
import java.io.*;
import java.nio.ByteBuffer;                         // * extra method - used for salting  
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AudioRNG class generates random numbers using audio entropy data.
 * The class extracts entropy bits from an audio file using least significant bits (LSBs),
 * hashes them to improve randomness, and provides a variety of random number generation methods.
 * It also supports secure random number generation using salts and SHA-256 hashing.
 */
public class AudioRNG {

    // Instance variables for entropy source and random number generation
    private File entropySource;                 // File object representing the entropy source
    private FileInputStream entropyReader;      // Stream to read from entropy source
    private int lsbAmount;                      // Number of least significant bits to extract per sample (default 4)
    private int bitsToExtract;                  // Total number of entropy bits needed for extraction (bitsPerInt)
    private final int BYTES_PER_SAMPLE = 2;     // Constant (2 bytes per sample)
    private final int SAMPLE_SIZE_BITS = 16;    // Constant (16-bit audio samples)

    // Extra instance variable for added salt to enhance uniqueness in hashed passwords
    private final byte[] salt;                 // Salt for enhancing randomness in hashes            


    // * Constructors and Setup * 

    /**
     * Default constructor that initializes the entropy source and the number of bits to extract.
     * Uses 4 LSBs per sample for entropy extraction.
     *
     * @param entropySourcePath Path to the entropy source file (audio file)
     * @param bitsToExtract Number of bits to extract for random number generation
     * @throws IOException If the entropy source file cannot be read or found
    */
    public AudioRNG(String entropySourcePath, int bitsToExtract) throws IOException{
        this(entropySourcePath, bitsToExtract, 4);  // Default to 4 LSBs
    }

    /**
     * Constructor to initialize the entropy source with a specified number of LSBs to extract.
     *
     * @param entropySourcePath Path to the entropy source file (audio file)
     * @param bitsToExtract Number of bits to extract for random number generation
     * @param lsbAmount Number of least significant bits to extract per sample
     * @throws IOException If the entropy source file cannot be read or found
     */
    public AudioRNG(String entropySourcePath, int bitsToExtract, int lsbAmount) throws IOException{
        if (entropySourcePath == null) {
        throw new IllegalArgumentException("entropySourcePath cannot be null.");
        }
        if (bitsToExtract <= 0) {
            throw new IllegalArgumentException("bitsToExtract must be greater than 0.");
        }
        if (lsbAmount <= 0 || lsbAmount > SAMPLE_SIZE_BITS) {
            throw new IllegalArgumentException("LSB amount must be between 1 and 16.");
        }

        this.entropySource = new File(entropySourcePath);
        if (!entropySource.exists()  || !entropySource.isFile() || !entropySource.canRead()) {
            throw new FileNotFoundException("Invalid entropy source file: " + entropySourcePath);
        }
        this.entropyReader = new FileInputStream(entropySource);
        this.lsbAmount = lsbAmount;
        this.bitsToExtract = bitsToExtract;
        this.salt = generateSalt();
    }

    // * Configuration Methods *

    /**
     * Changes the entropy source for the RNG.
     *
     * @param newEntropySourcePath Path to the new entropy source file
     * @throws IOException If the new entropy source file cannot be read or found
     */
    public void setSeed(String newEntropySourcePath) throws IOException {
        File newFile = new File(newEntropySourcePath);
        if (!newFile.exists() || !newFile.isFile() || !entropySource.canRead()) {
            throw new FileNotFoundException("Invalid entropy source file: " + newEntropySourcePath);
        }
        this.entropySource = newFile;
        if (entropyReader != null) entropyReader.close();
        this.entropyReader = new FileInputStream(entropySource);
    }
    
    /**
     * Closes the input stream used for reading entropy.
     *
     * @throws IOException If an error occurs while closing the input stream
     */
    public void close() throws IOException {
        if (entropyReader != null) {
            entropyReader.close();
        }
    }

    // * Getter and Setter methods *

    /**
     * Gets the current amount of LSBs to extract.
     *
     * @return The current LSB amount
     */
    public int getLsbAmount() {
        return lsbAmount;
    }

    /**
     * Sets the number of LSBs to extract per sample.
     *
     * @param lsbAmount Number of LSBs to extract
     */
    public void setLsbAmount(int lsbAmount) {
        if (lsbAmount <= 0 || lsbAmount > SAMPLE_SIZE_BITS) {
            throw new IllegalArgumentException("LSB amount must be between 1 and 16.");
        }
        this.lsbAmount = lsbAmount;
    }

    /**
     * Gets the number of bits to extract.
     *
     * @return The number of bits to extract for randomness
     */
    public int getBitsToExtract() {
        return bitsToExtract;
    }

    /**
     * Sets the number of bits to extract.
     *
     * @param bitsToExtract The number of bits to extract
     */
    public void setBitsToExtract(int bitsToExtract) {
        if (bitsToExtract <= 0) {
            throw new IllegalArgumentException("bitsToExtract must be greater than 0.");
        }    
        this.bitsToExtract = bitsToExtract;
    }

    // * Entropy Extraction Methods *

    /**
     * Extracts entropy bits from the audio file.
     *
     * @param bitsNeeded The number of bits to extract
     * @return A byte array containing the extracted entropy bits
     */
    private byte[] extractEntropyBits(int bitsNeeded) {
        int samplesNeeded = (bitsNeeded + lsbAmount - 1) / lsbAmount;
        int bytesNeeded = samplesNeeded * BYTES_PER_SAMPLE;
        byte[] sampleBytes = readAudioBytesOnce(bytesNeeded);
        return extractLSBbits(sampleBytes, bitsNeeded);
    }

    /**
     * Reads the required number of bytes from the entropy source.
     *
     * @param bytesNeeded The number of bytes to read
     * @return A byte array containing the read data
     * @throws RuntimeException If reading from the entropy source fails
     */
    private byte[] readAudioBytesOnce(int bytesNeeded) {
        byte[] buffer = new byte[bytesNeeded];
        int bytesRead = 0;

        try {
            while (bytesRead < bytesNeeded) {
                int read = entropyReader.read(buffer, bytesRead, bytesNeeded - bytesRead);
                if (read == -1) {
                    throw new EOFException("Not enough entropy available — reached end of file.");
                }
                bytesRead += read;
            }
            return buffer;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read entropy source: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts LSB bits from the audio sample data.
     *
     * @param samples The audio sample data
     * @param bitsNeeded The number of bits needed
     * @return A byte array containing the extracted LSB bits
     */
    private byte[] extractLSBbits(byte[] samples, int bitsNeeded) {
        byte[] result = new byte[(bitsNeeded + 7) / 8];
        int bitIndex = 0;
        int mask = (1 << lsbAmount) - 1;
    
        for (int i = 0; i + 1 < samples.length && bitIndex < bitsNeeded; i += 2) {
            int sample = (samples[i] & 0xFF) | ((samples[i + 1] & 0xFF) << 8);
            int lsb = sample & mask;
    
            for (int b = lsbAmount - 1; b >= 0 && bitIndex < bitsNeeded; b--, bitIndex++) {
                int bit = (lsb >> b) & 1;
                int byteIndex = bitIndex / 8;
                int bitOffset = 7 - (bitIndex % 8);
                result[byteIndex] |= bit << bitOffset;
            }
        }
    
        return result;
    }

    // * Hashing Methods *

    /**
     * Hashes the input bits using SHA-256 to improve randomness.
     *
     * @param bits The bits to hash
     * @return The hashed output (32-byte array)
     * @throws RuntimeException If SHA-256 algorithm is not available
     */
    private byte[] hashBits(byte[] bits) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bits);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found.", e);
        }
    }

    /**
     * Converts a hash to an integer by XORing 4-byte chunks.
     *
     * @param hash The hash to convert
     * @return The integer value obtained from the hash
     */ 
    private int hashToInt(byte[] hash) {
        int result = 0;
        for (int i = 0; i + 3 < hash.length; i += 4) {
            int chunk = ((hash[i] & 0xFF) << 24)
                      | ((hash[i + 1] & 0xFF) << 16)
                      | ((hash[i + 2] & 0xFF) << 8)
                      | (hash[i + 3] & 0xFF);
            result ^= chunk;
        }
        return result;
    }

    // * Random Number Generation Methods *

    /**
     * Generates a random integer.
     *
     * @return A random integer
     */
    public int nextInt() {
        byte[] rawEntropy = extractEntropyBits(bitsToExtract);
        byte[] hashedEntropy = hashBits(rawEntropy);
        int randomValue = hashToInt(hashedEntropy);
        return randomValue;
    }

    /**
     * Generates a random integer within a specified upper limit.
     *
     * @param exclusiveUpperLimit The upper limit (exclusive)
     * @return A random integer in the range [0, exclusiveUpperLimit)
     */
    public int nextInt(int exclusiveUpperLimit) {
        if (exclusiveUpperLimit <= 0) {
            throw new IllegalArgumentException("Upper limit must be > 0.");
        }

        byte[] rawEntropy = extractEntropyBits(bitsToExtract);  
        byte[] hashedEntropy = hashBits(rawEntropy);
        int randomValue = hashToInt(hashedEntropy);
        
        // Get modulus [0, exclusiveUpperLimit)
        int mod = randomValue % exclusiveUpperLimit;
    
        // If mod is negative, adjust by adding the upper limit
        if (mod < 0) {
            mod += exclusiveUpperLimit;
        }
    
        return mod;
    }

    /**
     * Generates a random integer within a specified inclusive lower limit and exclusive upper limit.
     *
     * @param inclusiveLowerLimit The lower limit (inclusive)
     * @param exclusiveUpperLimit The upper limit (exclusive)
     * @return A random integer in the range [inclusiveLowerLimit, exclusiveUpperLimit)
     */
    public int nextInt(int inclusiveLowerLimit, int exclusiveUpperLimit) {
        if (inclusiveLowerLimit >= exclusiveUpperLimit) {
            throw new IllegalArgumentException("Lower limit must be less than upper limit.");
        }

        int range = exclusiveUpperLimit - inclusiveLowerLimit;
        return inclusiveLowerLimit + nextInt(range);
    }

    /**
     * Generates an array of random integers within a specified inclusive lower and exclusive upper bound.
     *
     * @param count Number of integers to generate
     * @param inclusiveLowerLimit Inclusive lower bound of the random numbers
     * @param exclusiveUpperLimit Exclusive upper bound of the random numbers
     * @return An array of random integers
     * @throws IllegalArgumentException if count <= 0
     */
    public int[] nextInts(int count, int inclusiveLowerLimit, int exclusiveUpperLimit) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be > 0.");
        }
    
        int[] results = new int[count];
        for (int i = 0; i < count; i++) {
            results[i] = nextInt(inclusiveLowerLimit, exclusiveUpperLimit);
        }
        return results;
    }

    /**
     * Generates an array of random integers within the range [0, exclusiveUpperLimit).
     *
     * @param count Number of integers to generate
     * @param exclusiveUpperLimit The exclusive upper bound for random numbers
     * @return An array of random integers
     * @throws IllegalArgumentException if count <= 0
     */
    public int[] nextInts(int count, int exclusiveUpperLimit) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be > 0.");
        }
    
        int[] results = new int[count];
        for (int i = 0; i < count; i++) {
            results[i] = nextInt(exclusiveUpperLimit);
        }
        return results;
    }

    /**
     * Fills a given byte array with random bytes.
     *
     * @param bytes Byte array to fill with random values
     * @return The filled byte array
     * @throws NullPointerException if the byte array is null
     */
    public byte[] nextBytes(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("The byte array provided is null");
        }

        int i = 0; // Index to fill the byte array
        while (i < bytes.length) {
            int randomValue = nextInt();
        
            // Determine how many bytes to write from the integer (up to 4 bytes)
            int n = Math.min(bytes.length - i, 4);
        
            // Extract bytes from the integer
            for (int j = 0; j < n; j++) {
                bytes[i++] = (byte) (randomValue & 0xFF);
                randomValue >>= 8; // Shift the integer to the right to get the next byte
            }
        }
    
        return bytes; // Return the filled byte array
    }

    /**
     * Generates a random byte array of specified bit length.
     *
     * @param numBits Number of bits to generate
     * @return A byte array containing random bits
     */
    public byte[] nextBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        bytes = nextBytes(bytes);
        return bytes;
    }


    /**
     * Fills a byte array with raw entropy-based bytes, ignoring hash compression.
     *
     * @param bytes The byte array to fill
     * @return The filled byte array
     * @throws NullPointerException if the byte array is null
     */
    public byte[] nextRawBytes(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("The byte array provided is null");
        }
    
        int i = 0; // Index to fill the byte array
        while (i < bytes.length) {
            // Extract entropy directly
            byte[] rawEntropy = extractEntropyBits(bitsToExtract);  // Extract entropy bits
    
            // Hash the raw entropy to mix the bits
            byte[] hashedEntropy = hashBits(rawEntropy);  // Apply hashing to enhance randomness
    
            // Determine how many bytes to write)
            int n = Math.min(bytes.length - i, hashedEntropy.length);

            // Use the hashed entropy to fill the byte array
            for (int j = 0; j < n; j++) {
                bytes[i++] = hashedEntropy[j];
            }
        }
    
        return bytes; // Return the filled byte array
    }

    /**
     * Generates raw entropy-based bytes of specified bit length.
     *
     * @param numBits Number of bits to generate
     * @return Byte array filled with raw entropy-based bytes
     */
    public byte[] nextRawBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        bytes = nextRawBytes(bytes);
        return bytes;
    }

    // * Secure Extra Methods *

    /**
     * Generates a secure salt based on current time and file metadata.
     *
     * @return A byte array representing the salt
     */
    private byte[] generateSalt() {
        long nanoTime = System.nanoTime();
        long currentTime = System.currentTimeMillis();
        int filePathHash = entropySource.getAbsolutePath().hashCode();
        long fileModified = entropySource.lastModified();

        ByteBuffer buffer = ByteBuffer.allocate(28); // 8+8+4+8 bytes
        buffer.putLong(nanoTime);
        buffer.putLong(currentTime);
        buffer.putInt(filePathHash);
        buffer.putLong(fileModified);

        return buffer.array();
    }

    /**
     * Hashes the raw entropy with persistent and timestamp-based salt.
     *
     * @param rawEntropy Byte array of raw entropy
     * @return SHA-256 hashed byte array
     */
    public byte[] hashWithFullSalt(byte[] rawEntropy) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(rawEntropy);
    
            // Add persistent salt
            digest.update(salt);
    
            // Add timestamp salt
            long time = System.nanoTime();
            digest.update(ByteBuffer.allocate(8).putLong(time).array());
    
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available.", e);
        }
    }

    /**
     * Converts a byte array into an int using XOR, bit rotation, and constant addition.
     *
     * @param hash The hash byte array
     * @return Mixed integer result
     */
    private int hashToIntMixed(byte[] hash) {
        int result = 0xA5A5A5A5;  // A fixed seed with alternating bit patterns - A = 1010 ; 5 = 0101
        for (int i = 0; i + 3 < hash.length; i += 4) {
            int chunk = ((hash[i] & 0xFF) << 24)
                      | ((hash[i + 1] & 0xFF) << 16)
                      | ((hash[i + 2] & 0xFF) << 8)
                      | (hash[i + 3] & 0xFF);
            result = Integer.rotateLeft(result ^ chunk, 5) + 0x7ED55D16;
        }
        return result;
    }

    /**
     * Generates a secure random integer using salted entropy and hashing.
     *
     * @return A secure random integer
     */
    public int secureNextInt() {
        byte[] rawEntropy = extractEntropyBits(bitsToExtract);
        byte[] hashedEntropy = hashWithFullSalt(rawEntropy);
        int randomValue = hashToIntMixed(hashedEntropy);
        return randomValue;
    }

    /**
     * Generates a secure random integer within range [0, exclusiveUpperLimit).
     *
     * @param exclusiveUpperLimit Upper bound (exclusive)
     * @return A secure random integer
     */
    public int secureNextInt(int exclusiveUpperLimit) {
        if (exclusiveUpperLimit <= 0) {
            throw new IllegalArgumentException("Upper limit must be > 0.");
        }

        byte[] rawEntropy = extractEntropyBits(bitsToExtract);  
        byte[] hashedEntropy = hashWithFullSalt(rawEntropy);
        int randomValue = hashToIntMixed(hashedEntropy);
        
        // Get modulus [0, exclusiveUpperLimit)
        int mod = randomValue % exclusiveUpperLimit;
    
        // If mod is negative, adjust by adding the upper limit
        if (mod < 0) {
            mod += exclusiveUpperLimit;
        }
    
        return mod;
    }

    /**
     * Generates a secure random integer within a range [inclusiveLowerLimit, exclusiveUpperLimit).
     *
     * @param inclusiveLowerLimit Lower bound (inclusive)
     * @param exclusiveUpperLimit Upper bound (exclusive)
     * @return A secure random integer within range
     */
    public int secureNextInt(int inclusiveLowerLimit, int exclusiveUpperLimit) {
        if (inclusiveLowerLimit >= exclusiveUpperLimit) {
            throw new IllegalArgumentException("Lower limit must be less than upper limit.");
        }

        int range = exclusiveUpperLimit - inclusiveLowerLimit;
        return inclusiveLowerLimit + secureNextInt(range);
    }

    /**
     * Fills a byte array with secure random bytes.
     *
     * @param bytes The byte array to fill
     * @return The filled byte array
     */
    public byte[] secureNextBytes(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("The byte array provided is null");
        }

        int i = 0; // Index to fill the byte array
        while (i < bytes.length) {
            int randomValue = secureNextInt();
        
            // Determine how many bytes to write from the integer (up to 4 bytes)
            int n = Math.min(bytes.length - i, 4);
        
            // Extract bytes from the integer
            for (int j = 0; j < n; j++) {
                bytes[i++] = (byte) (randomValue & 0xFF);
                randomValue >>= 8; // Shift the integer to the right to get the next byte
            }
        }
    
        return bytes; // Return the filled byte array
    }

    /**
     * Generates a byte array of secure random bits.
     *
     * @param numBits Number of bits to generate
     * @return Byte array of secure random data
     */
    public byte[] secureNextBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        bytes = secureNextBytes(bytes);
        return bytes;
    }

    /**
     * Fills a byte array with secure raw entropy-based bytes (no integer compression).
     *
     * @param bytes Byte array to fill
     * @return Filled byte array
     */
    public byte[] secureNextRawBytes(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("The byte array provided is null");
        }
        
        int i = 0; // Index to fill the byte array
        while (i < bytes.length) {
            // Extract entropy directly
            byte[] rawEntropy = extractEntropyBits(bitsToExtract);  // Extract entropy bits
        
            // Hash the raw entropy to mix the bits
            byte[] hashedEntropy = hashWithFullSalt(rawEntropy);  // Apply hashing to enhance randomness
        
            // Determine how many bytes to write)
            int n = Math.min(bytes.length - i, hashedEntropy.length);
    
            // Use the hashed entropy to fill the byte array
            for (int j = 0; j < n; j++) {
                bytes[i++] = hashedEntropy[j];
            }
        }
        
        return bytes; // Return the filled byte array
    }

    /**
     * Generates secure raw bytes for a given number of bits.
     *
     * @param numBits Number of bits to generate
     * @return Byte array filled with raw secure entropy
     */
    public byte[] secureNextRawBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        bytes = secureNextRawBytes(bytes);
        return bytes;
    }


    /**
     * Generates binary files for NIST testing using various RNG modes.
     *
     * @param bitsPerSequence Number of bits per generated sequence
     */
    public void writeNISTBinaryFiles(int bitsPerSequence) {

        String baseName = entropySource.getName().replaceAll("\\..+$", "");

         // Create the directory where the files will be saved
        String directoryPath = "NIST-files-" + baseName + "-" + bitsToExtract + "-bits";
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            directory.mkdirs();  // Create the directory if it does not exist
        }

        try {
            // Reseed the AudioRNG before writing each file
            this.setSeed(entropySource.getAbsolutePath()); // Reseed using the entropy source

            // Write the regular binary file
            writeBinaryFile(directoryPath + File.separator + baseName + "-NIST.bin", bitsPerSequence);

            // Reseed the AudioRNG before writing the secure file
            this.setSeed(entropySource.getAbsolutePath()); // Reseed again
            writeSecureBinaryFile(directoryPath + File.separator + baseName + "-NIST-secure.bin", bitsPerSequence);

            // Reseed the AudioRNG before writing the raw binary file
            this.setSeed(entropySource.getAbsolutePath()); // Reseed again
            writeRawBinaryFile(directoryPath + File.separator + baseName + "-NIST-raw.bin", bitsPerSequence);

            // Reseed the AudioRNG before writing the secure raw binary file
            this.setSeed(entropySource.getAbsolutePath()); // Reseed again
            writeSecureRawBinaryFile(directoryPath + File.separator + baseName + "-NIST-secure-raw.bin", bitsPerSequence);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write NIST binary files", e);
        }
    }

    // === Internal Helpers for File Writing ===

    private void writeBinaryFile(String filename, int numBits) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (true) {
                try {
                    byte[] bytes = nextBytes(numBits); // Secure version of nextBytes
                    out.write(bytes);
                } catch (EOFException eof) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }
    
    private void writeSecureBinaryFile(String filename, int numBits) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (true) {
                try {
                    byte[] bytes = secureNextBytes(numBits); // Secure version of nextBytes
                    out.write(bytes);
                } catch (EOFException eof) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

    private void writeRawBinaryFile(String filename, int numBits) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (true) {
                try {
                    byte[] bytes = nextRawBytes(numBits); // Secure version of nextBytes
                    out.write(bytes);
                } catch (EOFException eof) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

    private void writeSecureRawBinaryFile(String filename, int numBits) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (true) {
                try {
                    byte[] bytes = secureNextRawBytes(numBits); // Secure version of nextBytes
                    out.write(bytes);
                } catch (EOFException eof) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }
    
}

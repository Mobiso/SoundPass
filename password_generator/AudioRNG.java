
import java.io.*;
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
   
    public AudioRNG(String entropySourcePath, int bitsToExtract) throws IOException{
        this(entropySourcePath, bitsToExtract, 4);  // Default to 4 LSBs
    }

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
    }

    public void setSeed(String newEntropySourcePath) throws IOException {
        File newFile = new File(newEntropySourcePath);
        if (!newFile.exists() || !newFile.isFile() || !entropySource.canRead()) {
            throw new FileNotFoundException("Invalid entropy source file: " + newEntropySourcePath);
        }
        this.entropySource = newFile;
        if (entropyReader != null) entropyReader.close();
        this.entropyReader = new FileInputStream(entropySource);
    }
    
    public void close() throws IOException {
        if (entropyReader != null) {
            entropyReader.close();
        }
    }

    public int getLsbAmount() {
        return lsbAmount;
    }

    public void setLsbAmount(int lsbAmount) {
        if (lsbAmount <= 0 || lsbAmount > SAMPLE_SIZE_BITS) {
            throw new IllegalArgumentException("LSB amount must be between 1 and 16.");
        }
        this.lsbAmount = lsbAmount;
    }

    public int getBitsToExtract() {
        return bitsToExtract;
    }

    public void setBitsToExtract(int bitsToExtract) {
        if (bitsToExtract <= 0) {
            throw new IllegalArgumentException("bitsToExtract must be greater than 0.");
        }    
        this.bitsToExtract = bitsToExtract;
    }

    private byte[] extractEntropyBits(int bitsNeeded) {
        int samplesNeeded = (bitsNeeded + lsbAmount - 1) / lsbAmount;
        int bytesNeeded = samplesNeeded * BYTES_PER_SAMPLE;
        byte[] sampleBytes = readAudioBytesOnce(bytesNeeded);
        return extractLSBbits(sampleBytes, bitsNeeded);
    }

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

    private byte[] hashBits(byte[] bits) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bits);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found.", e);
        }
    }

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

    public int nextInt() {
        byte[] rawEntropy = extractEntropyBits(bitsToExtract);
        byte[] hashedEntropy = hashBits(rawEntropy);
        int randomValue = hashToInt(hashedEntropy);
        return randomValue;
    }

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

    public int nextInt(int inclusiveLowerLimit, int exclusiveUpperLimit) {
        if (inclusiveLowerLimit >= exclusiveUpperLimit) {
            throw new IllegalArgumentException("Lower limit must be less than upper limit.");
        }

        int range = exclusiveUpperLimit - inclusiveLowerLimit;
        return inclusiveLowerLimit + nextInt(range);
    }

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

    public byte[] nextBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        return nextBytes(bytes);
    }

    public byte[] nextHashedBytes(byte[] bytes) {
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

    public byte[] nextHashedBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        bytes = nextHashedBytes(bytes);
        return bytes;
    }

    public byte[] nextRawBytes(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("The byte array provided is null");
        }
    
        int i = 0; // Index to fill the byte array
        while (i < bytes.length) {
            // Extract entropy directly
            byte[] rawEntropy = extractEntropyBits(bitsToExtract);  // Extract entropy bits
    
            // Determine how many bytes to write)
            int n = Math.min(bytes.length - i, rawEntropy.length);

            // Use the hashed entropy to fill the byte array
            for (int j = 0; j < n; j++) {
                bytes[i++] = rawEntropy[j];
            }
        }
    
        return bytes; // Return the filled byte array
    }

    public byte[] nextRawBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        bytes = nextRawBytes(bytes);
        return bytes;
    }

    // * Secure Extra Methods *

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

    public int secureNextInt() {
        byte[] rawEntropy = extractEntropyBits(bitsToExtract);
        byte[] hashedEntropy = hashBits(rawEntropy);
        int randomValue = hashToIntMixed(hashedEntropy);
        return randomValue;
    }

    public int secureNextInt(int exclusiveUpperLimit) {
        if (exclusiveUpperLimit <= 0) {
            throw new IllegalArgumentException("Upper limit must be > 0.");
        }

        byte[] rawEntropy = extractEntropyBits(bitsToExtract);  
        byte[] hashedEntropy = hashBits(rawEntropy);
        int randomValue = hashToIntMixed(hashedEntropy);
        
        // Get modulus [0, exclusiveUpperLimit)
        int mod = randomValue % exclusiveUpperLimit;
    
        // If mod is negative, adjust by adding the upper limit
        if (mod < 0) {
            mod += exclusiveUpperLimit;
        }
    
        return mod;
    }

    public int secureNextInt(int inclusiveLowerLimit, int exclusiveUpperLimit) {
        if (inclusiveLowerLimit >= exclusiveUpperLimit) {
            throw new IllegalArgumentException("Lower limit must be less than upper limit.");
        }

        int range = exclusiveUpperLimit - inclusiveLowerLimit;
        return inclusiveLowerLimit + secureNextInt(range);
    }

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

    public byte[] secureNextBytes(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] bytes = new byte[numBytes];
        bytes = secureNextBytes(bytes);
        return bytes;
    }


    // * NIST Testing files - bitsPerSequence % 8 = 0 *

    public void writeNISTBinaryFile(int bitsPerSequence, int sequences) {
        String baseName = entropySource.getName().replaceAll("\\..+$", "");
        String filename = baseName + "-" + bitsToExtract + "/" + lsbAmount + "-bits/lsbs" + "-NIST.bin";

        File file = new File(filename);
        file.getParentFile().mkdirs(); // Ensure directory structure exists

        int count = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (count < sequences) {
                try {
                    byte[] bytes = nextBytes(bitsPerSequence); 
                    out.write(bytes);
                    count++;
                } catch (EOFException eof) {
                    System.err.println("EOF reached: number of sequences written = " + count);
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

    public void writeNISTBinaryFileUntilEOF(int bitsPerSequence) {
        String baseName = entropySource.getName().replaceAll("\\..+$", "");
        String filename = baseName + "-" + bitsToExtract + "/" + lsbAmount + "-bits/lsbs" + "-NIST-UNTIL-EOF.bin";

        File file = new File(filename);
        file.getParentFile().mkdirs(); // Ensure directory structure exists

        int count = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (true) {
                try {
                    byte[] bytes = nextBytes(bitsPerSequence); 
                    out.write(bytes);
                    count++;
                } catch (EOFException eof) {
                    System.err.println("EOF reached: number of sequences written = " + count);
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

    public void writeNISTHashedBinaryFile(int bitsPerSequence, int sequences) {
        String baseName = entropySource.getName().replaceAll("\\..+$", "");
        String filename = baseName + "-" + bitsToExtract + "/" + lsbAmount + "-bits/lsbs" + "-NIST-hashed.bin";

        File file = new File(filename);
        file.getParentFile().mkdirs();

        int count = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            while (count < sequences) {
                try {
                    byte[] bytes = nextHashedBytes(bitsPerSequence);
                    out.write(bytes);
                    count++;
                } catch (EOFException eof) {
                    System.err.println("EOF reached: number of hashed sequences written = " + count);
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

    public void writeNISTHashedBinaryFileUntilEOF(int bitsPerSequence) {
        String baseName = entropySource.getName().replaceAll("\\..+$", "");
        String filename = baseName + "-" + bitsToExtract + "/" + lsbAmount + "-bits/lsbs" + "-NIST-hashed-UNTIL-EOF.bin";

        File file = new File(filename);
        file.getParentFile().mkdirs(); // Ensure directory structure exists

        int count = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (true) {
                try {
                    byte[] bytes = nextHashedBytes(bitsPerSequence); 
                    out.write(bytes);
                    count++;
                } catch (EOFException eof) {
                    System.err.println("EOF reached: number of hashed sequences written = " + count);
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

        public void writeNISTSecureBinaryFile(int bitsPerSequence, int sequences) {
        String baseName = entropySource.getName().replaceAll("\\..+$", "");
        String filename = baseName + "-" + bitsToExtract + "/" + lsbAmount + "-bits/lsbs" + "-NIST-secure.bin";

        File file = new File(filename);
        file.getParentFile().mkdirs();

        int count = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            while (count < sequences) {
                try {
                    byte[] bytes = secureNextBytes(bitsPerSequence);
                    out.write(bytes);
                    count++;
                } catch (EOFException eof) {
                    System.err.println("EOF reached: number of hashed sequences written = " + count);
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

    public void writeNISTSecureBinaryFileUntilEOF(int bitsPerSequence) {
        String baseName = entropySource.getName().replaceAll("\\..+$", "");
        String filename = baseName + "-" + bitsToExtract + "/" + lsbAmount + "-bits/lsbs" + "-NIST-secure-UNTIL-EOF.bin";

        File file = new File(filename);
        file.getParentFile().mkdirs(); // Ensure directory structure exists

        int count = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
            while (true) {
                try {
                    byte[] bytes = secureNextBytes(bitsPerSequence); 
                    out.write(bytes);
                    count++;
                } catch (EOFException eof) {
                    System.err.println("EOF reached: number of hashed sequences written = " + count);
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }
    
}

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.security.MessageDigest;
import java.nio.ByteBuffer;

public class SoundRNG {
    
    File entropySource;
    FileInputStream entropy_source_reader;
    final int DEFAULT_LSB_AMOUNT = 4;
    final int BYTES_PER_SAMPLE = 2;
    int entropy_per_int;
    public SoundRNG(String entropySourceFile, int entropy_per_int){
        entropySource = new File(entropySourceFile);
        this.entropy_per_int = entropy_per_int;
        try {
            entropy_source_reader = new FileInputStream(entropySource.getAbsolutePath());
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("Error opening entropy source file.");
            e.printStackTrace();
            System.exit(-1);
        }

        if(entropy_per_int % 8 != 0){
            System.out.println("entropy per int needs to be divisable by 8");
            System.exit(-2);
        }   
    }
    private byte[] readBytes(int bits){
        int bytes_to_read = Math.ceilDiv(bits, DEFAULT_LSB_AMOUNT) * BYTES_PER_SAMPLE;
        byte[] raw_bytes_from_entropy_source;
        try {
            raw_bytes_from_entropy_source = entropy_source_reader.readNBytes(bytes_to_read);
            return raw_bytes_from_entropy_source;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            System.out.println("Error reading from entropy source file.");
            e.printStackTrace();
            System.exit(-3);
        }
        return null;
    }

    private byte[] get_lsbs(byte[] entropy_bytes){
        int mask = (1 << DEFAULT_LSB_AMOUNT) - 1;
        byte[] lsb_bytes = new byte[entropy_bytes.length];
        for (int i = 0; i < entropy_bytes.length; i++) {
            lsb_bytes[i] = (byte) (entropy_bytes[i] & mask);
        }
       return lsb_bytes;
    }

    private byte[] get_entropy_bytes(byte[] samples){
        byte[] entropy_bytes = new byte[samples.length/2];
        //For somereason the samples are little endian when using filereader? So i start from 0
        for (int i = 0, j = 0; i < samples.length; i+=2, j++) {
            entropy_bytes[j] = samples[i];
        }
        return entropy_bytes;
    }

    private byte[] create_bit_string(byte[] lsbs){
        BigInteger accumaltor = BigInteger.ZERO;
        for (byte b : lsbs) {
            accumaltor = accumaltor.shiftLeft(DEFAULT_LSB_AMOUNT).or(BigInteger.valueOf(b));
        }
        byte[] lsb_bits_only = accumaltor.toByteArray();
        //remove leading zero byte
         if (lsb_bits_only.length > 1 && lsb_bits_only[0] == 0) {
            lsb_bits_only = Arrays.copyOfRange(lsb_bits_only, 1, lsb_bits_only.length);
        }
        return lsb_bits_only;
    }

    private byte[] hashBits(byte[] bits){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bits);
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("Error hashing lsb:s");
            e.printStackTrace();
            System.exit(-4);
        }
        return null;
       
    }

    private int hash_to_int(byte[] hashed_lsb){
        int num = 0;
        for (int i = 0; i < hashed_lsb.length; i += 4) {
            int chunk = ((hashed_lsb[i] & 0xFF) << 24) |
                        ((hashed_lsb[i + 1] & 0xFF) << 16) |
                        ((hashed_lsb[i + 2] & 0xFF) << 8) |
                        (hashed_lsb[i + 3] & 0xFF);
            num ^= chunk;
        }
        return num;
    }


    public int nextInt(int exclusiveUpperLimit){
        
        byte[] lsb = create_bit_string(get_lsbs(get_entropy_bytes(readBytes(entropy_per_int))));
        byte[] hashed_lsb = hashBits(lsb);
        int non_trimmed_num = hash_to_int(hashed_lsb);
        return Math.abs(non_trimmed_num % exclusiveUpperLimit);
    }

    public int nextInt(int incusivelowerlimit, int exclusiveupperLimit){
        
        byte[] lsb = create_bit_string(get_lsbs(get_entropy_bytes(readBytes(entropy_per_int))));
        byte[] hashed_lsb = hashBits(lsb);
        int non_trimmed_num = hash_to_int(hashed_lsb);
        int range = exclusiveupperLimit - incusivelowerlimit;
        int num = Math.abs(non_trimmed_num % range) + incusivelowerlimit;
        
        return num;
    }

}

import java.util.EnumSet;
import java.util.Random; //For testing
import java.io.IOException;
import java.util.EnumMap;
public class SoundPassGen{
    
    //Uses the same symbols as the algorithm used by firefox. Similar looking symbols are removed
    private final String SYMBOLS_LOWER_CASE = "abcdefghijkmnpqrstuvwxyz";
    private final String SYMBOLS_UPPER_CASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final String SYMBOLS_DIGITS = "23456789"; 
    private final String SYMBOLS_SPECIAL = "-~!@#$%^&*_+=)}:;\"'>,.?]";
    private final int MAX_CONSECUTIVE = 3;
    private final int MAX_SHUFFLES = 5;
    private EnumMap<symbolType,String> enumSymbolMapping = new EnumMap<>(symbolType.class);

    public enum symbolType{UPPERCASE,LOWERCASE,DIGITS,SPECIAL}

    private boolean skip_shuffle = false;

    private AudioRNG rng;
    //Include RNG
    public SoundPassGen(String entropySource, int collected_entropy_per_letter) throws Exception{
        //Create RNG object
        rng = new AudioRNG(entropySource, collected_entropy_per_letter);
        
        enumSymbolMapping.put(symbolType.UPPERCASE, SYMBOLS_UPPER_CASE);
        enumSymbolMapping.put(symbolType.LOWERCASE, SYMBOLS_LOWER_CASE);
        enumSymbolMapping.put(symbolType.DIGITS, SYMBOLS_DIGITS);
        enumSymbolMapping.put(symbolType.SPECIAL, SYMBOLS_SPECIAL);
    }
    public SoundPassGen(String entropySource, int collected_entropy_per_letter, boolean skip_shuffle, int lsbs_per_sample) throws Exception{
        //Create RNG object
        rng = new AudioRNG(entropySource, collected_entropy_per_letter,lsbs_per_sample);
        enumSymbolMapping.put(symbolType.UPPERCASE, SYMBOLS_UPPER_CASE);
        enumSymbolMapping.put(symbolType.LOWERCASE, SYMBOLS_LOWER_CASE);
        enumSymbolMapping.put(symbolType.DIGITS, SYMBOLS_DIGITS);
        enumSymbolMapping.put(symbolType.SPECIAL, SYMBOLS_SPECIAL);
        this.skip_shuffle = skip_shuffle;
    }

    public String generatePassword(int length,EnumSet<symbolType> required){
        StringBuilder password = new StringBuilder();
        password.append(pick_random_required(required));
        String allowed_symbols = build_allowed_symbols_string(required);
        while (password.length() < length) {
            password.append(allowed_symbols.charAt(rng.nextInt(allowed_symbols.length())));
        }
        //Shuffle since required are always first
        password = fisher_yates(password);
        int current_shuffles = 0;
        while (!skip_shuffle && exceeds_max_consecutive(password) && current_shuffles <= MAX_SHUFFLES) {
            password = fisher_yates(password);
            current_shuffles++;
        }
        return password.toString();
    }

    private StringBuilder pick_random_required(EnumSet<symbolType> required){
        StringBuilder randomly_picked_required_symbols = new StringBuilder();
        for (symbolType t : required) {
            String symbols = enumSymbolMapping.get(t);
            randomly_picked_required_symbols.append(symbols.charAt(rng.nextInt(symbols.length())));
        }
        //Return Stringbuilder to keep it array-like
        return randomly_picked_required_symbols;
    }

    private String build_allowed_symbols_string(EnumSet<symbolType> allowed){
        StringBuilder allowed_symbols = new StringBuilder();
        for (symbolType t : allowed) {
            String symbols = enumSymbolMapping.get(t);
            allowed_symbols.append(symbols);
        }
        return allowed_symbols.toString();
    }

    private StringBuilder fisher_yates(StringBuilder string){
        for (int i = 0; i < string.length()-2; i++) {
            int j = rng.nextInt(i, string.length()-1);
            char swapped_char = string.charAt(i);
            string.setCharAt(i, string.charAt(j));
            string.setCharAt(j, swapped_char);
        }
        return string;
    }

    private boolean exceeds_max_consecutive(StringBuilder string){
        int consecutive = 1;
        int longest_consecutive = 1;
        char previous_char = ' ';
        for (int i = 0; i < string.length(); i++) {
            char current_char = string.charAt(i);
            if(previous_char == string.charAt(i))
                consecutive++;
            else
                consecutive = 1;

            if(consecutive > longest_consecutive)
                longest_consecutive = consecutive;

            previous_char = current_char; 
        }

        return longest_consecutive > MAX_CONSECUTIVE;
    }





}
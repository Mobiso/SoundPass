import java.util.EnumSet;

public class TestSoundPassGen {
    public static void main(String[] args) {
        SoundPassGen generator = new SoundPassGen();
        EnumSet<SoundPassGen.symbolType> requiredTypes = EnumSet.allOf(SoundPassGen.symbolType.class);
        
        for (int i = 1; i <= 10; i++) {
            String password = generator.generatePassword(12, requiredTypes);
            System.out.println("Password " + i + ": " + password);
        }
    }
}


import java.util.EnumSet;
import java.io.PrintWriter;
/*
 * 
 *  Barebone passwords to file implimentation in order to generate something to use when writing the python tests 
 * 
 */
public class GenerateAndPrintPasswordsToFile {
    public static void main(String[] args) {
        
        EnumSet<SoundPassGen.symbolType> requiredTypes = EnumSet.allOf(SoundPassGen.symbolType.class);
        
        
        int numPasswords = Integer.parseInt(args[0]);
        int passwordLength = Integer.parseInt(args[1]);
        int entropy_per_int = Integer.parseInt(args[2]);
        String entropySource =  args[3];
        String outputname = entropySource + "_passwords.txt";

        SoundPassGen generator = new SoundPassGen(entropySource,entropy_per_int,true);
        PrintWriter passwordWriter;
        try {
           passwordWriter = new PrintWriter(outputname);
           for (int i = 0; i < numPasswords; i++) {
                String current_password = generator.generatePassword(passwordLength, requiredTypes);
                System.out.println(current_password);
                passwordWriter.println(current_password);
            }
            passwordWriter.close();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
            System.out.println("Something went wong");
            System.exit(-1);
        }
       

    }
}

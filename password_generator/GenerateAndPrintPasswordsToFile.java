
import java.util.EnumSet;
import java.io.PrintWriter;
/*
 * 
 *  Barebone passwords to file implimentation in order to generate something to use when writing the python tests 
 * 
 */
public class GenerateAndPrintPasswordsToFile {
    public static void main(String[] args) {
        
        if(args.length != 5){
            System.out.println("Usage: <num passwords> <password length> <lsbs per sample> <entropy per int> ");
            System.exit(-1);
        }
        EnumSet<SoundPassGen.symbolType> requiredTypes = EnumSet.allOf(SoundPassGen.symbolType.class);
        

        int numPasswords = 0;
        int passwordLength = 0;
        int entropy_per_int = 0;
        int lsbs_per_sample = 0;
        String entropySource = "";
        String outputname = "";
        try {
            numPasswords = Integer.parseInt(args[0]);
            passwordLength = Integer.parseInt(args[1]);
            entropy_per_int = Integer.parseInt(args[2]);
            lsbs_per_sample = Integer.parseInt(args[3]);
            entropySource =  args[4];
            outputname = entropySource + "_passwords.txt";
    
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("Wrong Arguments. " + e.toString());
            System.exit(-2);
        }
       

        SoundPassGen generator;
        PrintWriter passwordWriter;
        try {
           generator = new SoundPassGen(entropySource,entropy_per_int,true,lsbs_per_sample);
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
            System.exit(-3);
        }
       

    }
}

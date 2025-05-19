import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatisticalTestParser {

    static class TestResult {
        int[] counts = new int[10];
        double pValue;
        String testName;
        int totalSequences;

        public double getChiSquared() {
            double expected = totalSequences / 10.0;
            double chiSq = 0.0;
            for (int count : counts) {
                chiSq += Math.pow(count - expected, 2) / expected;
            }
            return chiSq;
        }

        public boolean isChiSquarePass() {
            return getChiSquared() <= 33.725;
        }

        public boolean isPValuePass() {
            return pValue >= 0.01;
        }

        @Override
            public String toString() {
            return String.format(
            "%s P-VALUE: %.6f (%s) CHI-SQ: %.4f (%s)",
            testName,
            pValue,
            isPValuePass() ? "PASS" : "FAIL",
            getChiSquared(),
            isChiSquarePass() ? "PASS" : "FAIL"
            );
        }
    }

    public static void main(String[] args) {
        String filename = "finalAnalysisReport.txt"; // Replace with file path

        int nonOverlapTotal = 0, nonOverlapPValuePass = 0, nonOverlapChiPass = 0;
        int reTotal = 0, rePValuePass = 0, reChiPass = 0;
        int revTotal = 0, revPValuePass = 0, revChiPass = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;

            Pattern pattern = Pattern.compile(
                "\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+" +
                "(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+" +
                "([0-9.]+)(?:\\s*\\*)?\\s+(\\d+)/(\\d+)(?:\\s*\\*)?\\s*(\\S.*)"
            );

            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    TestResult result = new TestResult();
                    for (int i = 0; i < 10; i++) {
                        result.counts[i] = Integer.parseInt(matcher.group(i + 1));
                    }
                    result.pValue = Double.parseDouble(matcher.group(11));
                    result.totalSequences = Integer.parseInt(matcher.group(13));
                    result.testName = matcher.group(14).trim();

                    if (result.testName.startsWith("NonOverlappingTemplate")) {
                        nonOverlapTotal++;
                        if (result.isPValuePass()) nonOverlapPValuePass++;
                        if (result.isChiSquarePass()) nonOverlapChiPass++;
                    } else if (result.testName.startsWith("RandomExcursionsVariant")) {
                        revTotal++;
                        if (result.isPValuePass()) revPValuePass++;
                        if (result.isChiSquarePass()) revChiPass++;
                    } else if (result.testName.startsWith("RandomExcursions")) {
                        reTotal++;
                        if (result.isPValuePass()) rePValuePass++;
                        if (result.isChiSquarePass()) reChiPass++;
                    } else {
                        System.out.println(result);
                    }
                }
            }

            if (nonOverlapTotal > 0) {
                System.out.printf("NonOverlappingTemplate P-VALUE: %d/%d UNIFORMITY: %d/%d%n",
                    nonOverlapPValuePass, nonOverlapTotal,
                    nonOverlapChiPass, nonOverlapTotal
                );
            }

            if (reTotal > 0) {
                System.out.printf("RandomExcursions P-VALUE: %d/%d UNIFORMITY: %d/%d%n",
                    rePValuePass, reTotal,
                    reChiPass, reTotal
                );
            }

            if (revTotal > 0) {
                System.out.printf("RandomExcursionsVariant P-VALUE: %d/%d UNIFORMITY: %d/%d%n",
                    revPValuePass, revTotal,
                    revChiPass, revTotal
                );
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
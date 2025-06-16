# SoundPass


password_generator contains the java files for the RNG and password generator. It does not work with live audio but requires a bin files of raw audio data. 
  1. AudioRNG.java is the RNG.
  2. SoundPassGen.java is our implimentation of the Chromium password algorithm using the RNG.
  3. GenerateAndPrintPasswordsToFile.java is for generating passwords and printing them to file.

data includes the test results, generated passwords and the 9 minutes user/adversary recording bin files.

recording_scripts contains the python scripts used for recording.

test_scripts includes the scrips used for testing passwords and lsb similarity between recordings.

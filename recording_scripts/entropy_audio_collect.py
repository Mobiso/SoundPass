import pyaudio
import wave
import time
import os
import numpy as np
import sys
from tqdm import tqdm

SAMPLES_TO_DISCARD = 10_000
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 44100
INITIAL_ENTROPY_SAMPLES = 1_000_000
TOTAL_INITIAL_ENTROPY_SAMPLES = INITIAL_ENTROPY_SAMPLES + SAMPLES_TO_DISCARD
RESTART_SAMPLES = 1000
TOTAL_RESTART_SAMPLES = RESTART_SAMPLES + SAMPLES_TO_DISCARD
RESTARTS = 1000
BYTES_PER_SAMPLE = 2


#Create directory
location_name = input("Please enter recording location name: ")
try:
    os.mkdir(location_name)
    print(f"Directory '{location_name}' created successfully.")
except FileExistsError:
    print(f"Directory '{location_name}' already exists.")
except PermissionError:
    print(f"Permission denied: Unable to create '{location_name}'.")
except Exception as e:
    print(f"An error occurred: {e}")


#Create audio interface
p = pyaudio.PyAudio() 
print("--------------------------------------------------------------")
devices = p.get_device_count()
for device in range(devices):
    device_info = p.get_device_info_by_index(device)
    #Check if this device is a microphone (an input device)
    if device_info.get('maxInputChannels') > 0:
      print(f"Microphone: {device_info.get('name')} , Device Index: {device_info.get('index')}")

recording_device = int(input("Please select a recording device: "))
print("--------------------------------------------------------------")
lsb_amount = int(input("Please enter lsb amount to capture 1-8"))
if lsb_amount > 8 or lsb_amount <= 0:
    print("Wrong amount of lsbs. Enter 1-8")
    sys.exit()
time.sleep(4)
print("Starting to record")
#Open stream
stream = p.open(format=FORMAT,
                channels=CHANNELS,
                rate=RATE,
                frames_per_buffer=TOTAL_INITIAL_ENTROPY_SAMPLES,
                input=True,
                input_device_index=recording_device)
#Record
raw_audio_bytes = stream.read(TOTAL_INITIAL_ENTROPY_SAMPLES)
    
print("Finished recording")
print("Closing stream...")
stream.close()
audio_entropy_bytes = []
#Extract the 8 lsb from each sample
audio_entropy_bytes = raw_audio_bytes[SAMPLES_TO_DISCARD * BYTES_PER_SAMPLE::2]
#Extract lsb by masking
lsb_mask = (1 << lsb_amount) - 1
for i in range(lsb_amount):
    masked_entropy_bytes = bytes([byte & lsb_mask for byte in audio_entropy_bytes])
    #Print file
    file_path = f"{location_name}/{location_name}_{lsb_amount - i}_INITIAL.bin"
    with open(file_path,"wb") as file:
        file.write(masked_entropy_bytes)
    lsb_mask = lsb_mask >> 1
print("Finished creating lsb files")
print("--------------------------------------------------------------")

restart_answer = input("Run restart tests? (y/n)")
#Restart tests recordings
match restart_answer:
    case "y":
        #Create byte Matrix
        restart_matrix = np.zeros((RESTARTS,RESTART_SAMPLES),dtype=np.ubyte)
        
        time.sleep(4)
        for i in tqdm(range(RESTARTS),desc="Restarts: "):
            p = pyaudio.PyAudio() 
            stream = p.open(format=FORMAT,
                        channels=CHANNELS,
                        rate=RATE,
                        frames_per_buffer=TOTAL_RESTART_SAMPLES,
                        input=True,
                        input_device_index=recording_device)
            #Record
            raw_audio_bytes = stream.read(TOTAL_RESTART_SAMPLES)
            audio_entropy_bytes = raw_audio_bytes[SAMPLES_TO_DISCARD * BYTES_PER_SAMPLE::2]
            for j in range(RESTART_SAMPLES):
                restart_matrix[i][j] = audio_entropy_bytes[j]
            stream.close()
            p.terminate

        #Create Row dataset
        row_dataset = []
        for i in range(RESTARTS):
            for j in range(RESTART_SAMPLES):
                row_dataset.append(restart_matrix[i][j])

        #Print row dataset files
        #Extract lsb by masking
        lsb_mask = (1 << lsb_amount) - 1
        for i in range(lsb_amount):
            masked_rowdataset = bytes([byte & lsb_mask for byte in row_dataset])
            #Print file
            file_path = f"{location_name}/{location_name}_{lsb_amount - i}_RESTART.bin"
            with open(file_path,"wb") as file:
                file.write(masked_rowdataset)
            lsb_mask = lsb_mask >> 1
        print("Finished printing restart files")
    case _ :
        print("I assume you typed n")
print("Jobs done")


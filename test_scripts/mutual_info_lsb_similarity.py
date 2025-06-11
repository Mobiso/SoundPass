from sklearn.metrics import mutual_info_score
import numpy as np
import sys
import tqdm as tqdm
import os
import random
from tqdm.contrib.concurrent import process_map
import csv
ALPHA = 0.05
def get_ent_bytes(file_path):
    with open(file_path, 'rb') as f:
        return f.read()[::2]

def generate_permutation(n):
    return np.random.permutation(n)

def main(a_path, b_path,lsb_amount,sequence_length, sequence_amount,permutations,output_file_name):

    
    mask = (1 << lsb_amount) - 1
    a_arr  = np.frombuffer(get_ent_bytes(a_path), dtype=np.uint8)
    b_arr  = np.frombuffer(get_ent_bytes(b_path), dtype=np.uint8)
    a_lsbs = np.array(a_arr & mask)   # dtype stays uint8
    b_lsbs = np.array(b_arr & mask)     # dtype stays uint8
    if sequence_length * sequence_amount > len(a_lsbs) or sequence_length * sequence_amount > len(b_lsbs):
        print(f"not enoug data for sequence amount: {sequence_amount} of length {sequence_length}")
        sys.exit()
    
    initial_score = calc_mutual_info((a_lsbs,b_lsbs))
    scores_greater_than_initial = 0
    permutations_indices = [np.random.permutation(sequence_length) for _ in tqdm.tqdm(range(permutations),desc="Pre calculating permution indices")]
    sequences = []

    for i in range(sequence_amount):
        start = i * sequence_length
        end   = start + sequence_length
        sequences.append((a_lsbs[start:end], b_lsbs[start:end]))
    p_values_index_0 = []
    for sequence in tqdm.tqdm(sequences,desc="Testing sequences"):
        p_values_index_0.append(calc_one_sequence((sequence,permutations_indices)))


    threshold = ALPHA#/sequence_amount
    passed_sequences = sum(1 for p_value in p_values_index_0 if p_value > threshold)
    with open(f"{output_file_name}.csv", "w", newline="") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(["seq", "p_value", "appears_random"])
        # you want the loop here:
        for i, p in enumerate(p_values_index_0):
            appears_random = int(p > threshold)
            writer.writerow([i, p, appears_random])

    print(f"sequences passed: {passed_sequences}")
    

def calc_one_sequence(args):
    (a,b),permutations_indices = args
    initial_mi = calc_mutual_info((a,b))
    scores_greater_than_initial = 0
    for i in tqdm.tqdm(range(len(permutations_indices)), desc="Testing permutations", leave=False):
        mi = calc_mutual_info((a,b[permutations_indices[i]]))
        if mi >= initial_mi:
            scores_greater_than_initial += 1
    
   
    #for mi in results:
    #    if mi >= initial_mi:
    #        scores_greater_than_initial += 1
            
    p_value = scores_greater_than_initial/(len(permutations_indices) + 1)
    return p_value
def calc_mutual_info(args):
    a,b = args
    return mutual_info_score(a,b)


if __name__ == "__main__":
    
    if len(sys.argv) != 8:
        print("Usage: python script.py <inputA.bin> <inputB.bin> <lsb amount> <sequence length> <sequence amount> <permutations> <output file name>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),int(sys.argv[5]),int(sys.argv[6]), sys.argv[7])
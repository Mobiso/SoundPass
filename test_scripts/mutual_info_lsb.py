from sklearn.metrics import mutual_info_score
import numpy as np
import sys
import tqdm as tqdm
ALPHA = 0.05
def read_chunks(file_path, byte_amount):
    chunks = []
    with open(file_path, 'rb') as f:
        return f.read(byte_amount)

def main(a_path, b_path,byte_amount,lsb_amount,permutations):

    mask = (1 << lsb_amount) - 1
    a_arr  = np.frombuffer(read_chunks(a_path,byte_amount), dtype=np.uint8)
    b_arr  = np.frombuffer(read_chunks(b_path,byte_amount), dtype=np.uint8)
    a_lsbs = a_arr & mask     # dtype stays uint8
    b_lsbs = b_arr & mask     # dtype stays uint8
    initial_score = calc_mutual_info(a_lsbs,b_lsbs)
    scores_greater_than_initial = 0
    for perm in tqdm.tqdm(range(permutations), desc="Testing permutations",unit ="perm",leave=False):
        score = calc_mutual_info(a_lsbs,np.random.permutation(b_lsbs))
        if score >= initial_score:
            scores_greater_than_initial += 1
    p_value = scores_greater_than_initial/(permutations + 1)
    print(f"Initial score = {initial_score}")
    print(f"p-value: {p_value}")
    print(f"A p-value greater than {ALPHA} means there is no difference from random {permutations} permutations")

def calc_mutual_info(a,b):
    return mutual_info_score(a,b)

if __name__ == "__main__":
    
    if len(sys.argv) != 6:
        print("Usage: python script.py <inputA.bin> <inputB.bin> <byte_amount> <lsb amount> <permutations>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),int(sys.argv[5]))
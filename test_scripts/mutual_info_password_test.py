from sklearn.metrics import mutual_info_score
import numpy as np
import sys
import os
import tqdm

from tqdm.contrib.concurrent import process_map
# All sets of symbols
ALPHA = 0.05



def read_passwords(filepath):
    """Read and strip passwords (one per line) from a file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return [line.strip() for line in f if line.strip()]


def global_mi(passwordsA,passwordsB, permutations_amount):
    passwordsA_symbol_list = list("".join(passwordsA))
    passwordsB_symbol_list = np.array(list("".join(passwordsB)))
    initial_mi = mutual_info_score(passwordsA_symbol_list,passwordsB_symbol_list)
    
    #Pre calculate perutations indexes
    permutations = [np.random.permutation(len(passwordsB_symbol_list)) for _ in tqdm.tqdm(range(permutations_amount), desc="Pre calculating permutations", unit ="perm",leave=False)]
    jobs = []
    for i in range(permutations_amount):
        jobs.append((passwordsA_symbol_list,passwordsB_symbol_list[permutations[i]]))

    results = process_map(
        process_one_perm_mi,
        jobs,
        chunksize=1,
        max_workers=os.cpu_count(),
        desc="Permutations tested",
        unit="perms",
        leave=False
    )

    number_of_mi_greater_than_initial = 0
    for mi in results:
        if mi >= initial_mi:
            number_of_mi_greater_than_initial += 1
    p_value_mi_perm = number_of_mi_greater_than_initial/(permutations_amount +1)
    
    print("Global results:")
    print(f"\tInitial MI: {initial_mi}")
    print(f"\tP-value permutation test: {p_value_mi_perm}\t", "PASS" if p_value_mi_perm > ALPHA else "FAIL")

def process_one_perm_mi(args):
    passwordsA_symbol_list,passwordsB_symbol_list = args
    return mutual_info_score(passwordsA_symbol_list,passwordsB_symbol_list)


def run_cross_position_dependencies(passwordsA,passwordsB,permutations_amount):
    perm_indices = [np.random.permutation(len(passwordsB)) for _ in tqdm.tqdm(range(permutations_amount), desc="Pre calculating permutations", unit ="perm",leave=False)]
    pair_progress_bar = tqdm.tqdm(total = (len(passwordsA[0])*len(passwordsA[0])),desc="Pairs completed",unit="pairs",leave=False)
    results = []
    for i in range(len(passwordsA[0])):
        for j in range(len(passwordsA[0])):
            colA = [pw[i] for pw in passwordsA]
            colB = np.array([pw[j] for pw in passwordsB])
            results.append(process_one_pair((i, j, colA, colB, perm_indices)))
            pair_progress_bar.update(1)
    
    print("\nCross Dependency Results: :")
    for i, j, p_val, mi in results:
        print(f"({i}, {j}) -> p_val: {p_val:.4f}, MI: {mi}", " |PASS" if p_val > ALPHA/(len(passwordsA[0])**2) else " |FAIL")
    

def process_one_pair(args):
    i, j, colA, colB, perm_indices = args
    initial_mi = mutual_info_score(colA,colB)
    number_of_mi_greater_than_initial = 0
    jobs = []

    for perm in perm_indices:
        jobs.append((colA,colB[perm]))
        
    results = process_map(
        process_one_perm_mi,
        jobs,
        chunksize=1,
        max_workers=os.cpu_count(),
        desc=f"Permutations tested for pos ({i},{j})",
        unit="perms",
        leave = False
    )
    for mi in results:
        if mi >= initial_mi:
            number_of_mi_greater_than_initial += 1
    
    p_val_mi = number_of_mi_greater_than_initial / (len(perm_indices) + 1)
    return (i, j, p_val_mi,initial_mi)


def main(a_path, b_path,permutations_amount):


    #Read passwords
    pwA = read_passwords(a_path)
    pwB = read_passwords(b_path)
    global_mi(pwA,pwB,permutations_amount)
    run_cross_position_dependencies(pwA,pwB,permutations_amount)
   


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python script.py <passwordsA.txt> <passwordsB.txt> <permutation amount>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2],int(sys.argv[3]))
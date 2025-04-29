from scipy.stats import chi2_contingency
import sys
import numpy as np
from numpy import random
import os
import tqdm
from scipy.stats.contingency import association
from concurrent.futures import ProcessPoolExecutor
from tqdm.contrib.concurrent import process_map
# All sets of symbols
SYMBOLS_LOWER = "abcdefghijkmnpqrstuvwxyz"
SYMBOLS_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
SYMBOLS_DIGITS = "23456789"
SYMBOLS_SPECIAL = "-~!@#$%^&*_+=)}:;\"'>,.?]"
ALPHA = 0.05
SYMBOLS_IN_USE = ""
CHAR_MAPPING = {}


def read_passwords(filepath):
    """Read and strip passwords (one per line) from a file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return [line.strip() for line in f if line.strip()]

def set_active_symbols(password, 
                          lower=SYMBOLS_LOWER, 
                          upper=SYMBOLS_UPPER, 
                          digits=SYMBOLS_DIGITS, 
                          special=SYMBOLS_SPECIAL):
    
    use = []
    if set(password) & set(lower):
        use.append(lower)
    if set(password) & set(upper):
        use.append(upper)
    if set(password) & set(digits):
        use.append(digits)
    if set(password) & set(special):
        use.append(special)
    
    global CHAR_MAPPING
    global SYMBOLS_IN_USE

    SYMBOLS_IN_USE = "".join(use)
    CHAR_MAPPING = {ch:index for index, ch in enumerate(SYMBOLS_IN_USE)}
    

def build_contingency(symbols_a, symbols_b):
    symbols_a = ''.join(symbols_a)
    symbols_b = ''.join(symbols_b)
    matrix = np.zeros((len(SYMBOLS_IN_USE),len(SYMBOLS_IN_USE)),dtype=int)
    for char_index in range(len(symbols_a)):
        matrix[CHAR_MAPPING.get(symbols_a[char_index])][CHAR_MAPPING.get(symbols_b[char_index])] += 1
    return matrix


#
# Transforms all passwords into a single long String
# Tests if User letter A -> Adversary letter B
# CHATGPT used to help format result

def run_chi_test(passwordsA, passwordsB, permutations_amount, cross_permutations=None):
   
    if cross_permutations is None:
        cross_permutations = permutations_amount

    # 1) Global analytic + permutation
    crv0, chi0, p0, dof0 = run_global_chi(passwordsA, passwordsB)
    p_chi_perm, p_crv_perm = run_permutation_global_chi(
        passwordsA, passwordsB, permutations_amount, chi0, crv0
    )

    print("=== Global χ² Test ===")
    print(f"  Initial p-value:\t{p0}")
    print(f"  Initial Cramer's V:\t{crv0}")
    print(f"  Degrees of freedom:\t{dof0}")
    print("  Result:", "FAIL" if p0 <= ALPHA else "PASS")
    print("---------------")
    print("=== Global Permutation Test ===")
    print(f"  χ² statistic permutation p-value:\t{p_chi_perm}")
    print(f"  Cramer's V permutation p-value:\t{p_crv_perm}")
    print("  χ² Perm test:\t","FAILED" if p_chi_perm   <= ALPHA else "PASSED")
    print("  Cramer's V:\t","FAILED" if p_crv_perm   <= ALPHA else "PASSED")
    print("---------------\n")

    # 2) Cross-position dependencies
    print("=== Cross-position Dependencies===")
    cross_results = run_cross_position_dependencies_chi(
        passwordsA, passwordsB, cross_permutations
    )
   # right after you compute cross_results...

    # 1) build a header and separator
    header = f"{'#':>3} | {'PosA':>4} | {'PosB':>4} | {'Chi2 p-val':>10} | {'crv p-val':>9} | {'Init p':>10} | {'CramersV init':>14} | {'STATUS':>8}"
    sep = "-" * len(header)
    print(header)
    print(sep)

    for rank, (i, j, p_chi, p_crv, p_init, crv_init) in enumerate(cross_results):
        # Bonferroni correction
        status = "PASS" if p_chi > ALPHA / (len(passwordsA[0])**2) else "FAIL"
        print(f"{rank:3d} | {i:4d} | {j:4d} | {p_chi:10.4f} | {p_crv:9.4f} | {p_init:10.4f} | {crv_init:14.4f} | {status:>8s}")
        print()  # blank line after the table

def run_global_chi(passwordsA, passwordsB):
    contingency = build_contingency(passwordsA,passwordsB)
    cramersV = association(contingency, method="cramer")
    chi2, p, dof, expected = chi2_contingency(contingency)
    return cramersV, chi2, p, dof


#
#   Tests if User letter A -> Adversary letter B.
#   Turns passwordsB into a single long strings, permutates it and perform a Chi square test against A.
#   Collects permutations_amounts of Chi2 statistics and compare the initial non permutated statistic to the distrobution.
#
def run_permutation_global_chi(passwordsA,passwordsB,permutations_amount, chi_initial, cramersV_initial):
    chi_statistics = []
    cramersV_statistics = []
    passwordsAString = ''.join(passwordsA)
    passwordsBString = ''.join(passwordsB)
    

    passwordsB_symbolArray = np.array(list(passwordsBString))
    
    number_of_chi_greater_than_initial = 0
    number_of_cramersV_greater_than_initial = 0
    
    #Pre calculate perutations indexes
    permutations = [np.random.permutation(len(passwordsB_symbolArray)) for _ in tqdm.tqdm(range(permutations_amount), desc="Pre calculating permutations", unit ="perm",leave=False)]
    jobs = []
    for i in range(permutations_amount):
        jobs.append((passwordsA,passwordsB_symbolArray[permutations[i]]))

    results = process_map(
        process_one_global_permutation,
        jobs,
        chunksize=1,
        max_workers=os.cpu_count(),
        desc="Permutations tested",
        unit="perms",
        leave=False
    )
    for cramersV, chi2 in results:
        if chi2 >= chi_initial:
            number_of_chi_greater_than_initial += 1
        if cramersV >= cramersV_initial:
            number_of_cramersV_greater_than_initial += 1
    #Calculate p-value
    p_value_chi = number_of_chi_greater_than_initial/(permutations_amount +1)
    p_value_cramersV = number_of_cramersV_greater_than_initial/(permutations_amount + 1)
    return p_value_chi, p_value_cramersV

def process_one_global_permutation(args):
    passwordsA,passwordsBPermutaded = args
    contingency = build_contingency(passwordsA,passwordsBPermutaded)
    chi2, p, dof, expected = chi2_contingency(contingency)
    cramersV = association(contingency,method="cramer")
    return cramersV,chi2

#
#   Tests if User letter A -> Adversary letter B for every position pair (i,j).
#   Collects permutations_amounts of Chi2 statistics for every position pair and compare the initial non permutated statistic to the distrobution.
#
def run_cross_position_dependencies_chi(passwordsA, passwordsB, permutations_amount):
  
    passwordLength = len(passwordsA[0])
    results = []
   
    perm_indices = [ np.random.permutation(len(passwordsA)) for _ in tqdm.tqdm(range(permutations_amount), desc="Pre calculating permutations indices", unit ="perm",leave=False)]
    # one progress bar for all pairs
    pair_progress_bar = tqdm.tqdm(total = (len(passwordsA[0])*len(passwordsA[0])),desc="Pairs completed",unit="pairs",leave=False)
    for i in range(len(passwordsA[0])):
        for j in range(len(passwordsA[0])):
            colA = [pw[i] for pw in passwordsA]
            colB = np.array([pw[j] for pw in passwordsB])
            results.append(process_one_pair((i, j, colA, colB, perm_indices)))
            pair_progress_bar.update(1) 

    # sort descending by p_val_chi
    return sorted(results, key=lambda x: x[2], reverse=True)
#Change so it uses multicores for 1 pair by giving jobs one perm indice each
def process_one_pair(args):
    i, j, colA, colB, perm_indices = args
    contingency = build_contingency(colA, colB)
    chi_initial, p_initial, _, _ = chi2_contingency(contingency)
    cramersV_initial = association(contingency, method="cramer")
      
    number_of_chi_greater_than_initial = 0
    number_of_cramersV_greater_than_initial = 0
    jobs = []

    for perm in perm_indices:
        jobs.append((colA,colB[perm]))
        
    results = process_map(
        process_one_permutation,
        jobs,
        chunksize=1,
        max_workers=os.cpu_count(),
        desc=f"Permutations tested for pos ({i},{j})",
        unit="perms",
        leave = False
    )
    for chi,cramersV in results:
        if chi >= chi_initial:
            number_of_chi_greater_than_initial += 1
        if cramersV >= cramersV_initial:
            number_of_cramersV_greater_than_initial += 1
   
    
    p_val_chi = number_of_chi_greater_than_initial / (len(perm_indices) + 1)
    p_val_cramer = number_of_cramersV_greater_than_initial/ (len(perm_indices) + 1)
    return (i, j, p_val_chi, p_val_cramer, p_initial, cramersV_initial)
def process_one_permutation(args):
    colA,permutedColB = args
    contingency = build_contingency(colA,permutedColB)
    chi,_, _, _ = chi2_contingency(contingency)
    cramersV = association(contingency,method="cramer")
    return chi,cramersV

            
def main(a_path, b_path,permutations_amount):
    #<
    #
    # Chi square tests H0 = There is no association between the letter A in passwordsA and letter B in passwordsB
    # If p <= significance level then reject H0 -> Ha is true (there is an association)
    #
    # Permutations tests H0 = Associations are the same as if it was random
    # If p <= significance level then reject H0 -> Ha is true (Associations exists and are stronger than random)
    #
    # Cross position relies on permutations due to some cell counts having less than 5. 
    #
    #


    #Read passwords
    pwA = read_passwords(a_path)
    pwB = read_passwords(b_path)

    #Detect which symbols in use based on first password
    set_active_symbols(pwA[0])
    print(f"Using symbols: {SYMBOLS_IN_USE}")
    print()
    run_chi_test(pwA,pwB,permutations_amount)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python script.py <passwordsA.txt> <passwordsB.txt> <permutation amount>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2],int(sys.argv[3]))
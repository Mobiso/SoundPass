from scipy.stats import chi2_contingency
import sys
import numpy as np
from numpy import random
import tqdm
from scipy.stats.contingency import association
import concurrent.futures
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
   # right after you compute `cross_results`...

    # 1) build a header and separator
    header = f"{'#':>3} | {'PosA':>4} | {'PosB':>4} | {'χ² p-val':>8} | {'crv p-val':>8} | {'Init p':>10} | {'CramersV init':>8}"
    sep    = "-" * len(header)
    print(header)
    print(sep)

    # 2) print the top 10 in fixed-width columns
    for rank, (i, j, p_chi, p_crv, p_init, crv_init) in enumerate(cross_results):
        print(f"{rank:3d} | {i:4d} | {j:4d} | {p_chi:8.4f} | {p_crv:8.4f} | {p_init:10.4f} | {crv_init:8.4f}")

    print()  # blank line after the table

def run_global_chi(passwordsA, passwordsB):
    contingency = build_contingency(
        ''.join(passwordsA),
        ''.join(passwordsB),
    )
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
    #Needs to be a char list to create permutation
    passwordsBList = list(passwordsBString)
    
    number_of_chi_greater_than_initial = 0
    number_of_cramersV_greater_than_initial = 0
    for i in tqdm.tqdm(range(permutations_amount), desc="Permutations Progress", unit="perm"):
        contingency = build_contingency(passwordsAString,random.permutation(passwordsBList))
        chi2, p, dof, expected = chi2_contingency(contingency)
        cramersV = association(contingency,method="cramer")
        if chi2 >= chi_initial:
            number_of_chi_greater_than_initial += 1
        if cramersV >= cramersV_initial:
            number_of_cramersV_greater_than_initial += 1
        
    #Calculate p-value
    p_value_chi = number_of_chi_greater_than_initial/(permutations_amount +1)
    p_value_cramersV = number_of_cramersV_greater_than_initial/(permutations_amount + 1)
    return p_value_chi, p_value_cramersV

#
#   Tests if User letter A -> Adversary letter B for every position pair (i,j).
#   Collects permutations_amounts of Chi2 statistics for every position pair and compare the initial non permutated statistic to the distrobution.
#
def run_cross_position_dependencies_chi(passwordsA, passwordsB, permutations):
  
    passwordLength = len(passwordsA[0])
    results = []
    # one progress bar for all pairs
    outer = tqdm.tqdm(total=passwordLength**2, desc="Position pairs", unit="pair")
    
    for i in range(passwordLength):
        colA = [pw[i] for pw in passwordsA]
        for j in range(passwordLength):
            colB_list = [pw[j] for pw in passwordsB]
            # initial (non-permuted) stats
            contingency = build_contingency(colA, colB_list)
            chi_initial, p_initial, _, _ = chi2_contingency(contingency)
            cramersV_initial = association(contingency, method="cramer")
            
            
            number_of_chi_greater_than_initial = 0
            number_of_cramersV_greater_than_initial = 0
            
            # permute and count
            inner = tqdm.tqdm(total=permutations, desc=f"Perm Pos ({i},{j})", unit="perm", leave=False)
            for _ in range(permutations):
                permuted_B = np.random.permutation(colB_list)
                cont = build_contingency(colA, permuted_B)
                chi, _, _, _ = chi2_contingency(cont)
                crv = association(cont)
                
                if chi >= chi_initial:
                    number_of_chi_greater_than_initial += 1
                if crv >= cramersV_initial:
                   number_of_cramersV_greater_than_initial += 1
                
                inner.update(1)
            inner.close()
            
            # compute p-values exactly as in the global test
            p_val_chi = number_of_chi_greater_than_initial / (permutations + 1)
            p_val_cramer = number_of_cramersV_greater_than_initial/ (permutations + 1)
            
            results.append((i, j, p_val_chi, p_val_cramer, p_initial, cramersV_initial))
            outer.update(1)
    
    outer.close()
    # sort descending by p_val_chi
    return sorted(results, key=lambda x: x[2], reverse=True)

  



def main(a_path, b_path,permutations_amount):
    #
    # INCLUDE CRAMERS V
    # LOOK UP P VALUE FOR PERMUTATION TESTING
    # CHOOSE A SIGNIFICANCE LEVEL lets go with 0.05
    # Look up bonferri correction

    #
    # Chi square tests H0 = There is no association between the letter A in passwordsA results in letter B in passwordsB
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
    run_chi_test(pwA,pwB,permutations_amount)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python script.py <passwordsA.txt> <passwordsB.txt> <permutation amount>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2],int(sys.argv[3]))

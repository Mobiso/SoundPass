
import sys
import csv

def read_file_lines(filepath):
    """Read and strip passwords (one per line) from a file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return [line.strip() for line in f if line.strip()]

def hamming(a,b):
    distance = 0
    for index in range(len(a)):
        if a[index] != b[index]:
            distance += 1
    return distance

def main(a_path, b_path,password_length):
    #Read passwords
    A = read_file_lines(a_path)
    B = read_file_lines(b_path)
    
    hamming_distro = {}
    for i in range(password_length):
        hamming_distro[i] = 0

    for password_index in range(len(A)):
        d = hamming(A[password_index], B[password_index])
        hamming_distro[d] = hamming_distro.get(d,0) + 1

    with open('hamming_distribution_passwords.csv', mode='w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(['Hamming Distance', 'Count'])  # header row
        for distance, count in sorted(hamming_distro.items()):
            writer.writerow([distance, count])

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python script.py <passwordsA.txt> <passwordsB.txt> <password length>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2],int(sys.argv[3]))
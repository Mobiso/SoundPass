
import sys
import csv

def read_chunks(file_path, chunk_size):
    chunks = []
    with open(file_path, 'rb') as f:
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                break
            chunks.append(chunk)
    return chunks

def hamming_byte(a,b):
    size_of_byte = 8
    distance = 0
    for bit_index in range(size_of_byte):
        if (a >> bit_index) & 1 != (b >> bit_index) & 1:
            distance += 1
    return distance

def hamming_bytes(a,b):
    if len(a) != len(b):
        print("Byte amount per chunk is not equal!")
        sys.exit
    hamming_dist = 0
    for byte_index in range(len(a)):
        hamming_dist += hamming_byte(a[byte_index],b[byte_index])
    return hamming_dist


def main(a_path, b_path,chunk_size):
    #Read passwords

    A_chunks = read_chunks(a_path,chunk_size)
    B_chunks = read_chunks(b_path,chunk_size)

    hamming_distro = {}
    size_of_byte = 8
    for i in range(chunk_size*size_of_byte):
        hamming_distro[i] = 0

    for chunk in range(len(A_chunks)):
        d = hamming_bytes(A_chunks[chunk], B_chunks[chunk])
        hamming_distro[d] = hamming_distro.get(d,0) + 1

    with open('hamming_distribution_input_rng.csv', mode='w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(['Hamming Distance', 'Count'])  # header row
        for distance, count in sorted(hamming_distro.items()):
            writer.writerow([distance, count])

if __name__ == "__main__":
    
    if len(sys.argv) != 4:
        print("Usage: python script.py <inputA.bin> <inputB.bin> <chunk size(bytes)>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2],int(sys.argv[3]))
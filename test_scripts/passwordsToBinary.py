import sys
def read_passwords(filepath):
    """Read and strip passwords (one per line) from a file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return [line.strip() for line in f if line.strip()]



def main(passwordsPath):
    #Read passwords
    passwords = read_passwords(passwordsPath)
    binary_passwords = "".join([''.join(format(ord(i),'08b') for i in password) + "\n" for password in passwords])
    out_path = f"{passwordsPath}_BINARY.txt"
    with open(out_path, "w") as f:
        f.write(binary_passwords)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python script.py <passwords.txt>")
        sys.exit(1)
    main(sys.argv[1])
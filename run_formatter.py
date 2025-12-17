import subprocess
import sys
import os


def run_command(command):
    try:
        subprocess.check_call(command)
    except subprocess.CalledProcessError as e:
        print(f"❌ Error running command: {' '.join(command)}")
        print(f"Details: {e}")
        sys.exit(1)


def main():
    print("--- 🧹 AUTO-FORMATTER ---")

    # 1. Install black if missing
    print("1️⃣  Installing/Updating 'black'...")
    run_command([sys.executable, "-m", "pip", "install", "black"])

    # 2. Run black on the current directory
    print("\n2️⃣  Formatting code with 'black'...")
    # "." means current directory (recursive)
    run_command([sys.executable, "-m", "black", "."])

    print("\n✅ Code formatting complete!")
    print("👉 NOW: You must COMMIT and PUSH these changes to fix the build.")
    print("Run:")
    print("   git add .")
    print("   git commit -m 'Style: Auto-format code with black'")
    print("   git push")


if __name__ == "__main__":
    main()
